@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.chaosconnect.app.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ChaosSettingsTest {
    private class Link:ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        var mask=16L;var enabled="0";var token=0L;var pending=byteArrayOf();var waiting=false;var fail=0
        val operations=mutableListOf<Int>()
        private fun text(value:String):ByteArray {val b=value.toByteArray();return WireWriter().u16(b.size).bytes(b).build()}
        override suspend fun send(packet:ByteArray){
            val p=WearEnvelope.unwrap(packet)!!;val op=p.frame.opcode;operations+=op
            val bytes=when(op){
                Op.HELLO->WireWriter().u8(1).u16(2048).u8(79).u8(0).u16(255).u32(8).build()
                Op.PING->byteArrayOf(1,0,2,0,0,0,0,0)
                Op.GET_ACCESS->WireWriter().u8(1).u8(1).u8(0).u8(2).u32(mask).u32(0).u8(1).u8(0).u8(0).u8(0).build()
                Op.SETTINGS_LIST->{
                    val r=WireReader(p.frame.payload);val offset=r.u16();r.end();assertEquals(0,offset)
                    pending=WireWriter().u8(1).u16(1).u16(1).u8(1).u16(4).u8(0).u32(0).u32(1).bytes(text("显示与交互")).bytes(text("屏幕翻转")).bytes(text(enabled)).bytes(text("")).build()
                    waiting=true;WireWriter().u32(++token).build()
                }
                Op.SETTINGS_WRITE->{
                    val r=WireReader(p.frame.payload);val id=r.u16();assertEquals(4,id);enabled=r.text(r.u16());r.end()
                    pending=WireWriter().u16(id).bytes(text(enabled)).build();waiting=true;WireWriter().u32(++token).build()
                }
                Op.SETTINGS_RESULT->{val r=WireReader(p.frame.payload);assertEquals(token,r.u32());r.end();if(waiting){waiting=false;byteArrayOf(0,0)}else byteArrayOf(1,fail.toByte())+(if(fail==0)pending else byteArrayOf())}
                else->error("unexpected op $op")
            }
            incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,op,0,p.frame.requestId,bytes)),p.packetId))
        }
        override fun close(){incoming.close()}
    }
    @Test fun catalogueWriteAndActualReadbackUseOwnerResult()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        try{
            session.connect();val client=ChaosSettings(session);val item=client.load().single();assertEquals("屏幕翻转",item.label);assertEquals("0",item.value)
            assertEquals("1",client.write(item,"1"));assertEquals("1",client.load().single().value)
            assertTrue(link.operations.count{it==Op.SETTINGS_RESULT}>=6)
            link.mask=1;session.ping();val before=link.operations.count{it==Op.SETTINGS_WRITE}
            try{client.write(item,"0");fail("permission must reject locally")}catch(_:ProtocolException){}
            assertEquals(before,link.operations.count{it==Op.SETTINGS_WRITE})
        }finally{session.close()}
    }
    @Test fun ownerFailureNeverBecomesASuccessfulWrite()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        try{session.connect();val client=ChaosSettings(session);val item=client.load().single();link.fail=9
            try{client.write(item,"1");fail()}catch(e:DeviceStatusException){assertEquals(9,e.code);assertEquals(Op.SETTINGS_WRITE,e.opcode)}
        }finally{session.close()}
    }
    @Test fun invalidRangesReadOnlyAndTruncatedCataloguesAreRejected(){
        val numeric=BandSetting(1,1,1,10,"显示","秒数","3",emptyList())
        assertFalse(numeric.validate("11"));assertFalse(numeric.validate("-1"));assertFalse(numeric.validate("x"));assertTrue(numeric.validate("5"))
        assertFalse(numeric.copy(kind=5).validate("5"));assertFalse(numeric.copy(kind=3,minimum=0,maximum=5).validate("中文"))
        for(bytes in listOf(byteArrayOf(),byteArrayOf(1,1,0,0,0,0),byteArrayOf(2,1,0,1,0,1))){try{ChaosSettings.decode(bytes,0);fail()}catch(_:ProtocolException){}}
        assertEquals(16,MobilePermission.required(Op.SETTINGS_LIST,byteArrayOf()))
        assertEquals(16,MobilePermission.required(Op.SETTINGS_WRITE,byteArrayOf()))
        assertEquals(16,MobilePermission.required(Op.SETTINGS_RESULT,byteArrayOf()))
    }
}
