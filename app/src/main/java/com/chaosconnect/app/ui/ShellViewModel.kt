package com.chaosconnect.app.ui

import android.app.Application
import android.net.Uri
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
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
    private val packageMutable=MutableStateFlow(PackageInstallState())
    val packageInstall=packageMutable.asStateFlow()
    private var delivery:DeliveryPackage?=null
    private var packageJob:Job?=null
    @Volatile private var packageEpoch=0
    fun selectPackage(uri:Uri){
        if(packageMutable.value.busy)return
        val epoch=++packageEpoch;packageMutable.value=PackageInstallState(busy=true,message="正在读取并检查投递包…")
        packageJob=viewModelScope.launch {
            var cached:File?=null
            try{
                val pack=withContext(Dispatchers.IO){
                    val dir=File(app.cacheDir,"delivery").apply{mkdirs()};val target=File.createTempFile("delivery-",".bin",dir);cached=target
                    app.contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{output->
                        val buffer=ByteArray(65536);var size=0L
                        while(true){ensureActive();val n=input.read(buffer);if(n<0)break;if(n==0)continue;size+=n;checkWire(size<=DeliveryPackageReader.MAX_CONTAINER,"投递包超过48MiB");output.write(buffer,0,n)}
                    }} ?: throw ProtocolException("无法打开选择的文件")
                    DeliveryPackageReader.parse(target)
                }
                delivery?.source?.delete();delivery=pack
                packageMutable.value=PackageInstallState(name=pack.name,kind=if(pack.kind==1)"字体包" else "图标包",files=pack.entries.size,total=pack.total,message="投递包检查通过")
            }catch(e:CancellationException){cached?.delete();throw e}
            catch(e:Exception){cached?.delete();if(epoch==packageEpoch)packageMutable.value=PackageInstallState(message=e.message ?: "读取投递包失败")}
        }
    }
    fun askPackageInstall(){if(!packageMutable.value.busy&&delivery!=null)packageMutable.update{it.copy(confirming=true)}}
    fun dismissPackageInstall(){packageMutable.update{it.copy(confirming=false)}}
    fun stopPackageInstall(){++packageEpoch;packageJob?.cancel();packageJob=null;packageMutable.update{it.copy(busy=false,confirming=false,message=if(it.busy)"已取消安装，未提交的资源会被清理" else it.message)}}
    fun installPackage(){
        if(!packageMutable.value.confirming||packageMutable.value.busy)return
        val pack=delivery ?: return;val active=session ?: return
        if(active.state.value.phase!=SessionPhase.CONNECTED){packageMutable.update{it.copy(confirming=false,message="请先连接并在手环确认")};return}
        stopSpeedTest();val epoch=++packageEpoch
        packageMutable.update{it.copy(confirming=false,busy=true,done=0,message="正在请求手环安装权限…")}
        packageJob=viewModelScope.launch(Dispatchers.IO){
            try{ChaosPackageInstaller(active).install(pack){progress->if(epoch==packageEpoch)packageMutable.update{it.copy(done=progress.done,message=when(progress.state){1->"请在手环确认安装投递包：${pack.name}";6->"安装完成，可在Chaos中选择${pack.name}";else->"正在安装投递包…"})}}
                if(epoch==packageEpoch)packageMutable.update{it.copy(busy=false)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(epoch==packageEpoch)packageMutable.update{it.copy(busy=false,message=e.message ?: "安装失败，请核对手环清单")}}
        }
    }
    private val bandController=BandSettingsController(viewModelScope)
    val bandSettings=bandController.state
    fun loadBandSettings(){stopSpeedTest();bandController.load()}
    fun writeBandSetting(setting:BandSetting,value:String){stopSpeedTest();bandController.write(setting,value)}
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
        stopPackageInstall();resetSpeedTest();terminalController.bind(null);bandController.bind(null);resetBrowser();sessionObserver?.cancel();session?.close();session=null;files=null
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
        stopPackageInstall();resetSpeedTest();resetBrowser();sessionObserver?.cancel();session?.close();files=null
        val active=ChaosSession(transport,viewModelScope);session=active;terminalController.bind(active);bandController.bind(active)
        var catalogSaved=false
        sessionObserver=viewModelScope.launch {active.state.collect {state -> if(session===active) {
            protocolMutable.value=state
            if(state.phase!=SessionPhase.CONNECTED||state.access?.allows(MobilePermission.CONTROL)!=true){if(bandSettings.value.items.isNotEmpty()||bandSettings.value.busy)bandController.revoke()}
            if(state.phase!=SessionPhase.CONNECTED && packageInstall.value.busy)stopPackageInstall()
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
    fun disconnectProtocol() {stopPackageInstall();resetSpeedTest();terminalController.bind(null);bandController.bind(null);resetBrowser();bluetooth.cancelConnection();session?.close();files=null}
    override fun onCleared() {stopPackageInstall();delivery?.source?.delete();stopSpeedTest();terminalController.bind(null);bandController.bind(null);bluetooth.close();session?.close();super.onCleared()}
}
