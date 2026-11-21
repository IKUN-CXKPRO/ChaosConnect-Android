package com.chaosconnect.app.protocol

import com.chaos.bandpack.data.pack.*
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

class StudioDeliveryTest {
    private fun resource(name:String)=javaClass.getResourceAsStream(name)!!.use{it.readBytes()}
    private fun parse(bytes:ByteArray,check:(DeliveryPackage)->Unit){val f=File.createTempFile("studio-delivery-",".bin");try{f.writeBytes(bytes);check(DeliveryPackageReader.parse(f))}finally{f.delete()}}
    @Test fun newTwoResourceAnimatedIconsAreInstallableWithoutExecutingLua(){
        val preview=resource("/studio/reference-preview.block")
        val bytes=IconPackBuilder.build("local PACK_NAME = \"__PACK_NAME__\"\nlocal PACK_TITLE = \"__PACK_TITLE__\"\n",IconPackBuilder.Inputs("Demo","图标投递Demo","Demo",null,listOf(Cipk.Icon("activities",resource("/studio/cover96.bin")),Cipk.Icon("activities_anim",resource("/studio/valid.dgf")))),preview).bytes
        for(pack in listOf(bytes,resource("/studio/animated-pack.bin")))parse(pack){delivery->
            assertEquals(2,delivery.kind);assertEquals("Demo",delivery.name);assertTrue(delivery.entries.any{it.name=="activities_anim.bin"});assertTrue(delivery.entries.any{it.name=="animations.bin"})
            assertFalse(delivery.entries.any{it.name.endsWith("lua")});assertTrue(delivery.crc>0)
        }
    }
    @Test fun studioFontBuilderRetainsFinalShortNameAndFontPayload(){
        val raw=resource("/delivery/web-font.bin");var font=byteArrayOf()
        parse(raw){delivery->RandomAccessFile(delivery.source,"r").use{f->val entry=delivery.entries.single();f.seek(entry.offset);font=ByteArray(entry.size).also{f.readFully(it)}}}
        val assets=PackAssets(ByteArray(64),ByteArray(32),"local FONT_NAME = \"__FONT_NAME__\"\nlocal PACK_LABEL = \"__PACK_LABEL__\"\nlocal PACK_TITLE = \"__PACK_TITLE__\"\n","")
        val bytes=FontPackBuilder.build(assets,FontPackBuilder.Inputs("文楷","文楷投递","文楷",null,font),resource("/studio/reference-preview.block")).bytes
        parse(bytes){delivery->assertEquals("文楷",delivery.name);assertEquals(1,delivery.kind);assertEquals(font.size,delivery.total.toInt());assertEquals(listOf("font.ttf"),delivery.entries.map{it.name})}
    }
}
