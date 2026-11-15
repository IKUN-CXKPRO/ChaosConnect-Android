package com.chaosconnect.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaosconnect.app.ChaosApplication
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var session:ChaosSession?=null
    private var sessionObserver:Job?=null
    var files:ChaosFiles?=null
        private set
    private val bluetooth=ChaosBluetooth(application,viewModelScope) {transport,peer ->
        attachAuthenticatedTransport(transport,peer)
    }
    val bluetoothState=bluetooth.state
    fun scanBluetooth(){bluetooth.scan()}
    fun bluetoothPermissionDenied(){bluetooth.permissionDenied()}
    fun connectBluetooth(address:String){session?.close();files=null;bluetooth.connect(address)}
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
        sessionObserver?.cancel();session?.close();files=null
        val active=ChaosSession(transport,viewModelScope);session=active
        var catalogSaved=false
        sessionObserver=viewModelScope.launch {active.state.collect {state -> if(session===active) {
            protocolMutable.value=state
            if(state.phase==SessionPhase.DISCONNECTED || state.phase==SessionPhase.AWAITING_APPROVAL)files=null
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
    fun disconnectProtocol() {bluetooth.cancelConnection();session?.close();files=null}
    override fun onCleared() {bluetooth.close();session?.close();super.onCleared()}
}
