@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.chaosconnect.app.protocol

import com.chaosconnect.app.ui.TerminalController
import com.chaosconnect.app.ui.TerminalTranscript
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ChaosTerminalTest {
    private class Link:ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        var mask=33L;var lease=7L;var state=2;var closes=0;var opens=0
        var output="nsh> ".toByteArray();val inputs=mutableListOf<String>()
        override suspend fun send(packet:ByteArray) {
            val p=WearEnvelope.unwrap(packet)!!;val f=p.frame
            val data=when(f.opcode) {
                Op.HELLO->WireWriter().u8(1).u16(2048).u8(31).u8(0).u16(255).u32(8).build()
                Op.PING->byteArrayOf(1,0,0,0,0,0,0,0)
                Op.GET_ACCESS->WireWriter().u8(1).u8(1).u8(0).u8(2).u32(mask).u32(0).u8(1).u8(0).u8(0).u8(0).build()
                Op.NSH_OPEN->{opens++;WireWriter().u32(lease).u8(state).u32(0).build()}
                Op.NSH_READ->{val r=WireReader(f.payload);assertEquals(lease,r.u32());assertEquals(2048,r.u16());r.end()
                    WireWriter().u32(lease).u8(state).u32(0).bytes(output).build().also{output=byteArrayOf()}}
                Op.NSH_WRITE->{val r=WireReader(f.payload);assertEquals(lease,r.u32());inputs.add(r.bytes(r.remaining).toString(Charsets.UTF_8));byteArrayOf()}
                Op.NSH_CLOSE->{closes++;byteArrayOf()}
                else->error("Unexpected operation")
            }
            incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,f.opcode,0,f.requestId,data)),p.packetId))
        }
        override fun close(){incoming.close()}
    }
    @Test fun terminalUsesTheAuthorizedLeaseForRealInputAndOutput()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234)
        try {
            session.connect();val terminal=ChaosTerminal(session)
            assertEquals(7L,terminal.open().lease);assertEquals("nsh> ",terminal.read().bytes.toString(Charsets.UTF_8))
            terminal.send("ls /data\n".toByteArray());assertEquals(listOf("ls /data\n"),link.inputs)
            terminal.close();assertEquals(1,link.closes)
            for(bytes in listOf(byteArrayOf(0),byteArrayOf(4),ByteArray(2049){65})) {
                try {terminal.send(bytes);fail()}catch(_:ProtocolException){}
            }
        }finally{session.close()}
    }
    @Test fun permissionRevocationStopsPollingAndDoesNotSendCommands()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234);val controller=TerminalController(backgroundScope)
        try {
            session.connect();controller.bind(session);controller.visible=true;controller.start();runCurrent()
            assertTrue(controller.state.value.active);assertEquals("nsh> ",controller.state.value.output)
            controller.sendCommand("pwd");runCurrent();assertEquals(listOf("pwd\n"),link.inputs)
            link.mask=1;session.ping();advanceTimeBy(151);runCurrent()
            assertFalse(controller.state.value.active)
            controller.sendCommand("reboot");runCurrent();assertEquals(1,link.inputs.size)
        }finally{controller.bind(null);session.close()}
    }
    @Test fun repeatedStopAndRestartWaitsForThePreviousLeaseToClose()=runTest {
        val link=Link();val session=ChaosSession(link,backgroundScope,0x1234);val controller=TerminalController(backgroundScope)
        try {
            session.connect();controller.bind(session);controller.start();runCurrent();assertEquals(1,link.opens)
            controller.stop();controller.start();runCurrent();assertEquals(1,link.closes);assertEquals(2,link.opens)
            controller.stop();runCurrent();assertEquals(2,link.closes)
        }finally{controller.bind(null);session.close()}
    }
    @Test fun splitUtf8AnsiBackspaceAndOutputBoundariesRemainReadable() {
        val transcript=TerminalTranscript();val bytes="你好\u001b[31m!\u001b[0m\n".toByteArray()
        var result="";for(b in bytes)result=transcript.append(byteArrayOf(b));assertEquals("你好!\n",result)
        assertEquals("你好!\nX",transcript.append("test\rX".toByteArray()))
        assertEquals("你好!\nYZ",transcript.append("\bYZ".toByteArray()))
        assertTrue(transcript.append("a".repeat(40000).toByteArray()).length<=32768)
        transcript.clear();assertEquals("",transcript.append(byteArrayOf()))
    }
    @Test fun malformedOrStaleTerminalPacketsAndPermissionBitsAreRejected() {
        assertEquals(32,MobilePermission.required(Op.NSH_OPEN,byteArrayOf()))
        assertTrue(MobilePermission.labels(32).contains("NSH终端"))
        for(payload in listOf(byteArrayOf(),WireWriter().u32(0).u8(2).u32(0).build(),WireWriter().u32(4).u8(8).u32(0).build())) {
            try{TerminalPacket.decode(payload);fail()}catch(_:Exception){}
        }
        try{TerminalPacket.decode(WireWriter().u32(4).u8(2).u32(0).build(),5);fail()}catch(_:ProtocolException){}
    }
}
