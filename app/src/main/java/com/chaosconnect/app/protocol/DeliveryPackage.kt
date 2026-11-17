package com.chaosconnect.app.protocol

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

data class DeliveryEntry(val name:String,val offset:Long,val size:Int,val crc:Long)
data class DeliveryPackage(val source:File,val kind:Int,val name:String,val entries:List<DeliveryEntry>,val total:Long,val crc:Long) {
    fun metadata():ByteArray=WireWriter().u8(kind).u8(name.toByteArray().size).bytes(name.toByteArray()).u16(entries.size).u32(total).build()
    fun descriptor(entry:DeliveryEntry)=WireWriter().u16(entry.name.toByteArray().size).bytes(entry.name.toByteArray()).u32(entry.size.toLong()).u32(entry.crc).build()
}
/** Strict reader of the 10 Pro delivery container. Lua is metadata only; never executed.
 * Payload ranges stay in a private immutable file rather than retaining the full bin in RAM. */
object DeliveryPackageReader {
    const val MAX_CONTAINER=48L*1024*1024
    private fun utf8(b:ByteArray)=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
    private fun validName(s:String,kind:Int):Boolean {
        val b=s.toByteArray();return b.size in 1..12 && s.last()!=' ' && s.none{it.isISOControl()||it in '\u202a'..'\u202e'||it in '\u2066'..'\u2069'} && (kind==1||s.all{it.code in 33..126})
    }
    fun parse(file:File):DeliveryPackage=RandomAccessFile(file,"r").use { f->
        val length=f.length();checkWire(length in 0x190L..MAX_CONTAINER,"投递包大小无效")
        fun bytes(at:Long,n:Int):ByteArray {checkWire(at>=0&&n>=0&&at+n<=length,"投递包记录越界");return ByteArray(n).also{f.seek(at);f.readFully(it)}}
        fun u32(at:Long):Long {val b=bytes(at,4);return (b[0].toLong() and 255) or ((b[1].toLong() and 255) shl 8) or ((b[2].toLong() and 255) shl 16) or ((b[3].toLong() and 255) shl 24)}
        fun expect(at:Long,value:Long){checkWire(u32(at)==value,"投递包结构不匹配（${at.toString(16)}）")}
        expect(0,0x1234a55a);expect(4,0x10000);expect(0x10,0x800);expect(0x14,0x10000);expect(0x1c,1)
        val pkg=bytes(0x28,12).toString(Charsets.US_ASCII);checkWire(pkg.matches(Regex("[0-9]{12}"))&&pkg!="979820260926","不是独立资源投递包")
        val count=u32(0xd8);checkWire(count==4L,"只支持制作台输出的字体和图标投递包")
        val end=0x148+16*(count+2);expect(0x20,end);expect(0xa8,0x80000000);expect(0xac,end);expect(0xb0,1);expect(0xb4,0x148)
        repeat(9){i->val at=0xb8L+i*8;expect(at,if(at==0xd8L)count else 0);expect(at+4,if(i<5)0x158 else end-16)}
        expect(0x148,0);expect(0x14c,0);expect(0x150,end-16);expect(0x154,16)
        repeat(4){i->expect(end-16+i*4,if(i==0)0x05000000 else 0)}
        expect(end,0x410);var pos=end+12+u32(end+8);checkWire(pos<=length,"预览数据越界")
        data class Slot(val path:String,val at:Long,val size:Int)
        val slots=mutableListOf<Slot>()
        repeat(4){i->val at=0x158L+i*16;expect(at,0x05000000L+i);expect(at+4,0)
            val off=u32(at+8);val size=u32(at+12);checkWire(off==pos&&size>=21&&off+size<=length,"投递包文件区不连续或越界")
            val h=u32(off);val pathSize=(h ushr 24).toInt();val dataSize=(h and 0xffffff).toInt()
            checkWire(pathSize>0&&size==20L+pathSize+dataSize,"文件长度与记录不一致")
            checkWire(bytes(off+4,16).all{it==0.toByte()},"文件保留字段无效")
            val pathBytes=bytes(off+20,pathSize);checkWire(pathBytes.all{it.toInt() in 32..126},"投递包路径无效")
            slots+=Slot(pathBytes.toString(Charsets.US_ASCII),off+20+pathSize,dataSize);pos+=size
        }
        checkWire(pos==length,"投递包尾部有多余内容")
        val kind=when(slots[0].path){"_lua/fontpack/main.lua"->1;"_lua/iconpack/main.lua"->2;else->throw ProtocolException("不是字体或图标投递包")}
        val root=if(kind==1)"_lua/fontpack/" else "_lua/iconpack/"
        checkWire(slots.map{it.path}==listOf(root+"main.lua",root+"chaos_sup.ko",root+"chaos_icon.bin",root+if(kind==1)"font.ttf" else "pack.bin"),"投递包槽位不匹配")
        checkWire(slots[0].size in 1..262144,"投递包元数据过大")
        val lua=utf8(bytes(slots[0].at,slots[0].size));val key=if(kind==1)"FONT_NAME" else "PACK_NAME"
        val matches=Regex("(?m)^\\s*local\\s+$key\\s*=\\s*\"((?:[^\"\\\\\\r\\n]|\\\\.)*)\"[ \\t]*(?:--[^\\r\\n]*)?$").findAll(lua).toList()
        checkWire(matches.size==1,"投递包名称缺失或不明确")
        val literal=matches.single().groupValues[1];val name=buildString {var i=0;while(i<literal.length){val c=literal[i++];if(c=='\\'){checkWire(i<literal.length&&literal[i] in listOf('\\','"'),"投递包名称转义无效");append(literal[i++])}else append(c)}}
        checkWire(validName(name,kind),"投递包名称无效或未替换")
        val payload=slots[3];val entries=mutableListOf<DeliveryEntry>();var total=0L
        fun entry(name:String,at:Long,n:Int){checkWire(n>0&&at+n<=payload.at+payload.size,"资源文件长度无效");val crc=CRC32();var done=0;while(done<n){val b=bytes(at+done,minOf(65536,n-done));crc.update(b);done+=b.size};entries+=DeliveryEntry(name,at,n,crc.value);total+=n}
        if(kind==1){
            checkWire(payload.size>=12,"字体文件头缺失")
            val h=bytes(payload.at,12);val magic=h.copyOfRange(0,4);checkWire(magic.contentEquals(byteArrayOf(0,1,0,0))||magic.toString(Charsets.US_ASCII) in listOf("OTTO","true"),"字体不是SFNT文件")
            val tables=((h[4].toInt() and 255) shl 8) or (h[5].toInt() and 255);checkWire(tables in 1..256&&12+16*tables<=payload.size,"字体表数量无效")
            repeat(tables){i->val row=bytes(payload.at+12+16*i,16);val b=ByteBuffer.wrap(row);val offset=b.getInt(8).toLong() and 0xffffffffL;val len=b.getInt(12).toLong() and 0xffffffffL;checkWire(offset+len<=payload.size,"字体表越界")}
            entry("font.ttf",payload.at,payload.size)
        }else{
            checkWire(payload.size>=8&&bytes(payload.at,4).toString(Charsets.US_ASCII)=="CIPK","图标资源不是CIPK")
            val icons=u32(payload.at+4);checkWire(icons in 1..256,"图标数量无效");var at=payload.at+8;val seen=mutableSetOf<String>()
            repeat(icons.toInt()){
                checkWire(at+5<=payload.at+payload.size,"图标记录截断");val n=u32(at);val nl=bytes(at+4,1)[0].toInt() and 255;at+=5
                checkWire(n in 1..0x100000&&nl in 1..64&&at+nl+n<=payload.at+payload.size,"图标长度无效")
                val nm=bytes(at,nl).toString(Charsets.US_ASCII);at+=nl
                checkWire(nm.matches(Regex("[A-Za-z0-9_]+\\.bin"))&&seen.add(nm),"图标名无效或重复")
                checkWire(nm.endsWith("_anim.bin")||n<=0x80000,"单张图标过大");checkWire(nm!="animations.bin"||n==20L,"动画清单长度无效")
                entry(nm,at,n.toInt());at+=n
            };checkWire(at==payload.at+payload.size,"图标容器尾部无效")
        }
        checkWire(total in 1..32L*1024*1024,"资源总大小超限")
        val pkgData=DeliveryPackage(file,kind,name,entries,total,0);val crc=CRC32();crc.update(pkgData.metadata())
        for(e in entries){crc.update(pkgData.descriptor(e));var done=0;while(done<e.size){val b=bytes(e.offset+done,minOf(65536,e.size-done));crc.update(b);done+=b.size}}
        pkgData.copy(crc=crc.value)
    }
}
