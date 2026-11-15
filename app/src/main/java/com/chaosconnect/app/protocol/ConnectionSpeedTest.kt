package com.chaosconnect.app.protocol

import kotlinx.coroutines.*

data class SpeedProgress(val sent:Long=0,val received:Long=0,val elapsedMillis:Long=0) {
    val bytesPerSecond get()=if(elapsedMillis>0)received*1000/elapsedMillis else 0
}
/** Uses real CCN1 echo frames and the negotiated in-flight window. Counts only
 * byte-verified replies, excludes warmup, and never creates files on the watch. */
class ConnectionSpeedTest(private val session:ChaosSession,private val now:()->Long=System::nanoTime) {
    suspend fun run(onProgress:(SpeedProgress)->Unit={}):Nothing {
        checkWire(session.state.value.phase==SessionPhase.CONNECTED,"请先连接并在手环确认")
        val n=session.state.value.negotiation ?: throw ProtocolException("连接尚未完成协商")
        val warmup=ByteArray(minOf(32,n.chunk)){(it*29+7).toByte()}
        // Session-owned writes may finish after the caller stops the test.
        checkWire(session.submit(Op.ECHO,warmup).await().payload.contentEquals(warmup),"测速回显校验失败")
        val start=now();var notified=start;var received=0L;var sequence=0L
        val queue=ArrayDeque<Pair<ByteArray,Deferred<CcnCodec.Frame>>>()
        fun elapsed()=((now()-start).coerceAtLeast(0)/1_000_000).coerceAtLeast(1)
        try {
            while(true) {
                currentCoroutineContext().ensureActive()
                while(queue.size<n.window) {
                    currentCoroutineContext().ensureActive()
                    val id=sequence++
                    val data=ByteArray(n.chunk){i->((i*37L+id*19) xor (id ushr ((i%8)*8))).toByte()}
                    queue.addLast(data to session.submit(Op.ECHO,data))
                }
                val (data,pending)=queue.removeFirst()
                val frame=pending.await()
                checkWire(frame.payload.contentEquals(data),"测速数据校验失败，结果未计入")
                received+=data.size
                val t=now()
                if(t-notified>=200_000_000L){notified=t;onProgress(SpeedProgress(received,received,elapsed()))}
            }
        }finally{
            // Already-submitted RPCs belong to the session. Let this bounded
            // window finish: cancelling a native socket write halfway through
            // a record would force the transport to close the whole connection.
            // No more frames are enqueued once the benchmark is cancelled.
            queue.clear()
        }
    }
}
