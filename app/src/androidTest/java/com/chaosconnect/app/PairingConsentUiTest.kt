package com.chaosconnect.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.protocol.*
import com.chaosconnect.app.ui.ShellViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented presentation/heartbeat test; the peer is test-only, no radio. */
@RunWith(AndroidJUnit4::class)
class PairingConsentUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private class Link:ChaosTransport {
        override val maximumPacketBytes=4096
        override val incoming=Channel<ByteArray>(32)
        @Volatile var authorization=1
        @Volatile var developerWaiting=false
        override suspend fun send(packet:ByteArray) {
            val p=WearEnvelope.unwrap(packet)!!;var status=0
            val bytes=when(p.frame.opcode) {
                Op.HELLO->WireWriter().u8(1).u16(2048).u8(15).u8(0).u16(255).u32(8).build()
                Op.PING->WireWriter().u8(1).u8(0).u8(2).u8(if(developerWaiting)1 else 0).u32(if(developerWaiting)33 else 0).build()
                Op.GET_ACCESS->WireWriter().u8(1).u8(1).u8(2).u8(authorization).u32(31).u32(17)
                    .u8(if(authorization==2)1 else 0).u8(0).u8(0).u8(0).build()
                Op.SET_DEV->{developerWaiting=true;status=14;WireWriter().u32(33).build()}
                Op.LIST -> {
                    val r=WireReader(p.frame.payload);val path=r.text(r.u16());val offset=r.u32().toInt();val limit=r.u8()
                    val entries=if(path=="/data")listOf(RemoteEntry("Documents",true,0,0))+(1..16).map{RemoteEntry("file$it.txt",false,it.toLong(),100)} else emptyList()
                    val page=entries.drop(offset).take(limit)
                    WireWriter().u16(entries.size).u16(offset).u16(page.size).apply {
                        page.forEach {entry->val name=entry.name.toByteArray();u8(name.size).bytes(name).u8(if(entry.directory)1 else 0).u32(entry.size).u32(entry.modifiedAt)}
                    }.build()
                }
                Op.STAT -> WireWriter().u8(0).u32(42).u32(100).build()
                else->byteArrayOf()
            }
            incoming.send(WearEnvelope.wrap(CcnCodec.encode(CcnCodec.Frame(1,p.frame.opcode,status,p.frame.requestId,bytes)),p.packetId))
        }
        override fun close(){incoming.close()}
    }
    @Test fun fileBrowserLoadsAndPagesAfterConsentAndClearsOnDisconnect() {
        val app=ui.activity.application as ChaosApplication
        val original=runBlocking {app.settings.settings.first()}
        lateinit var vm:ShellViewModel
        ui.runOnUiThread {vm=ViewModelProvider(ui.activity)[ShellViewModel::class.java]}
        try {
            for(miuix in listOf(true,false)) {
                runBlocking {app.settings.update(AppSettings(useMiuix=miuix,dynamicColor=false,haptics=false))}
                val link=Link().apply{authorization=2}
                ui.runOnUiThread {vm.attachAuthenticatedTransport(link)}
                ui.waitUntil(5_000){vm.protocol.value.phase==SessionPhase.CONNECTED}
                ui.onNodeWithTag("tab_0").performClick()
                ui.onNodeWithTag("home_files").performScrollTo().performClick()
                ui.waitUntil(5_000){vm.browser.value.loaded}
                assertEquals(16,vm.browser.value.entries.size)
                ui.onNodeWithTag("files_more").performScrollTo().performClick()
                ui.waitUntil(5_000){vm.browser.value.entries.size==17}
                ui.onNodeWithTag("file_Documents").performScrollTo().performClick()
                println("AFTER_FOLDER_CLICK: style=$miuix state=${vm.browser.value}")
                try {ui.waitUntil(5_000){vm.browser.value.path=="/data/Documents" && vm.browser.value.loaded}}
                catch(e:Exception){throw AssertionError("folder navigation: miuix=$miuix browser=${vm.browser.value} phase=${vm.protocol.value}",e)}
                ui.onNodeWithText("此文件夹为空").assertIsDisplayed()
                ui.onNodeWithTag("files_parent").performScrollTo().performClick()
                ui.waitUntil(5_000){vm.browser.value.path=="/data" && vm.browser.value.loaded}
                ui.onNodeWithTag("file_file1.txt").performScrollTo().performClick()
                ui.waitUntil(5_000){vm.browser.value.selectedStat!=null}
                assertEquals(42L,vm.browser.value.selectedStat!!.size)
                ui.runOnUiThread {vm.disconnectProtocol()}
                ui.waitForIdle()
                ui.onNodeWithTag("files_connect").assertIsDisplayed()
                assertTrue(vm.browser.value.entries.isEmpty())
                ui.runOnUiThread {ui.activity.onBackPressedDispatcher.onBackPressed()}
                ui.waitForIdle();ui.onNodeWithTag("tab_0").assertIsSelected()
            }
        }finally {
            ui.runOnUiThread {vm.disconnectProtocol()}
            runBlocking {app.settings.update(original)}
        }
    }
    @Test fun approvalDeveloperRequestAndRevocationUpdateBothDesignSystems() {
        val app=ui.activity.application as ChaosApplication
        val original=runBlocking {app.settings.settings.first()}
        lateinit var vm:ShellViewModel
        ui.runOnUiThread {vm=ViewModelProvider(ui.activity)[ShellViewModel::class.java]}
        try {
            for(miuix in listOf(true,false)) {
                runBlocking {app.settings.update(AppSettings(useMiuix=miuix,dynamicColor=false,haptics=false))}
                ui.waitForIdle()
                ui.onNodeWithTag("tab_1").performClick()
                val link=Link()
                ui.runOnUiThread {vm.attachAuthenticatedTransport(link)}
                ui.waitUntil(5_000){vm.protocol.value.phase==SessionPhase.AWAITING_APPROVAL}
                ui.onNodeWithTag("pair_connection_title").performScrollTo().assertTextEquals("等待手环确认连接")
                link.authorization=2
                ui.waitUntil(5_000){vm.protocol.value.phase==SessionPhase.CONNECTED}
                ui.onNodeWithTag("pair_connection_title").assertTextEquals("设备已连接")
                ui.onNodeWithTag("pair_connection_status").performScrollTo().assertTextEquals("已连接 · 权限由手环控制")
                // A second approval is developer permission, not session consent.
                ui.runOnUiThread {vm.requestDeveloper()}
                ui.waitUntil(5_000){vm.protocol.value.permission?.waiting==true}
                assertEquals(SessionPhase.CONNECTED,vm.protocol.value.phase)
                ui.onNodeWithTag("pair_connection_title").performScrollTo().assertTextEquals("设备已连接")
                link.authorization=3
                ui.waitUntil(5_000){vm.protocol.value.phase==SessionPhase.DISCONNECTED}
                ui.onNodeWithTag("pair_connection_status").performScrollTo().assertTextEquals("手环已拒绝或撤销Android连接")
                ui.onNodeWithTag("pair_connection_title").performScrollTo().assertTextEquals("会话已断开")
                ui.runOnUiThread {ui.activity.onBackPressedDispatcher.onBackPressed()}
                ui.waitForIdle()
            }
        } finally {
            ui.runOnUiThread {vm.disconnectProtocol()}
            runBlocking {app.settings.update(original)}
        }
    }
}
