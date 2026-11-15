package com.chaosconnect.app.protocol

import java.security.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

object Op {
    const val HELLO=1;const val LIST=2;const val STAT=3;const val MKDIR=4;const val REMOVE=5
    const val RMDIR=6;const val RENAME=7;const val COPY=8
    const val OPEN_READ=0x10;const val READ=0x11;const val CLOSE_READ=0x12
    const val OPEN_WRITE=0x20;const val WRITE=0x21;const val COMMIT=0x22;const val ABORT=0x23
    const val PING=0x30;const val ECHO=0x31;const val SET_DEV=0x40;const val GET_DEV=0x41;const val GET_ACCESS=0x42;const val DISCONNECT=0x43
    const val NSH_OPEN=0x50;const val NSH_WRITE=0x51;const val NSH_READ=0x52;const val NSH_CLOSE=0x53
}
data class Negotiation(val chunk:Int,val capabilities:Int,val maximumPath:Int,val window:Int)
data class DevicePermission(val connected:Boolean,val developer:Boolean,val policy:Int,val authorization:Int,val token:Long) {
    val waiting get()=authorization==1
    companion object {
        fun decode(bytes:ByteArray):DevicePermission {
            val r=WireReader(bytes);val connected=r.u8();val developer=r.u8();val policy=r.u8();val auth=r.u8();val token=r.u32();r.end()
            checkWire(connected in 0..1 && developer in 0..1 && policy in 0..2 && auth in 0..4,"权限状态无效")
            checkWire(auth!=1 || token!=0L,"等待授权却没有 token")
            return DevicePermission(connected==1,developer==1,policy,auth,token)
        }
    }
}
object MobilePermission {
    const val READ=1;const val WRITE=2;const val MANAGE=4;const val DEVELOPER=8;const val CONTROL=16;const val NSH=32
    fun required(op:Int,payload:ByteArray):Int=when(op) {
        Op.LIST,Op.STAT,Op.OPEN_READ,Op.READ->READ
        Op.MKDIR,Op.OPEN_WRITE,Op.WRITE,Op.COMMIT->WRITE
        Op.REMOVE,Op.RMDIR,Op.RENAME->MANAGE
        Op.COPY->READ or WRITE
        Op.NSH_OPEN,Op.NSH_WRITE,Op.NSH_READ,Op.NSH_CLOSE->NSH
        Op.SET_DEV->if(payload.firstOrNull()==1.toByte())DEVELOPER else 0
        else->0
    }
    fun labels(mask:Long):String=listOf(READ to "读取文件",WRITE to "写入文件",MANAGE to "删除和重命名",DEVELOPER to "开发者模式",CONTROL to "设备控制（预留）",NSH to "NSH终端")
        .filter {mask and it.first.toLong()!=0L}.joinToString("、") {it.second}.ifEmpty {"无文件或控制权限"}
}
data class AndroidAccess(val android:Boolean,val connectionPolicy:Int,val authorization:Int,val permissions:Long,val token:Long,val granted:Boolean) {
    val waiting get()=authorization==1
    fun allows(mask:Int)=granted && permissions and mask.toLong()==mask.toLong()
    companion object {
        fun decode(bytes:ByteArray):AndroidAccess {
            val r=WireReader(bytes);val version=r.u8();val kind=r.u8();val policy=r.u8();val auth=r.u8()
            val mask=r.u32();val token=r.u32();val granted=r.u8();val reserved=List(3){r.u8()};r.end()
            checkWire(version==1 && kind in 0..1 && policy in 0..2 && auth in 0..4 && mask and 63L.inv()==0L && granted in 0..1 && reserved.all{it==0},"Android授权状态无效")
            checkWire(auth!=1 || token!=0L,"连接授权缺少token")
            checkWire(granted==0 || (auth==2 && policy!=1),"授权状态相互矛盾")
            return AndroidAccess(kind==1,policy,auth,mask,token,granted==1)
        }
    }
}
enum class SessionPhase { DISCONNECTED, HANDSHAKING, AWAITING_APPROVAL, CONNECTED }
data class SessionState(val phase:SessionPhase=SessionPhase.DISCONNECTED,val negotiation:Negotiation?=null,
    val permission:DevicePermission?=null,val access:AndroidAccess?=null,val message:String="等待蓝牙连接与手环确认",val rejectedPackets:Long=0,
    val attempted:Boolean=false)
class DeviceStatusException(val code:Int,val opcode:Int):Exception(statusText(code))
fun statusText(code:Int):String = listOf("成功","请求格式错误","路径不存在","没有权限","目标已存在","不是目录","是目录","空间不足","设备忙或目录非空","读写失败","CRC 校验失败","偏移顺序错误","不支持","超出大小限制","等待手环授权").getOrElse(code){"设备错误 $code"}

