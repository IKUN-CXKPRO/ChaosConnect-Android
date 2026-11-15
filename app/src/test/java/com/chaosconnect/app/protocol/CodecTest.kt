package com.chaosconnect.app.protocol

import org.junit.Assert.*
import org.junit.Test

class CodecTest {
    private fun hex(text:String)=text.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private fun rejects(block:()->Unit) {try {block();fail("Expected rejection")}catch(_:ProtocolException){}}
    @Test fun sharedModuleVectorMatchesBothLayers() {
        assertTrue(CcnCodec.selfCheck())
        val packet=WearEnvelope.unwrap(hex(CcnCodec.PACKET_VECTOR))!!
        assertEquals(0x12341234L,packet.packetId);assertEquals(Op.HELLO,packet.frame.opcode)
        assertEquals(0x1234,packet.frame.requestId)
    }
    @Test fun everyTruncationAndBitCorruptionIsRejected() {
        val frame=CcnCodec.encode(CcnCodec.Frame(1,Op.READ,0,65535,ByteArray(2048){it.toByte()}))
        for(n in 0 until frame.size)rejects {CcnCodec.decode(frame.copyOf(n))}
        for(at in frame.indices)rejects {CcnCodec.decode(frame.copyOf().also {it[at]=(it[at].toInt() xor 1).toByte()})}
        rejects {CcnCodec.decode(frame+0)}
    }
    @Test fun envelopeChecksNamespaceDuplicatesOverflowAndIds() {
        val packet=hex(CcnCodec.PACKET_VECTOR)
        assertNull(WearEnvelope.unwrap(packet.copyOf().also {it[1]=1})) // OTHER → another type
        assertNull(WearEnvelope.unwrap(packet.copyOf().also {val i=it.indexOf('C'.code.toByte());it[i]='X'.code.toByte()}))
        rejects {WearEnvelope.unwrap(packet+byteArrayOf(8,0))}
        rejects {WearEnvelope.unwrap(byteArrayOf(8,-1,-1,-1,-1,0x10))}
        val frame=CcnCodec.encode(CcnCodec.Frame(1,Op.PING,0,7))
        rejects {WearEnvelope.unwrap(WearEnvelope.wrap(frame,0x12340008))}
    }
    @Test fun pathsUseUtf8BytesAndRejectEscapingRoot() {
        val path=WireWriter().path("/data/目录/../文件.txt").build()
        val r=WireReader(path);assertEquals("/data/文件.txt",r.text(r.u16()));r.end()
        try {WireWriter().path("/../../data");fail()}catch(_:IllegalArgumentException){}
        try {WireWriter().path("/data/"+"字".repeat(100));fail()}catch(_:IllegalArgumentException){}
        rejects {WireReader(byteArrayOf(-61)).text(1)}
    }
}
