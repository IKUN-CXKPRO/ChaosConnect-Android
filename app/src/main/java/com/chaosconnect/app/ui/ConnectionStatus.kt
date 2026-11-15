package com.chaosconnect.app.ui

import com.chaosconnect.app.protocol.BluetoothState
import com.chaosconnect.app.protocol.SessionPhase
import com.chaosconnect.app.protocol.SessionState
import com.chaosconnect.app.protocol.BandDiscovery
import com.chaosconnect.app.data.SavedDevice

/** Both design systems use the live CCN1 state after a session starts.
 * Carrier messages describe discovery/socket setup only; they are not consent. */
data class ConnectionStatus(val title:String,val message:String)

val SessionState.connectionTitle:String get()=when(phase) {
    SessionPhase.CONNECTED->"设备已连接"
    SessionPhase.HANDSHAKING->"正在协商"
    SessionPhase.AWAITING_APPROVAL->"等待手环确认连接"
    SessionPhase.DISCONNECTED->if(attempted)"会话已断开" else "设备未连接"
}

fun pairingStatus(bluetooth:BluetoothState,session:SessionState):ConnectionStatus {
    if(bluetooth.connecting || (!session.attempted && session.phase==SessionPhase.DISCONNECTED))
        return ConnectionStatus(if(bluetooth.connecting)"正在连接蓝牙" else "设备未连接",bluetooth.message)
    return ConnectionStatus(session.connectionTitle,session.message)
}

fun homeDeviceName(bluetooth:BluetoothState,devices:List<SavedDevice>):String =
    bluetooth.selectedPeer?.name?.let(BandDiscovery::name)
        ?: devices.firstOrNull{BandDiscovery.name(it.name)!=null}?.name
        ?: "未选择设备"
