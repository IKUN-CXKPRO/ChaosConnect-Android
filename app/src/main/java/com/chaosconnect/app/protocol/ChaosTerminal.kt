package com.chaosconnect.app.protocol

/** A token belongs to one PTY. No automatic resending of command input. */
data class TerminalPacket(val lease:Long,val state:Int,val error:Int,val bytes:ByteArray) {
    companion object {
        fun decode(payload:ByteArray,expected:Long?=null):TerminalPacket {
            val r=WireReader(payload);val lease=r.u32();val state=r.u8();val error=r.u32().toInt();val bytes=r.bytes(r.remaining)
            checkWire(lease!=0L && state in 0..5 && (expected==null || lease==expected),"NSH会话状态无效")
            return TerminalPacket(lease,state,error,bytes)
        }
    }
}
class ChaosTerminal(private val session:ChaosSession) {
    private var lease=0L
    suspend fun open():TerminalPacket {
        checkWire(session.state.value.negotiation?.capabilities?.and(16)==16,"手环尚未支持远程NSH终端，请更新模块")
        val packet=TerminalPacket.decode(session.request(Op.NSH_OPEN).payload);lease=packet.lease;return packet
    }
    suspend fun read():TerminalPacket {
        checkWire(lease!=0L,"NSH尚未启动")
        val chunk=session.state.value.negotiation?.chunk ?: 256
        return TerminalPacket.decode(session.request(Op.NSH_READ,WireWriter().u32(lease).u16(chunk).build()).payload,lease)
    }
    suspend fun send(bytes:ByteArray) {
        val limit=session.state.value.negotiation?.chunk ?: 0
        checkWire(lease!=0L && bytes.isNotEmpty() && bytes.size<=limit && bytes.none{it==0.toByte() || it==4.toByte()},"NSH输入过长或包含不支持的字符")
        WireReader(session.request(Op.NSH_WRITE,WireWriter().u32(lease).bytes(bytes).build()).payload).end()
    }
    suspend fun close() {
        val token=lease;lease=0
        if(token!=0L)WireReader(session.request(Op.NSH_CLOSE,WireWriter().u32(token).build()).payload).end()
    }
}
