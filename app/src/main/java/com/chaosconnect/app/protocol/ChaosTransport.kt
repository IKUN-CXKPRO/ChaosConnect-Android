package com.chaosconnect.app.protocol

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** Complete WearPacket boundary for secure standard SPP or authenticated Xiaomi SAR/L2.
 * Each incoming element is one complete decoded WearPacket protobuf, not a BLE fragment.
 * send() must enqueue in order or throw; it must never silently drop a packet. */
interface ChaosTransport {
    val incoming: ReceiveChannel<ByteArray>
    val maximumPacketBytes: Int
    suspend fun send(packet: ByteArray)
    fun close()
}

/** Real callback adapter for a native/host transport. No simulated peer is installed. */
class AuthenticatedTransport(
    override val maximumPacketBytes: Int,
    private val sender: suspend (ByteArray) -> Unit,
    private val closer: () -> Unit,
) : ChaosTransport {
    init {require(maximumPacketBytes>=128)}
    private val queue=Channel<ByteArray>(32)
    override val incoming:ReceiveChannel<ByteArray> get()=queue
    override suspend fun send(packet:ByteArray) {require(packet.size<=maximumPacketBytes);sender(packet.copyOf())}
    fun receive(packet:ByteArray):Boolean {
        if(packet.size>CcnCodec.MAX_WIRE) {failed(ProtocolException("接收包超长"));return false}
        if(queue.trySend(packet.copyOf()).isSuccess)return true
        failed(ProtocolException("接收队列已满或链路已关闭"));return false
    }
    fun failed(cause:Throwable) {queue.close(cause)}
    override fun close() {queue.close();closer()}
}