/** One instance owns one link lease. Request IDs are never reused within it. */
class ChaosSession(private val transport:ChaosTransport,parentScope:CoroutineScope,
    private val prefix:Int=SecureRandom().nextInt(65535)+1,private val requestTimeoutMillis:Long=15_000) {
    init {require(prefix in 1..65535 && requestTimeoutMillis>0)}
    private val scope=CoroutineScope(parentScope.coroutineContext+SupervisorJob(parentScope.coroutineContext[Job]))
    private val lock=Any()
    private val sendLock=Mutex()
    private var slots=Semaphore(8)
    private var nextId=1
    private var closed=false
    private var started=false
    private data class Pending(val opcode:Int,val reply:CompletableDeferred<CcnCodec.Frame>)
    private val pending=mutableMapOf<Int,Pending>()
    private val mutableState=MutableStateFlow(SessionState())
    val state:StateFlow<SessionState> = mutableState.asStateFlow()
    private val progressEvents=MutableStateFlow<Pair<Long,Long>?>(null)
    val progress:StateFlow<Pair<Long,Long>?> = progressEvents.asStateFlow()

    suspend fun connect():Negotiation {
        synchronized(lock) {check(!started && !closed);started=true}
        mutableState.value=SessionState(phase=SessionPhase.HANDSHAKING,message="正在协商 CCN1",attempted=true)
        scope.launch(start=CoroutineStart.UNDISPATCHED) {
            try {for(bytes in transport.incoming)receive(bytes);close("设备链路已关闭")}
            catch(e:CancellationException){throw e}
            catch(e:Exception){close("设备链路中断")}
        }
        try {
            val r=WireReader(request(Op.HELLO,"CCA1".toByteArray(Charsets.US_ASCII)).payload)
            val version=r.u8();val chunk=r.u16();val flags=r.u8();val dev=r.u8();val maxPath=r.u16();val window=r.u32();r.end()
            checkWire(version==1 && chunk in 1..2048 && dev in 0..1 && maxPath in 1..255 && window in 1..8,"HELLO 协商参数无效")
            checkWire(flags and 12==12,"模块未提供Android连接授权能力，请更新Chaos模块")
            val negotiation=Negotiation(chunk,flags,maxPath,window.toInt())
            slots=Semaphore(negotiation.window)
            mutableState.update {it.copy(negotiation=negotiation)}
            val presence=ping()
            checkWire(presence.connected,"设备没有建立 CCN1 会话")

            scope.launch {
                try {while(isActive) {delay(if(state.value.permission?.waiting==true || state.value.access?.waiting==true)1_000 else 3_000);ping(10_000)}}
                catch(e:CancellationException){throw e}
                catch(e:Exception){close("心跳失败，CCN1 会话已断开")}
            }
            return negotiation
        } catch(e:Exception) {close(if(e is TimeoutCancellationException)"连接协商超时" else "CCN1 协商失败");throw e}
    }

    private fun receive(bytes:ByteArray) {
        if(synchronized(lock){closed})return
        val packet=try {WearEnvelope.unwrap(bytes)}catch(e:ProtocolException) {null}
        if(packet==null || packet.packetId ushr 16 != prefix.toLong()) {
            mutableState.update {it.copy(rejectedPackets=it.rejectedPackets+1)};return
        }
        val frame=packet.frame
        if(frame.flags==CcnCodec.EVENT && frame.requestId==0 && frame.status==0) {
            when(frame.opcode) {
                0xc2 -> {
                    val presence=try {DevicePermission.decode(frame.payload)}catch(e:ProtocolException){return}
                    if(!presence.connected && !presence.developer && presence.authorization==0 && presence.token==0L)close("手环已停用 ChaosConnect")
                }
                0xc1 -> if(frame.payload.size==8) {val r=WireReader(frame.payload);progressEvents.value=r.u32() to r.u32()}
            }
            return
        }
        if(frame.flags!=CcnCodec.RESPONSE)return
        synchronized(lock) {
            val expected=pending[frame.requestId] ?: return
            if(expected.opcode==frame.opcode)expected.reply.complete(frame)
        }
    }

    /** Invocations enqueue in order. Used in bounded batches for the negotiated window. */
    internal fun submit(opcode:Int,payload:ByteArray=byteArrayOf(),timeout:Long=requestTimeoutMillis):Deferred<CcnCodec.Frame> = scope.async(start=CoroutineStart.UNDISPATCHED) {
        try {
            withTimeout(timeout) {
                slots.withPermit {
                    val reply=CompletableDeferred<CcnCodec.Frame>()
                    var id=0
                    try {
                        sendLock.withLock {
                            synchronized(lock) {
                                checkWire(!closed && started,"CCN1 尚未连接")
                                checkWire(nextId<=65535,"请求编号已用尽，请重新建立会话")
                                checkWire(state.value.phase==SessionPhase.CONNECTED || opcode in listOf(Op.HELLO,Op.PING,Op.GET_ACCESS,Op.DISCONNECT),"尚未完成握手")
                                checkWire(state.value.permission?.waiting!=true || opcode in listOf(Op.PING,Op.SET_DEV,Op.GET_DEV,Op.GET_ACCESS,Op.DISCONNECT),"请先在手环完成授权或取消")
                                state.value.access?.let {access ->
                                    if(opcode !in listOf(Op.HELLO,Op.PING,Op.GET_ACCESS,Op.DISCONNECT))
                                        checkWire(access.allows(MobilePermission.required(opcode,payload)),"手环未授予此操作权限")
                                }
                                id=nextId++;pending[id]=Pending(opcode,reply)
                            }
                            val frame=CcnCodec.encode(CcnCodec.Frame(0,opcode,0,id,payload.copyOf()))
                            val packet=WearEnvelope.wrap(frame,(prefix.toLong() shl 16) or id.toLong())
                            checkWire(packet.size<=transport.maximumPacketBytes,"消息超过当前承载容量")
                            try {transport.send(packet)}catch(e:CancellationException){throw e}
                            catch(e:Exception){close("设备发送失败，会话已断开");throw e}
                        }
                        val response=reply.await()
                        if(response.status!=0 && !(opcode==Op.SET_DEV && response.status==14))throw DeviceStatusException(response.status,opcode)
                        response
                    } finally {synchronized(lock){pending.remove(id)}}
                }
            }
        } catch(e:TimeoutCancellationException) {close("请求超时，会话已断开；操作不会自动重发");throw e}
    }
    suspend fun request(opcode:Int,payload:ByteArray=byteArrayOf(),timeout:Long=requestTimeoutMillis):CcnCodec.Frame {
        val work=submit(opcode,payload,timeout)
        return try {work.await()} finally {if(!work.isCompleted)work.cancel()}
    }
    suspend fun ping(timeout:Long=requestTimeoutMillis):DevicePermission {
        val result=DevicePermission.decode(request(Op.PING,timeout=timeout).payload)
        if(!result.connected){close("设备已结束当前会话");throw ProtocolException("设备会话已结束")}
        mutableState.update {it.copy(permission=result)}
        if(state.value.negotiation?.capabilities?.and(8)==8) {
            val access=AndroidAccess.decode(request(Op.GET_ACCESS,timeout=timeout).payload)
            checkWire(access.android,"模块未将本会话识别为Android客户端")
            if(access.authorization==3 || access.authorization==4) {
                val message=if(access.authorization==4)"手环连接确认超时" else "手环已拒绝或撤销Android连接"
                close(message);throw ProtocolException(message)
            }
            mutableState.update {it.copy(access=access,phase=if(access.granted)SessionPhase.CONNECTED else SessionPhase.AWAITING_APPROVAL,
                message=if(access.granted)"已连接 · 权限由手环控制" else "请在手环确认本次Android连接")}
        }
        return result
    }
    suspend fun setDeveloper(enabled:Boolean):Long? {
        checkWire(state.value.negotiation?.capabilities?.and(1)==1,"设备未声明开发者模式能力")
        val frame=request(Op.SET_DEV,byteArrayOf(if(enabled)1 else 0))
        if(frame.status==14) {
            val r=WireReader(frame.payload);val token=r.u32();r.end();checkWire(token!=0L,"授权 token 无效")
            mutableState.update {it.copy(permission=it.permission?.copy(developer=false,authorization=1,token=token))}
            ping();return token
        }
        WireReader(frame.payload).end();ping();return null
    }
    suspend fun getDeveloper():Boolean {
        val r=WireReader(request(Op.GET_DEV).payload);val enabled=r.u8();r.end()
        checkWire(enabled in 0..1,"开发者模式状态无效");return enabled==1
    }
    fun close(message:String="已断开连接") {
        val replies=synchronized(lock) {
            if(closed)return
            closed=true
            pending.values.map {it.reply}.also {pending.clear()}
        }
        replies.forEach {it.completeExceptionally(ProtocolException(message))}
        mutableState.update {it.copy(phase=SessionPhase.DISCONNECTED,negotiation=null,permission=null,access=null,message=message)}
        try {transport.close()}catch(_:Exception){}finally {scope.cancel()}
    }
}
