package com.chaosconnect.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaosconnect.app.ChaosApplication
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ChaosApplication
    // Null until DataStore loads, so a cold launch never flashes the wrong theme.
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val devices = app.database.devices().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val edits = Channel<(AppSettings) -> AppSettings>(Channel.UNLIMITED)
    private val protocolMutable=MutableStateFlow(SessionState())
    val protocol:StateFlow<SessionState> = protocolMutable.asStateFlow()
    private val checkMutable=MutableStateFlow<Boolean?>(null)
    val protocolCheck:StateFlow<Boolean?> = checkMutable.asStateFlow()
    private val terminalController=TerminalController(viewModelScope)
    val terminal=terminalController.state
    fun startTerminal(){terminalController.start()}
    fun stopTerminal(){terminalController.stop()}
    fun sendTerminal(command:String){terminalController.sendCommand(command)}
    fun interruptTerminal(){terminalController.interrupt()}
    fun clearTerminal(){terminalController.clear()}
    fun terminalVisible(visible:Boolean){terminalController.visible=visible}
    private val speedMutable=MutableStateFlow(SpeedTestState())
    val speedTest=speedMutable.asStateFlow()
    private var speedJob:Job?=null
    @Volatile private var speedGeneration=0
    fun startSpeedTest() {
        if(speedJob?.isActive==true)return
        val link=session
        if(link==null || link.state.value.phase!=SessionPhase.CONNECTED){speedMutable.value=SpeedTestState(message="请先连接并在手环确认");return}
        val generation=++speedGeneration
        speedMutable.value=SpeedTestState(running=true,message="正在测速并校验数据…")
        speedJob=viewModelScope.launch(Dispatchers.Default) {
            try {
                ConnectionSpeedTest(link).run {progress->if(generation==speedGeneration)speedMutable.update{it.copy(measured=true,progress=progress)}}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==speedGeneration)speedMutable.update{it.copy(running=false,message=e.message ?: "测速失败")}}
        }
    }
    fun stopSpeedTest(){++speedGeneration;speedJob?.cancel();speedJob=null;speedMutable.update{it.copy(running=false,message=if(it.running)"测速已停止" else it.message)}}
    private fun resetSpeedTest(){stopSpeedTest();speedMutable.value=SpeedTestState()}
    private var session:ChaosSession?=null
    private var sessionObserver:Job?=null
    var files:ChaosFiles?=null
        private set
    private val browserMutable=MutableStateFlow(FileBrowserState())
    val browser:StateFlow<FileBrowserState> = browserMutable.asStateFlow()
    private var browserJob:Job?=null
    private fun resetBrowser() {browserJob?.cancel();browserMutable.value=FileBrowserState()}
    fun loadDirectory(path:String=browser.value.path,append:Boolean=false) {
        if(browser.value.loading)return
        if(protocol.value.phase!=SessionPhase.CONNECTED || protocol.value.access?.allows(MobilePermission.READ)==false)return
        val client=files ?: return
        val before=browser.value
        val offset=if(append && path==before.path)before.entries.size else 0
        browserMutable.value=before.copy(path=path,entries=if(offset>0)before.entries else emptyList(),
            total=if(offset>0)before.total else 0,loading=true,loaded=offset>0,message=null,selectedName=null,selectedStat=null)
        browserJob=viewModelScope.launch {
            try {
                val page=client.list(path,offset)
                browserMutable.update{it.copy(entries=(if(offset>0)before.entries else emptyList())+page.entries,total=page.total,loading=false,loaded=true)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){browserMutable.update{it.copy(loading=false,message=e.message ?: "读取目录失败")}}
        }
    }
    fun openFile(entry:RemoteEntry) {
        if(entry.directory){loadDirectory(browser.value.child(entry.name));return}
        if(browser.value.loading)return
        val client=files ?: return
        val path=browser.value.child(entry.name)
        browserMutable.update{it.copy(loading=true,message=null,selectedName=null,selectedStat=null)}
        browserJob=viewModelScope.launch {
            try {val stat=client.stat(path);browserMutable.update{it.copy(loading=false,selectedName=entry.name,selectedStat=stat)}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){browserMutable.update{it.copy(loading=false,message=e.message ?: "读取文件信息失败")}}
        }
    }
    private val bluetooth=ChaosBluetooth(application,viewModelScope) {transport,peer ->
        attachAuthenticatedTransport(transport,peer)
    }
    val bluetoothState=bluetooth.state
    fun scanBluetooth(){bluetooth.scan()}
    fun bluetoothPermissionDenied(){bluetooth.permissionDenied()}
    fun connectBluetooth(address:String){
        if(bluetooth.state.value.connecting)return
        resetSpeedTest();terminalController.bind(null);resetBrowser();sessionObserver?.cancel();session?.close();session=null;files=null
        protocolMutable.value=SessionState()
        bluetooth.connect(address)
    }
    fun requestDeveloper(){viewModelScope.launch{try{session?.setDeveloper(true)}catch(e:CancellationException){throw e}catch(e:Exception){protocolMutable.update{it.copy(message=e.message ?: "请求失败")}}}}
    init {
        viewModelScope.launch {
            // Serialize rapid changes against the latest stored value; no lost toggles.
            for (edit in edits) app.settings.update(edit(app.settings.settings.first()))
        }
    }
    fun update(edit: (AppSettings) -> AppSettings) { edits.trySend(edit) }
    /** A real secure Bluetooth or authenticated host carrier; no mock peer is installed. */
    fun attachAuthenticatedTransport(transport:ChaosTransport,peer:BluetoothPeer?=null) {
        resetSpeedTest();resetBrowser();sessionObserver?.cancel();session?.close();files=null
        val active=ChaosSession(transport,viewModelScope);session=active;terminalController.bind(active)
        var catalogSaved=false
        sessionObserver=viewModelScope.launch {active.state.collect {state -> if(session===active) {
            protocolMutable.value=state
            if(state.phase!=SessionPhase.CONNECTED && speedTest.value.running)stopSpeedTest()
            if(terminal.value.active && (state.phase!=SessionPhase.CONNECTED || state.access?.allows(MobilePermission.NSH)!=true))terminalController.revoke()
            if(state.phase==SessionPhase.DISCONNECTED || state.phase==SessionPhase.AWAITING_APPROVAL){files=null;resetBrowser()}
            if(state.phase==SessionPhase.CONNECTED) {
                if(files==null)files=ChaosFiles(active)
                if(peer!=null && !catalogSaved){catalogSaved=true;app.database.devices().upsert(com.chaosconnect.app.data.SavedDevice(peer.address,peer.name,System.currentTimeMillis()))}
            }
        }}}
        viewModelScope.launch {
            try {active.connect()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){if(session===active)files=null}
        }
    }
    fun checkProtocol() {checkMutable.value=CcnCodec.selfCheck()}
    fun disconnectProtocol() {resetSpeedTest();terminalController.bind(null);resetBrowser();bluetooth.cancelConnection();session?.close();files=null}
    override fun onCleared() {stopSpeedTest();terminalController.bind(null);bluetooth.close();session?.close();super.onCleared()}
}
