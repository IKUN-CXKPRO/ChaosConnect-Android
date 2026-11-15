package com.chaosconnect.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.data.DarkMode
import com.chaosconnect.app.protocol.*
import com.chaosconnect.app.ui.*
import com.chaosconnect.app.ui.page.md3.*
import com.chaosconnect.app.ui.page.miuix.*
import com.chaosconnect.app.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainPagesUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun bothConnectionScreensFilterNamesAndDispatchTheSelectedBand() {
        var miuix by mutableStateOf(true)
        var selected:String?=null
        val peers=listOf(BluetoothPeer("unknown","",false),BluetoothPeer("speaker","Speaker",true),
            BluetoothPeer("watch","Xiaomi Watch S4",true),BluetoothPeer("band","Xiaomi Smart Band 10 Pro",false))
        ui.setContent {
            AppTheme(AppSettings(useMiuix=miuix,darkMode=DarkMode.LIGHT,dynamicColor=false)) {
                if(miuix)MiuixConnection(BluetoothState(peers=peers),{},{selected=it},SessionState(),{},{},{})
                else Md3Connection(BluetoothState(peers=peers),{},{selected=it},SessionState(),{},{},{})
            }
        }
        for(style in listOf(true,false)) {
            ui.runOnIdle {miuix=style};ui.waitForIdle()
            ui.onNodeWithText("Speaker").assertDoesNotExist()
            ui.onNodeWithText("Xiaomi Watch S4").assertDoesNotExist()
            ui.onNodeWithTag("connect_unknown").assertDoesNotExist()
            ui.onNodeWithText("Xiaomi Smart Band 10 Pro").assertExists()
            ui.onNodeWithTag("connect_band").performScrollTo().performClick()
            ui.runOnIdle {assertEquals("band",selected);selected=null}
        }
    }
    @Test fun homeHasLiveDeviceStatusAndWorkingFeatureCards() {
        var miuix by mutableStateOf(true)
        var phase by mutableStateOf(SessionPhase.AWAITING_APPROVAL)
        var files=0;var terminals=0
        ui.setContent {
            AppTheme(AppSettings(useMiuix=miuix,darkMode=DarkMode.LIGHT,dynamicColor=false)) {
                val state=pairingStatus(BluetoothState(),SessionState(phase=phase,attempted=true))
                if(miuix)MiuixHome("Xiaomi Smart Band 10 Pro",state,{},{},{files++},{terminals++})
                else Md3Home("Xiaomi Smart Band 10 Pro",state,{},{},{files++},{terminals++})
            }
        }
        for(style in listOf(true,false)) {
            ui.runOnIdle {miuix=style;phase=SessionPhase.AWAITING_APPROVAL}
            ui.onNodeWithTag("home_device_name",useUnmergedTree=true).assertTextEquals("Xiaomi Smart Band 10 Pro")
            ui.onNodeWithTag("home_connection_status",useUnmergedTree=true).assertTextEquals("等待手环确认连接")
            ui.onNodeWithTag("add_device").assertDoesNotExist()
            ui.onNodeWithTag("home_files").performScrollTo().performClick()
            ui.onNodeWithTag("home_terminal").performScrollTo().performClick()
            ui.runOnIdle {phase=SessionPhase.CONNECTED}
            ui.onNodeWithTag("home_connection_status",useUnmergedTree=true).assertTextEquals("设备已连接")
        }
        ui.runOnIdle{assertEquals(2,files);assertEquals(2,terminals)}
    }
    @Test fun filePageShowsOnlyRealEntriesAndCallsDirectoryAndPaginationActions() {
        var miuix by mutableStateOf(true)
        var picked:String?=null;var more=0
        val entry=RemoteEntry("Documents",true,0,0)
        ui.setContent {
            AppTheme(AppSettings(useMiuix=miuix,darkMode=DarkMode.LIGHT,dynamicColor=false)) {
                val browser=FileBrowserState(entries=listOf(entry),loaded=true,total=17)
                val protocol=SessionState(phase=SessionPhase.CONNECTED)
                if(miuix)MiuixFiles(browser,protocol,{},{},{},{more++},{picked=it.name})
                else Md3Files(browser,protocol,{},{},{},{more++},{picked=it.name})
            }
        }
        for(style in listOf(true,false)) {
            ui.runOnIdle {miuix=style}
            ui.onNodeWithTag("file_path").assertTextEquals("/data")
            ui.onNodeWithTag("file_Documents").performScrollTo().performClick()
            ui.runOnIdle {assertEquals("Documents",picked);picked=null}
            ui.onNodeWithTag("files_more").performScrollTo().performClick()
        }
        ui.runOnIdle {assertEquals(2,more)}
    }
}
