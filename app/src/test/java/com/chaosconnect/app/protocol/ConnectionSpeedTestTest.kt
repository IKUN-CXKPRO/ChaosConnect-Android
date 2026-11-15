@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.chaosconnect.app.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionSpeedTestTest {
    private class Link(private val scope:CoroutineScope):ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(64)
        var sent=0L;var pending=0;var maximum=0;var corrupt=false;var corruptFrom=0L;var chunk=128;var writeDelay=0L;var cancelledWrites=0
        override suspend fun send(packet:ByteArray) {
            val p=WearEnvelope.unwrap(packet)!!;val f=p.frame
            if(f.opcode==Op.ECHO && writeDelay>0) {
                try{delay(writeDelay)}catch(e:CancellationException){cancelledWrites++;throw e}
            }
            val data=when(f.opcode) {
                Op.HELLO->WireWriter().u8(1).u16(chunk).u8(31).u8(0).u16(255).u32(4).build()
                Op.PING->byteArrayOf(1,0,0,0,0,0,0,0)
                Op.GET_ACCESS->WireWriter().u8(1).u8(1).u8(0).u8(2).u32(1).u32(0).u8(1).u8(0).u8(0).u8(0).build()
                Op.ECHO->f.payload.copyOf().also{assertTrue(it.size<=chunk);sent+=it.size;if(corrupt && sent>corruptFrom)it[0]=(it[0].toInt() xor 1).toByte()}
                else->error("Unexpected opcode")
            }
            if(f.opcode==Op.ECHO) {
                pending++;maximum=maxOf(maximum,pending)
                scope.launch{delay(10);pending--;incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,f.opcode,0,f.requestId,data)),p.packetId))}
            }else incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,f.opcode,0,f.requestId,data)),p.packetId))
        }
        override fun close(){incoming.close()}
    }
    @Test fun windowedTestCountsOnlyVerifiedPayloadAndExcludesWarmup()=runTest {
        val link=Link(backgroundScope);val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect();val progress=mutableListOf<SpeedProgress>()
            val job=launch {ConnectionSpeedTest(session){testScheduler.currentTime*1_000_000}.run{progress.add(it)}}
            advanceTimeBy(211);runCurrent()
            val result=progress.last()
            assertEquals(4,link.maximum);assertEquals(result.sent,result.received)
            // First reply in the 200ms batch triggers the throttled update;
            // the other three verified replies remain for the next update.
            assertEquals(200L,result.elapsedMillis);assertEquals(9856L,result.received)
            assertEquals(49280L,result.bytesPerSecond);assertTrue(job.isActive)
            job.cancelAndJoin();advanceTimeBy(20);runCurrent()
            assertEquals(84L*128,link.sent-32)
        }finally{session.close()}
    }
    @Test fun corruptionNeverBecomesASpeedResult()=runTest {
        val link=Link(backgroundScope);val session=ChaosSession(link,backgroundScope,0x1234)
        try {session.connect();link.corrupt=true;link.corruptFrom=32
            try {ConnectionSpeedTest(session){testScheduler.currentTime*1_000_000}.run()}catch(_:ProtocolException){}
        }finally{session.close()}
    }
    @Test fun stopDuringAnActiveWriteDrainsOnlyTheBoundedWindowWithoutBreakingTheSocket()=runTest {
        val link=Link(backgroundScope);val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect();link.writeDelay=5
            val job=launch {ConnectionSpeedTest(session){testScheduler.currentTime*1_000_000}.run()}
            advanceTimeBy(17);runCurrent();job.cancelAndJoin()
            advanceTimeBy(100);runCurrent()
            assertEquals(0,link.cancelledWrites);assertEquals(32L+128*4,link.sent)
            val total=link.sent;advanceTimeBy(100);runCurrent();assertEquals(total,link.sent)
            assertEquals(SessionPhase.CONNECTED,session.state.value.phase);session.ping()
        }finally{session.close()}
    }
    @Test fun cancellationDoesNotCloseTheConnectionOrKeepSending()=runTest {
        val link=Link(backgroundScope);val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect();val job=launch {ConnectionSpeedTest(session){testScheduler.currentTime*1_000_000}.run()}
            advanceTimeBy(25);runCurrent();job.cancelAndJoin();val sent=link.sent
            advanceTimeBy(100);runCurrent();assertEquals(sent,link.sent);assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
            session.ping()
        }finally{session.close()}
    }
    @Test fun keepsRunningPastTimeByteAndSixteenBitRequestLimits()=runTest {
        val link=Link(backgroundScope);val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect();var latest=SpeedProgress()
            val job=launch {ConnectionSpeedTest(session){testScheduler.currentTime*1_000_000}.run{latest=it}}
            advanceTimeBy(180_011);runCurrent()
            assertTrue(job.isActive);assertTrue(latest.elapsedMillis>30_000)
            assertTrue(latest.received>8L*1024*1024);assertTrue((link.sent-32)/128>65535)
            job.cancelAndJoin();advanceTimeBy(20);runCurrent()
            session.ping();assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
        }finally{session.close()}
    }
    @Test fun stoppingDuringWarmupDoesNotCancelAnActiveSocketWrite()=runTest {
        val link=Link(backgroundScope);val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect();link.writeDelay=5
            val job=launch {ConnectionSpeedTest(session){testScheduler.currentTime*1_000_000}.run()}
            advanceTimeBy(2);runCurrent();job.cancelAndJoin()
            advanceTimeBy(30);runCurrent()
            assertEquals(0,link.cancelledWrites);assertEquals(32L,link.sent)
            session.ping();assertEquals(SessionPhase.CONNECTED,session.state.value.phase)
        }finally{session.close()}
    }
}
