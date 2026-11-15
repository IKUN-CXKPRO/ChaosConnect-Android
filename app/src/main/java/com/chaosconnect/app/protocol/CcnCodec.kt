package com.chaosconnect.app.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

class ProtocolException(message: String) : Exception(message)

/** Byte-for-byte CCN1 v1 implementation from the public wire contract. */
object CcnCodec {
    const val HEADER = 16
    const val MAX_CHUNK = 2048
    const val MAX_PAYLOAD = 2304
    const val MAX_FRAME = HEADER + MAX_PAYLOAD
    const val MAX_WIRE = MAX_FRAME + 96
    const val REQUEST = 0
    const val RESPONSE = 1
    const val EVENT = 2

    data class Frame(val flags: Int, val opcode: Int, val status: Int, val requestId: Int, val payload: ByteArray = byteArrayOf())
    fun encode(frame: Frame): ByteArray {
        require(frame.flags in 0..2 && frame.opcode in 0..255 && frame.status in 0..255 && frame.requestId in 0..65535)
        require(frame.payload.size <= MAX_PAYLOAD)
        val result = WireWriter().bytes(byteArrayOf(67,67,78,49)).u8(1).u8(frame.flags).u8(frame.opcode)
            .u8(frame.status).u16(frame.requestId).u16(frame.payload.size).u32(0).bytes(frame.payload).build()
        val crc = CRC32().apply { update(result,0,12); update(result,HEADER,frame.payload.size) }.value
        repeat(4) { result[12+it] = (crc ushr (it*8)).toByte() }
        return result
    }
    fun decode(bytes: ByteArray): Frame {
        checkWire(bytes.size in HEADER..MAX_FRAME,"帧长度无效")
        val r=WireReader(bytes)
        checkWire(r.bytes(4).contentEquals(byteArrayOf(67,67,78,49)) && r.u8()==1,"不是 CCN1 v1")
        val flags=r.u8(); val op=r.u8(); val status=r.u8(); val id=r.u16(); val length=r.u16(); val expected=r.u32()
        checkWire(flags in 0..2 && length==r.remaining,"标志或载荷长度无效")
        val payload=r.bytes(length)
        val crc=CRC32().apply {update(bytes,0,12);update(payload)}.value
        checkWire(crc==expected,"CRC32 校验失败")
        return Frame(flags,op,status,id,payload)
    }
    const val HELLO_VECTOR = "43434e310100010034120000d0f4a6ad"
    const val PACKET_VECTOR = "080010b4a4d09101b201264a240a100a0c4368616f73436f6e6e6563741200121043434e310100010034120000d0f4a6ad"
    fun selfCheck(): Boolean = encode(Frame(REQUEST,1,0,0x1234)).hex()==HELLO_VECTOR &&
        WearEnvelope.wrap(encode(Frame(REQUEST,1,0,0x1234)),0x12341234).hex()==PACKET_VECTOR
}

internal fun checkWire(valid: Boolean, message: String) { if(!valid) throw ProtocolException(message) }
fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

class WireWriter {
    private val out=ByteArrayOutputStream()
    fun u8(value: Int)=apply {require(value in 0..255);out.write(value)}
    fun u16(value: Int)=apply {require(value in 0..65535);repeat(2){out.write(value ushr (it*8) and 255)}}
    fun u32(value: Long)=apply {require(value in 0..0xffffffffL);repeat(4){out.write((value ushr (it*8)).toInt() and 255)}}
    fun bytes(value: ByteArray)=apply {out.write(value)}
    fun path(value: String)=apply {
        require(value.startsWith('/') && value.none {it.code<32 || it=='\u007f'}) {"路径必须是绝对路径且不含控制字符"}
        val normalized=ArrayDeque<String>()
        value.split('/').forEach {part -> when(part) {"","." -> Unit;".." -> {require(normalized.isNotEmpty());normalized.removeLast()};else -> normalized.addLast(part)}}
        val raw=("/"+normalized.joinToString("/")).toByteArray(Charsets.UTF_8)
        require(raw.size in 1..255) {"路径不能超过 255 个 UTF-8 字节"}
        u16(raw.size).bytes(raw)
    }
    fun build(): ByteArray=out.toByteArray()
}

class WireReader(private val data: ByteArray) {
    private var at=0
    val remaining get()=data.size-at
    fun u8(): Int {checkWire(remaining>=1,"载荷截断");return data[at++].toInt() and 255}
    fun u16(): Int=u8() or (u8() shl 8)
    fun u32(): Long {var result=0L;repeat(4){result=result or (u8().toLong() shl (it*8))};return result}
    fun bytes(size: Int): ByteArray {checkWire(size>=0 && size<=remaining,"载荷截断");return data.copyOfRange(at,at+size).also {at+=size}}
    fun text(size:Int):String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes(size))).toString()
    } catch(e:java.nio.charset.CharacterCodingException) {throw ProtocolException("文件名 UTF-8 无效")}
    fun end()=checkWire(remaining==0,"载荷有多余数据")
}
