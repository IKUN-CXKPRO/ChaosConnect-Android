package com.chaosconnect.app.protocol

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RemoteEntry(val name:String,val directory:Boolean,val size:Long,val modifiedAt:Long)
data class DirectoryPage(val total:Int,val offset:Int,val entries:List<RemoteEntry>) {
    val hasMore get()=offset+entries.size<total
}
data class RemoteStat(val directory:Boolean,val size:Long,val modifiedAt:Long)
data class FileProgress(val completed:Long,val total:Long)

/** Native file protocol only. One file transaction at a time, up to eight blocks in flight.
 * Streams remain owned by the caller. No shared scratch file or app-wide file permission. */
class ChaosFiles(private val session:ChaosSession) {
    private val operations=Mutex()
    private fun negotiation()=session.state.value.negotiation ?: throw ProtocolException("请先完成 HELLO")
    private fun path(value:String):ByteArray=WireWriter().path(value).build().also {
        require(it.size-2<=negotiation().maximumPath){"路径超过设备协商上限"}
    }
    suspend fun list(path:String,offset:Int=0,limit:Int=16):DirectoryPage=operations.withLock {
        require(offset in 0..65535 && limit in 1..16)
        val request=WireWriter().bytes(path(path)).u32(offset.toLong()).u8(limit).build()
        val r=WireReader(session.request(Op.LIST,request).payload)
        val total=r.u16();val actual=r.u16();val count=r.u16()
        checkWire(actual==offset && count<=limit && (count>0 || offset>=total),"目录分页参数无效")
        val entries=List(count) {
            val name=r.text(r.u8());val directory=r.u8();val size=r.u32();val mtime=r.u32()
            checkWire(name.isNotEmpty() && name!="." && name!=".." && name.none {it=='/' || it.code<32} && directory in 0..1,"目录条目无效")
            RemoteEntry(name,directory==1,size,mtime)
        }
        r.end();DirectoryPage(total,actual,entries)
    }
    suspend fun stat(path:String):RemoteStat=operations.withLock {
        val r=WireReader(session.request(Op.STAT,path(path)).payload);val kind=r.u8();val size=r.u32();val mtime=r.u32();r.end()
        checkWire(kind in 0..1,"文件类型无效");RemoteStat(kind==1,size,mtime)
    }
    private suspend fun empty(opcode:Int,payload:ByteArray) {WireReader(session.request(opcode,payload).payload).end()}
    suspend fun mkdir(path:String)=operations.withLock {empty(Op.MKDIR,path(path))}
    suspend fun remove(path:String,directory:Boolean=false)=operations.withLock {empty(if(directory)Op.RMDIR else Op.REMOVE,path(path))}
    suspend fun rename(from:String,to:String)=operations.withLock {empty(Op.RENAME,path(from)+path(to))}
    suspend fun copy(from:String,to:String,overwrite:Boolean=false)=operations.withLock {empty(Op.COPY,path(from)+path(to)+byteArrayOf(if(overwrite)1 else 0))}

    private data class WriteAck(val request:Deferred<CcnCodec.Frame>,val offset:Long,val crc:Long)
    suspend fun upload(path:String,input:InputStream,size:Long,overwrite:Boolean=false,
        onProgress:(FileProgress)->Unit={}):Unit=operations.withLock {
        require(size in 0..32L*1024*1024){"文件上限为 32 MiB"}
        val n=negotiation();val queue=ArrayDeque<WriteAck>();val crc=CRC32()
        var opened=false
        try {
            empty(Op.OPEN_WRITE,WireWriter().bytes(path(path)).u32(size).u8(if(overwrite)1 else 0).build());opened=true
            var offset=0L
            suspend fun acknowledge() {
                val ack=queue.removeFirst();val r=WireReader(ack.request.await().payload)
                val written=r.u32();val checksum=r.u32();r.end()
                checkWire(written==ack.offset && checksum==ack.crc,"WRITE 回执偏移或 CRC32 不匹配")
                onProgress(FileProgress(written,size))
            }
            while(offset<size) {
                currentCoroutineContext().ensureActive()
                val length=minOf(n.chunk.toLong(),size-offset).toInt()
                val bytes=withContext(Dispatchers.IO) {
                    val buffer=ByteArray(length);var at=0
                    while(at<length) {
                        val read=input.read(buffer,at,length-at)
                        if(read<0)throw ProtocolException("本地文件在声明大小之前结束")
                        if(read==0) {val single=input.read();if(single<0)throw ProtocolException("本地文件提前结束");buffer[at++]=single.toByte()} else at+=read
                    };buffer
                }
                crc.update(bytes)
                val request=session.submit(Op.WRITE,WireWriter().u32(offset).bytes(bytes).build())
                offset+=length;queue.addLast(WriteAck(request,offset,crc.value))
                if(queue.size>=n.window)acknowledge()
            }
            while(queue.isNotEmpty())acknowledge()
            // Every WRITE has been acknowledged and its rolling CRC checked before COMMIT.
            empty(Op.COMMIT,WireWriter().u32(crc.value).build());opened=false
            onProgress(FileProgress(size,size))
        } finally {
            queue.forEach {it.request.cancel()}
            if(opened) withContext(NonCancellable) {
                try {session.request(Op.ABORT,timeout=2_000)}catch(_:Exception){ /* Remote lease also cleans up after loss. */ }
            }
        }
    }
    private data class ReadBlock(val request:Deferred<CcnCodec.Frame>,val offset:Long,val length:Int)
    suspend fun download(path:String,output:OutputStream,onProgress:(FileProgress)->Unit={}):Long=operations.withLock {
        val n=negotiation();val queue=ArrayDeque<ReadBlock>();var opened=false
        try {
            val r=WireReader(session.request(Op.OPEN_READ,path(path)).payload);val size=r.u32();r.end();opened=true
            var scheduled=0L;var completed=0L
            suspend fun consume() {
                val block=queue.removeFirst();val bytes=block.request.await().payload
                checkWire(block.offset==completed && bytes.size==block.length,"READ 数据截断或顺序错误")
                withContext(Dispatchers.IO) {output.write(bytes)}
                completed+=bytes.size;onProgress(FileProgress(completed,size))
            }
            while(scheduled<size) {
                currentCoroutineContext().ensureActive()
                val length=minOf(n.chunk.toLong(),size-scheduled).toInt()
                val request=session.submit(Op.READ,WireWriter().u32(scheduled).u16(length).build())
                queue.addLast(ReadBlock(request,scheduled,length));scheduled+=length
                if(queue.size>=n.window)consume()
            }
            while(queue.isNotEmpty())consume()
            empty(Op.CLOSE_READ,byteArrayOf());opened=false
            onProgress(FileProgress(size,size));size
        } finally {
            queue.forEach {it.request.cancel()}
            if(opened)withContext(NonCancellable) {try {session.request(Op.CLOSE_READ,timeout=2_000)}catch(_:Exception){}}
        }
    }
    suspend fun echo(bytes:ByteArray):ByteArray=operations.withLock {
        require(bytes.size<=negotiation().chunk)
        session.request(Op.ECHO,bytes).payload.also {checkWire(it.contentEquals(bytes),"链路回显校验失败")}
    }
}
