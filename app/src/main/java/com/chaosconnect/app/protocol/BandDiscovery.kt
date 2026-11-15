package com.chaosconnect.app.protocol

/** Shared by discovery, bonded devices, connection requests and the displayed list. */
object BandDiscovery {
    const val PREFIX = "Xiaomi Smart Band"
    const val CHAOS_NAME = "Chaos-Module"
    const val HINT = "仅显示 Xiaomi Smart Band 系列及 Chaos-Module 设备。"
    fun name(value: String?): String? = value?.takeIf { it.startsWith(PREFIX) || it == CHAOS_NAME }
    fun visible(peers: List<BluetoothPeer>): List<BluetoothPeer> = peers
        .filter { name(it.name) != null }
        .distinctBy { it.address }
        .sortedWith(compareByDescending<BluetoothPeer> { it.bonded }.thenBy { it.name }.thenBy { it.address })
}
