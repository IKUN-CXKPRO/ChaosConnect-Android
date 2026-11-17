package com.chaosconnect.app.protocol
import java.io.File
import java.io.RandomAccessFile
import org.junit.Test
import org.junit.Assert.*
class DeliveryPackageTest {
    private fun sample(name:String)=File.createTempFile("delivery-",".bin").also{it.deleteOnExit();it.writeBytes(javaClass.getResourceAsStream("/delivery/web-$name.bin")!!.readBytes())}
    @Test fun productionWebFontAndIconFixturesExposeFinalNamesAndPayloadOnly(){
        val font=DeliveryPackageReader.parse(sample("font"));assertEquals("文楷",font.name);assertEquals(1,font.kind);assertEquals(listOf("font.ttf"),font.entries.map{it.name});assertEquals(44,font.total.toInt())
        val icons=DeliveryPackageReader.parse(sample("icons"));assertEquals("MyIcons",icons.name);assertEquals(listOf("app.bin"),icons.entries.map{it.name});assertEquals(64,icons.total.toInt());assertTrue(icons.crc>0)
    }
    @Test fun corruptedOffsetsTruncationAndTrailingBytesAreRejected(){
        val original=sample("icons").readBytes()
        for(bytes in listOf(original.copyOf(original.size-1),original+byteArrayOf(1),original.copyOf().also{it[0x20]=0},original.copyOf().also{it[0]=0})){
            val f=File.createTempFile("bad-delivery",".bin");try{f.writeBytes(bytes);try{DeliveryPackageReader.parse(f);fail("Accepted damaged package")}catch(_:ProtocolException){}}finally{f.delete()}
        }
    }
    @Test fun metadataLuaIsNeverExecutedAndUnknownPathsAreRejected(){
        val f=sample("font");val bytes=f.readBytes();val target="_lua/fontpack/font.ttf".toByteArray();val at=bytes.indices.first{i->i+target.size<=bytes.size&&bytes.copyOfRange(i,i+target.size).contentEquals(target)}
        bytes[at]='/'.code.toByte();f.writeBytes(bytes)
        try{DeliveryPackageReader.parse(f);fail()}catch(_:ProtocolException){}
    }
}
