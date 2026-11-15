@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.chaosconnect.app.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    private class Link:ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        val queued=mutableListOf<WearEnvelope.Packet>()
        var closeCount=0
        override suspend fun send(packet:ByteArray) {
            val p=WearEnvelope.unwrap(packet)!!
            when(p.frame.opcode) {
                Op.HELLO -> answer(p,WireWriter().u8(1).u16(128).u8(15).u8(0).u16(255).u32(8).build())
                Op.PING -> answer(p,byteArrayOf(1,0,2,0,0,0,0,0))
                Op.GET_ACCESS -> answer(p,WireWriter().u8(1).u8(1).u8(0).u8(2).u32(31).u32(0).u8(1).u8(0).u8(0).u8(0).build())
                else -> queued.add(p)
            }
        }
        suspend fun answer(p:WearEnvelope.Packet,payload:ByteArray=p.frame.payload,prefix:Long=p.packetId ushr 16,op:Int=p.frame.opcode) {
            val frame=CcnCodec.Frame(1,op,0,p.frame.requestId,payload)
            incoming.send(WearEnvelope.wrap(CcnCodec.encode(frame),(prefix shl 16) or p.frame.requestId.toLong()))
        }
        override fun close(){closeCount++;incoming.close()}
    }
    @Test fun negotiatedWindowIgnoresStalePrefixAndMatchesOutOfOrderReplies()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect()
            val requests=(0..8).map {i->async {session.request(Op.ECHO,byteArrayOf(i.toByte()))}}
            runCurrent();assertEquals(8,link.queued.size)
            val first=link.queued.first()
            link.answer(first,prefix=0x9999);link.answer(first,op=Op.STAT);runCurrent()
            assertFalse(requests.first().isCompleted)
            link.queued.toList().reversed().forEach {link.answer(it)}
            runCurrent();assertEquals(9,link.queued.size)
            link.answer(link.queued.last())
            requests.forEachIndexed {i,job->assertArrayEquals(byteArrayOf(i.toByte()),job.await().payload)}
            assertTrue(session.state.value.rejectedPackets>=1)
        }finally {session.close()}
    }
    @Test fun validDisabledEventRevokesPendingWorkOnlyForCurrentPrefix()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        session.connect()
        val work=async {runCatching {session.request(Op.ECHO)}}
        runCurrent()
        val frame=CcnCodec.encode(CcnCodec.Frame(2,0xc2,0,0,byteArrayOf(0,0,2,0,0,0,0,0)))
        link.incoming.send(WearEnvelope.wrap(frame,0x43210000));runCurrent()
        assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
        link.incoming.send(WearEnvelope.wrap(frame,0x12340000));runCurrent()
        assertTrue(work.await().isFailure)
        assertEquals(SessionPhase.DISCONNECTED,session.state.value.phase)
        assertNull(session.state.value.permission);assertEquals(1,link.closeCount)
    }
    @Test fun timeoutDisconnectsWithoutReplayingMutatingRequests()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        session.connect()
        val work=async {runCatching {session.request(Op.MKDIR,WireWriter().path("/data/test").build(),timeout=50)}}
        runCurrent();advanceTimeBy(51);runCurrent()
        assertTrue(work.await().isFailure)
        assertEquals(1,link.queued.count {it.frame.opcode==Op.MKDIR})
        assertEquals(SessionPhase.DISCONNECTED,session.state.value.phase)
    }
    @Test fun callbackTransportDoesNotSilentlyDropQueueOverflow() {
        var closed=false
        val adapter=AuthenticatedTransport(4096,{}, {closed=true})
        repeat(32){assertTrue(adapter.receive(byteArrayOf(8,0)))}
        assertFalse(adapter.receive(byteArrayOf(8,0)))
        adapter.close();assertTrue(closed)
    }
}
