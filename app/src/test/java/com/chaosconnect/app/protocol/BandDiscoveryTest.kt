package com.chaosconnect.app.protocol

import org.junit.Assert.*
import org.junit.Test

class BandDiscoveryTest {
    @Test fun rejectsUnnamedMacFallbackAndOtherBrands() {
        listOf(null,"","   ","AA:BB:CC:DD:EE:FF","Redmi Watch 6","Xiaomi Watch S4","xiaomi Smart Band 10"," Xiaomi Smart Band 10").forEach {
            assertNull("Must hide: $it",BandDiscovery.name(it))
        }
        assertEquals("Xiaomi Smart Band 10 Pro",BandDiscovery.name("Xiaomi Smart Band 10 Pro"))
        assertEquals("Xiaomi Smart Band 9",BandDiscovery.name("Xiaomi Smart Band 9"))
    }
    @Test fun acceptsChaosModuleWithoutBroadeningToUnrelatedNames() {
        assertEquals("Chaos-Module",BandDiscovery.name("Chaos-Module"))
        listOf("Chaos-Module Headphones","Chaos-Module-fake","chaos-module"," Chaos-Module").forEach {
            assertNull(BandDiscovery.name(it))
        }
        val chaos=BluetoothPeer("05","Chaos-Module",false)
        val band=BluetoothPeer("01","Xiaomi Smart Band 10 Pro",true)
        assertEquals(listOf(band,chaos),BandDiscovery.visible(listOf(chaos,band,chaos)))
    }
    @Test fun deduplicatesAndFiltersPairedAndDiscoveredDevicesTogether() {
        val band=BluetoothPeer("01","Xiaomi Smart Band 10 Pro",true)
        val result=BandDiscovery.visible(listOf(
            BluetoothPeer("02","",false),BluetoothPeer("03","Headphones",true),band,
            band.copy(bonded=false),BluetoothPeer("04","Xiaomi Smart Band 9",false)))
        assertEquals(listOf(band,BluetoothPeer("04","Xiaomi Smart Band 9",false)),result)
    }
}
