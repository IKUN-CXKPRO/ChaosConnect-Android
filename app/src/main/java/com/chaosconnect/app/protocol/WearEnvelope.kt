package com.chaosconnect.app.protocol

import java.io.ByteArrayOutputStream

/** Known fields only: WearPacket OTHER=0 → thirdparty_app(22) → message_content(9).
 * No invented protobuf fields or private Bluetooth PSM. */
object WearEnvelope {
    data class Packet(val packetId: Long, val frame: CcnCodec.Frame)
    private data class Field(val wire: Int,val value:Long=0,val bytes:ByteArray=byteArrayOf())
    private class Writer {
        private val out=ByteArrayOutputStream()
        fun varint(value:Long)=apply {require(value in 0..0xffffffffL);var v=value;do {out.write(((v and 127) or if(v>=128)128 else 0).toInt());v=v ushr 7}while(v!=0L)}
        fun number(field:Int,value:Long)=apply {varint((field shl 3).toLong());varint(value)}
        fun bytes(field:Int,value:ByteArray)=apply {varint(((field shl 3) or 2).toLong());varint(value.size.toLong());out.write(value)}
        fun build()=out.toByteArray()
    }
    private fun fields(data:ByteArray,targets:Set<Int>):Map<Int,Field> {
        val reader=WireReader(data);val result=mutableMapOf<Int,Field>()
        fun varint():Long {
            var value=0L
            repeat(5) {i -> val b=reader.u8();checkWire(i!=4 || b and 240==0,"protobuf varint 溢出");value=value or ((b and 127).toLong() shl (i*7));if(b and 128==0)return value}
            throw ProtocolException("protobuf varint 溢出")
        }
        while(reader.remaining>0) {
            val tag=varint();val key=(tag ushr 3).toInt();val wire=(tag and 7).toInt()
            checkWire(key>0,"protobuf tag 无效")
            val field=when(wire) {
                0 -> Field(wire,varint())
                2 -> {val length=varint();checkWire(length<=reader.remaining,"protobuf 长度无效");Field(wire,bytes=reader.bytes(length.toInt()))}
                1 -> Field(wire,bytes=reader.bytes(8))
                5 -> Field(wire,bytes=reader.bytes(4))
                else -> throw ProtocolException("不支持的 protobuf wire type")
            }
            if(key in targets)checkWire(result.put(key,field)==null,"重复的 protobuf 字段")
        }
        return result
    }
    private fun Map<Int,Field>.required(key:Int,wire:Int):Field {
        val f=this[key] ?: throw ProtocolException("缺少 protobuf 字段 $key")
        checkWire(f.wire==wire,"protobuf 字段类型错误");return f
    }
    fun wrap(frame:ByteArray,packetId:Long):ByteArray {
        CcnCodec.decode(frame)
        val basic=Writer().bytes(1,"ChaosConnect".toByteArray()).bytes(2,byteArrayOf()).build()
        val message=Writer().bytes(1,basic).bytes(2,frame).build()
        return Writer().number(1,0).number(2,packetId).bytes(22,Writer().bytes(9,message).build()).build()
    }
    /** Returns null for another app namespace/type. Malformed packets throw. */
    fun unwrap(data:ByteArray):Packet? {
        checkWire(data.size<=CcnCodec.MAX_WIRE,"承载包过大")
        val packet=fields(data,setOf(1,2,22))
        if(packet.required(1,0).value!=0L)return null
        val app=packet[22] ?: return null
        checkWire(app.wire==2,"thirdparty_app 类型错误")
        val messageField=fields(app.bytes,setOf(9))[9] ?: return null
        checkWire(messageField.wire==2,"message_content 类型错误")
        val message=fields(messageField.bytes,setOf(1,2))
        val basic=fields(message.required(1,2).bytes,setOf(1))
        if(!basic.required(1,2).bytes.contentEquals("ChaosConnect".toByteArray()))return null
        val id=packet.required(2,0).value
        val frame=CcnCodec.decode(message.required(2,2).bytes)
        checkWire(frame.requestId==(id and 65535).toInt(),"请求 ID 与承载不一致")
        return Packet(id,frame)
    }
}
