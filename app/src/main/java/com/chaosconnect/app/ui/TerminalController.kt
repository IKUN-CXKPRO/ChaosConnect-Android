package com.chaosconnect.app.ui

import com.chaosconnect.app.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TerminalController(private val scope:CoroutineScope) {
    private val mutable=MutableStateFlow(TerminalState())
    val state:StateFlow<TerminalState> = mutable.asStateFlow()
    private val transcript=TerminalTranscript()
    private var session:ChaosSession?=null
    private var client:ChaosTerminal?=null
    private var job:Job?=null
    private val input=Mutex()
    var visible=false
    fun bind(value:ChaosSession?) {
        if(session===value)return
        stop();session=value;transcript.clear();mutable.value=TerminalState()
    }
    fun start() {
        if(job?.isActive==true)return
        val link=session ?: return
        if(link.state.value.phase!=SessionPhase.CONNECTED || link.state.value.access?.allows(MobilePermission.NSH)!=true) {
            mutable.update{it.copy(message="请在手环 ChaosConnect → 权限设置 → Android端权限中开启NSH终端")};return
        }
        val previous=job
        val terminal=ChaosTerminal(link);client=terminal
        mutable.update{it.copy(busy=true,message="正在连接手环NSH")}
        job=scope.launch {
            try {
                previous?.join()
                terminal.open()
                mutable.update{it.copy(active=true,busy=false,message="手环NSH已连接")}
                while(isActive) {
                    if(link.state.value.access?.allows(MobilePermission.NSH)!=true)throw ProtocolException("手环已撤销NSH终端权限")
                    val packet=terminal.read()
                    if(packet.bytes.isNotEmpty())mutable.update{it.copy(output=transcript.append(packet.bytes))}
                    if(packet.state in listOf(0,3,4,5)) {
                        mutable.update{it.copy(message=if(packet.error!=0)"NSH错误：${packet.error}" else "NSH会话已结束")};break
                    }
                    delay(if(visible)150 else 1000)
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(client===terminal)mutable.update{it.copy(message=e.message ?: "NSH连接失败")}}
            finally {
                withContext(NonCancellable) {
                    if(link.state.value.phase==SessionPhase.CONNECTED && link.state.value.access?.allows(MobilePermission.NSH)==true)
                        runCatching{withTimeout(2000){terminal.close()}}
                }
                if(client===terminal){client=null;mutable.update{it.copy(active=false,busy=false)}}
            }
        }
    }
    fun sendCommand(command:String){if(command.isNotBlank())send((command+"\n").toByteArray(Charsets.UTF_8))}
    fun interrupt(){send(byteArrayOf(3))}
    private fun send(bytes:ByteArray) {
        val terminal=client ?: return
        scope.launch {
            try {input.withLock{if(client===terminal && state.value.active)terminal.send(bytes)}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(client===terminal)mutable.update{it.copy(message=e.message ?: "发送失败")}}
        }
    }
    fun stop(){job?.cancel();client=null;mutable.update{it.copy(active=false,busy=false,message="NSH终端已停止")}}
    fun revoke(){stop();mutable.update{it.copy(message="手环已撤销NSH终端权限")}}
    fun clear(){transcript.clear();mutable.update{it.copy(output="")}}
}
