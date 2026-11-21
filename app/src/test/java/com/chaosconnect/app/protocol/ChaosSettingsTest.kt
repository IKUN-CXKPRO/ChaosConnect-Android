@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.chaosconnect.app.protocol

import com.chaosconnect.app.ui.BandSettingsController
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ChaosSettingsTest {
    private class Link(val fast:Boolean=false):ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        var mask=16L;var enabled="0";var token=0L;var pending=byteArrayOf();var waiting=false;var fail=0
        val operations=java.util.Collections.synchronizedList(mutableListOf<Int>())
        val limits=mutableListOf<Int>()
        private fun text(value:String):ByteArray {val b=value.toByteArray();return WireWriter().u16(b.size).bytes(b).build()}
        override suspend fun send(packet:ByteArray){
            val p=WearEnvelope.unwrap(packet)!!;val op=p.frame.opcode;operations+=op
            val bytes=when(op){
                Op.HELLO->WireWriter().u8(1).u16(2048).u8(if(fast)207 else 79).u8(0).u16(255).u32(8).build()
                Op.PING->byteArrayOf(1,0,2,0,0,0,0,0)
                Op.GET_ACCESS->WireWriter().u8(1).u8(1).u8(0).u8(2).u32(mask).u32(0).u8(1).u8(0).u8(0).u8(0).build()
                Op.SETTINGS_LIST->{
                    val r=WireReader(p.frame.payload);val offset=r.u16();val limit=if(fast)r.u8()else 8;r.end();limits+=limit
                    val end=minOf(offset+limit,10)
                    val page=WireWriter().u8(1).u16(10).u16(end).u8(end-offset)
                    for(i in offset until end)page.u16(4+i).u8(0).u32(0).u32(1).bytes(text("显示与交互")).bytes(text(if(i==0)"屏幕翻转" else "选项$i")).bytes(text(if(i<2)enabled else "0")).bytes(text(""))
                    pending=page.build();waiting=true;WireWriter().u32(++token).build()
                }
                Op.SETTINGS_WRITE,Op.SETTINGS_APPLY->{
                    assertEquals(if(fast)Op.SETTINGS_APPLY else Op.SETTINGS_WRITE,op)
                    val r=WireReader(p.frame.payload);val id=r.u16();assertEquals(4,id);enabled=r.text(r.u16());r.end()
                    pending=if(fast)WireWriter().u8(1).u8(0).u16(2).u16(id).u8(1).bytes(text(enabled)).u16(5).u8(1).bytes(text(enabled)).build()
                        else WireWriter().u16(id).bytes(text(enabled)).build()
                    waiting=true;WireWriter().u32(++token).build()
                }
                Op.SETTINGS_RESULT->{val r=WireReader(p.frame.payload);assertEquals(token,r.u32());r.end();if(waiting){waiting=false;byteArrayOf(0,0)}else byteArrayOf(1,fail.toByte())+(if(fail==0)pending else byteArrayOf())}
                else->error("unexpected op $op")
            }
            incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,op,0,p.frame.requestId,bytes)),p.packetId))
        }
        override fun close(){incoming.close()}
    }
    @Test fun legacyWriteReadsActualValueAndRevocationStillBlocksRequests()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        try{
            session.connect();val client=ChaosSettings(session);val item=client.load().first();assertEquals("屏幕翻转",item.label);assertEquals("0",item.value)
            val pages=link.operations.count{it==Op.SETTINGS_LIST}
            assertEquals("1",client.write(item,"1"));assertEquals(pages,link.operations.count{it==Op.SETTINGS_LIST})
            assertEquals(listOf(8,8),link.limits)
            link.mask=1;session.ping();val before=link.operations.count{it==Op.SETTINGS_WRITE}
            try{client.write(item,"0");fail("permission must reject locally")}catch(_:ProtocolException){}
            assertEquals(before,link.operations.count{it==Op.SETTINGS_WRITE})
        }finally{session.close()}
    }
    @Test fun controllerAppliesRelatedActualValuesWithoutAnotherCatalogueRead()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val link=Link(true);val session=ChaosSession(link,scope,0x1234);val controller=BandSettingsController(scope)
        try{
            session.connect();controller.bind(session);controller.load()
            val loaded=withTimeout(3_000){controller.state.first{it.loaded&&!it.busy}}
            assertEquals(10,loaded.items.size);assertEquals(listOf(32),link.limits)
            val before=link.operations.count{it==Op.SETTINGS_LIST}
            controller.write(loaded.items.first(),"1")
            val updated=withTimeout(3_000){controller.state.first{!it.busy&&it.items.firstOrNull()?.value=="1"}}
            assertEquals("1",updated.items.first{it.id==5}.value)
            assertEquals(before,link.operations.count{it==Op.SETTINGS_LIST});assertEquals(1,link.operations.count{it==Op.SETTINGS_APPLY})
            controller.write(updated.items.first(),"0")
            withTimeout(3_000){controller.state.first{!it.busy&&it.items.firstOrNull()?.value=="0"}}
            assertEquals(before,link.operations.count{it==Op.SETTINGS_LIST})
        }finally{controller.bind(null);session.close();scope.cancel()}
    }
    @Test fun ownerFailureNeverBecomesASuccessfulWrite()=runTest {
        val link=Link(true);val session=ChaosSession(link,backgroundScope,0x1234)
        try{session.connect();val client=ChaosSettings(session);val item=client.load().first();link.fail=9
            try{client.apply(item,"1");fail()}catch(e:DeviceStatusException){assertEquals(9,e.code);assertEquals(Op.SETTINGS_APPLY,e.opcode)}
        }finally{session.close()}
    }
    @Test fun sparseCaptionsOptionsAndRemovalsMergeWithoutLosingOtherSettings(){
        fun text(s:String)=WireWriter().u16(s.toByteArray().size).bytes(s.toByteArray()).build()
        val row=BandSetting(150,2,0,1,"字体","字体槽位","0",listOf("官方","测试"))
        val update=ChaosSettings.decodeUpdate(WireWriter().u8(1).u8(0).u16(2).u16(150).u8(7).bytes(text("1")).bytes(text("字体选择")).bytes(text("官方|新字体")).u16(1000).u8(8).build())
        val items=update.merge(listOf(row,row.copy(id=1000),row.copy(id=151)))
        assertEquals(listOf(150,151),items.map{it.id});assertEquals("1",items.first().value);assertEquals("字体选择",items.first().label);assertEquals("新字体",items.first().options[1]);assertEquals("0",items.last().value)
        for(bytes in listOf(byteArrayOf(),byteArrayOf(1,0,0,0),byteArrayOf(1,0,1,0,4,0,0))){try{ChaosSettings.decodeUpdate(bytes);fail()}catch(_:ProtocolException){}}
    }
    @Test fun invalidRangesReadOnlyAndTruncatedCataloguesAreRejected(){
        val numeric=BandSetting(1,1,1,10,"显示","秒数","3",emptyList())
        assertFalse(numeric.validate("11"));assertFalse(numeric.validate("-1"));assertFalse(numeric.validate("x"));assertTrue(numeric.validate("5"))
        assertFalse(numeric.copy(kind=5).validate("5"));assertFalse(numeric.copy(kind=3,minimum=0,maximum=5).validate("中文"))
        for(bytes in listOf(byteArrayOf(),byteArrayOf(1,1,0,0,0,0),byteArrayOf(2,1,0,1,0,1))){try{ChaosSettings.decode(bytes,0);fail()}catch(_:ProtocolException){}}
        for(op in listOf(Op.SETTINGS_LIST,Op.SETTINGS_WRITE,Op.SETTINGS_RESULT,Op.SETTINGS_APPLY))assertEquals(16,MobilePermission.required(op,byteArrayOf()))
    }
}
