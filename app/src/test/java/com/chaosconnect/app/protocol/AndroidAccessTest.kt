@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.chaosconnect.app.protocol
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import com.chaosconnect.app.ui.pairingStatus
import org.junit.Test
import org.junit.Assert.*
class AndroidAccessTest {
    private class Link:ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        var auth=1;var granted=false;var mask=1L;var closed=false;val operations=mutableListOf<Int>()
        fun access()=WireWriter().u8(1).u8(1).u8(2).u8(auth).u32(mask).u32(17).u8(if(granted)1 else 0).u8(0).u8(0).u8(0).build()
        override suspend fun send(packet:ByteArray) {
            val p=WearEnvelope.unwrap(packet)!!;operations.add(p.frame.opcode)
            val bytes=when(p.frame.opcode){
                Op.HELLO->{assertArrayEquals("CCA1".toByteArray(),p.frame.payload);WireWriter().u8(1).u16(2048).u8(15).u8(0).u16(255).u32(8).build()}
                Op.PING->byteArrayOf(1,0,2,0,0,0,0,0)
                Op.GET_ACCESS->access()
                else->byteArrayOf()
            }
            incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,p.frame.opcode,0,p.frame.requestId,bytes)),p.packetId))
        }
        override fun close(){closed=true;incoming.close()}
    }
    @Test fun approvalAndPermissionsAreSeparateFromBluetooth()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        try{
            session.connect();assertEquals(SessionPhase.AWAITING_APPROVAL,session.state.value.phase)
            try{session.request(Op.LIST);fail()}catch(_:ProtocolException){}
            assertFalse(link.operations.contains(Op.LIST))
            link.auth=2;link.granted=true;session.ping();assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
            session.request(Op.OPEN_READ)
            try{session.request(Op.OPEN_WRITE);fail()}catch(_:ProtocolException){}
            assertFalse(link.operations.contains(Op.OPEN_WRITE))
            link.mask=3;session.ping();session.request(Op.OPEN_WRITE)
            link.auth=3;link.granted=false
            try{session.ping();fail()}catch(_:ProtocolException){}
            assertEquals(SessionPhase.DISCONNECTED,session.state.value.phase);assertTrue(link.closed)
        }finally{session.close()}
    }
    @Test fun heartbeatRefreshesApprovalAndThePairingScreenWithoutAnotherRequest()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        val carrier=BluetoothState(message="蓝牙链路已建立，请在手环确认ChaosConnect连接")
        try {
            session.connect();runCurrent()
            assertEquals("等待手环确认连接",pairingStatus(carrier,session.state.value).title)
            link.auth=2;link.granted=true
            advanceTimeBy(1001);runCurrent()
            assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
            assertEquals("设备已连接",pairingStatus(carrier,session.state.value).title)
            assertEquals("已连接 · 权限由手环控制",pairingStatus(carrier,session.state.value).message)
            link.auth=3;link.granted=false
            advanceTimeBy(3001);runCurrent()
            assertEquals(SessionPhase.DISCONNECTED,session.state.value.phase)
            assertEquals("会话已断开",pairingStatus(carrier,session.state.value).title)
            assertEquals("手环已拒绝或撤销Android连接",pairingStatus(carrier,session.state.value).message)
        } finally {session.close()}
    }
    @Test fun malformedOrContradictoryGrantsAreRejected(){
        val link=Link();val correct=link.access();assertTrue(AndroidAccess.decode(correct).waiting)
        for(at in listOf(0,1,2,3,13)){val bad=correct.copyOf();bad[at]=99;try{AndroidAccess.decode(bad);fail()}catch(_:ProtocolException){}}
        val missingToken=correct.copyOf();for(i in 8..11)missingToken[i]=0
        try{AndroidAccess.decode(missingToken);fail()}catch(_:ProtocolException){}
        val contradictory=correct.copyOf();contradictory[12]=1
        try{AndroidAccess.decode(contradictory);fail()}catch(_:ProtocolException){}
    }
}
