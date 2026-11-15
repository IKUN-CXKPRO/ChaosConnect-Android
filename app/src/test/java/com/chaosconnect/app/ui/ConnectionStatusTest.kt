package com.chaosconnect.app.ui

import com.chaosconnect.app.protocol.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionStatusTest {
    @Test fun homeShowsSelectedBandThenLastSavedBandWithoutInventingADevice() {
        val records=listOf(com.chaosconnect.app.data.SavedDevice("other","Headphones",1),
            com.chaosconnect.app.data.SavedDevice("saved","Xiaomi Smart Band 9",0))
        assertEquals("未选择设备",homeDeviceName(BluetoothState(),emptyList()))
        assertEquals("Xiaomi Smart Band 9",homeDeviceName(BluetoothState(),records))
        assertEquals("Xiaomi Smart Band 10 Pro",homeDeviceName(BluetoothState(selectedPeer=BluetoothPeer("new","Xiaomi Smart Band 10 Pro",false)),records))
    }
    private val oldCarrier=BluetoothState(message="蓝牙链路已建立，请在手环确认ChaosConnect连接")
    @Test fun liveConsentAndDeveloperConsentAreDifferentStates() {
        val waiting=SessionState(phase=SessionPhase.AWAITING_APPROVAL,attempted=true,message="请在手环确认本次Android连接")
        assertEquals("等待手环确认连接",pairingStatus(oldCarrier,waiting).title)
        val approved=waiting.copy(phase=SessionPhase.CONNECTED,message="已连接 · 权限由手环控制",
            access=AndroidAccess(true,2,2,31,17,true))
        assertEquals(ConnectionStatus("设备已连接","已连接 · 权限由手环控制"),pairingStatus(oldCarrier,approved))
        val developerWaiting=approved.copy(permission=DevicePermission(true,false,2,1,33))
        assertEquals(pairingStatus(oldCarrier,approved),pairingStatus(oldCarrier,developerWaiting))
    }
    @Test fun initialDiscoveryNewAttemptAndClosedSessionUseTheRightOwner() {
        val scan=BluetoothState(scanning=true,message="正在搜索普通蓝牙设备")
        assertEquals(scan.message,pairingStatus(scan,SessionState()).message)
        val closed=SessionState(attempted=true,message="手环已拒绝或撤销Android连接")
        assertEquals(ConnectionStatus("会话已断开",closed.message),pairingStatus(oldCarrier,closed))
        val retry=oldCarrier.copy(connecting=true,message="正在连接新设备")
        assertEquals(ConnectionStatus("正在连接蓝牙",retry.message),pairingStatus(retry,closed))
        assertEquals("正在协商",pairingStatus(oldCarrier,SessionState(phase=SessionPhase.HANDSHAKING,attempted=true)).title)
    }
}
