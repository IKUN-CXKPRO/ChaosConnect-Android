package com.chaosconnect.app.protocol

import java.io.RandomAccessFile
import kotlinx.coroutines.*

data class InstallProgress(val state:Int,val done:Long,val total:Long)
class ChaosPackageInstaller(private val session:ChaosSession) {
    private fun status(bytes:ByteArray):Pair<Long,InstallProgress>{val r=WireReader(bytes);val token=r.u32();val state=r.u8();val done=r.u32();val total=r.u32();r.end();checkWire(state in 0..7&&done<=total,"安装状态无效");return token to InstallProgress(state,done,total)}
    suspend fun install(pack:DeliveryPackage,onProgress:(InstallProgress)->Unit) {
        checkWire(session.state.value.negotiation?.capabilities?.and(32)==32,"请先更新支持投递包安装的Chaos模块")
        var token=0L;var committed=false
        try {
            // Use session-owned writes: stopping never truncates a native socket record.
            var result=withContext(NonCancellable){status(session.submit(0x60,pack.metadata()+WireWriter().u32(pack.crc).build()).await().payload)}
            token=result.first;currentCoroutineContext().ensureActive();checkWire(token!=0L,"手环没有返回安装凭据");onProgress(result.second)
            while(result.second.state==1){delay(300);result=status(session.submit(0x61,WireWriter().u32(token).build()).await().payload);checkWire(result.first==token,"安装请求已过期");onProgress(result.second)}
            checkWire(result.second.state==2,when(result.second.state){3->"手环拒绝安装投递包";4->"手环安装确认超时";else->"安装请求已失效"})
            val n=session.state.value.negotiation!!;var notified=0L
            RandomAccessFile(pack.source,"r").use {f->var base=0L;for(e in pack.entries){
                session.submit(0x62,WireWriter().u32(token).bytes(pack.descriptor(e)).build()).await()
                f.seek(e.offset);var offset=0;val queue=ArrayDeque<Pair<Long,Deferred<CcnCodec.Frame>>>()
                suspend fun consume(){val(expected,pending)=queue.removeFirst();val(t,progress)=status(pending.await().payload);checkWire(t==token&&progress.done==expected,"安装写入回执不匹配");val now=System.nanoTime();if(now-notified>200_000_000L){notified=now;onProgress(progress)}}
                while(offset<e.size){currentCoroutineContext().ensureActive();val b=ByteArray(minOf(n.chunk,e.size-offset));withContext(Dispatchers.IO){f.readFully(b)}
                    val request=session.submit(0x63,WireWriter().u32(token).u32(offset.toLong()).bytes(b).build());offset+=b.size
                    queue.addLast((base+offset) to request);if(queue.size>=n.window)consume()
                };while(queue.isNotEmpty())consume()
                session.submit(0x64,WireWriter().u32(token).build()).await();base+=e.size
            }}
            val(t,progress)=status(session.submit(0x65,WireWriter().u32(token).build()).await().payload)
            checkWire(t==token&&progress.state==6&&progress.done==pack.total,"安装提交未完成，请核对手环清单")
            committed=true;onProgress(progress)
        }finally{
            if(token!=0L&&!committed)withContext(NonCancellable){try{session.submit(0x66,WireWriter().u32(token).build(),timeout=3_000).await()}catch(_:Exception){}}
        }
    }
}
