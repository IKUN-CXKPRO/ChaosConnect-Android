package com.chaosconnect.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaosconnect.app.data.*
import com.chaosconnect.app.protocol.*
import com.chaosconnect.app.ui.*
import com.chaosconnect.app.ui.page.md3.*
import com.chaosconnect.app.ui.page.miuix.*
import com.chaosconnect.app.ui.theme.AppTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun terminalPermissionsAndCommandActionsWorkInBothDesignSystems() {
        var miuix by mutableStateOf(true);var allowed by mutableStateOf(false)
        var sent="";var interrupts=0;var cleared=0
        ui.setContent {
            val settings=AppSettings(useMiuix=miuix,darkMode=DarkMode.DARK,dynamicColor=false)
            AppTheme(settings) {
                val protocol=SessionState(phase=SessionPhase.CONNECTED,access=AndroidAccess(true,0,2,if(allowed)33 else 1,0,true))
                val terminal=TerminalState(active=allowed,message="手环NSH已连接",output="nsh> pwd\n/data\nnsh> ")
                if(miuix)MiuixTerminal(terminal,protocol,{},{},{},{sent=it},{interrupts++},{cleared++})
                else Md3Terminal(terminal,protocol,{},{},{},{sent=it},{interrupts++},{cleared++})
            }
        }
        for(style in listOf(true,false)) {
            ui.runOnIdle{miuix=style;allowed=false}
            ui.onNodeWithTag("terminal_status",useUnmergedTree=true).assertTextEquals("NSH终端权限未开启")
            ui.onNodeWithTag("terminal_send").performScrollTo().assertIsNotEnabled()
            ui.runOnIdle{allowed=true}
            ui.onNodeWithTag("terminal_input").performScrollTo().performTextInput("ls /data")
            ui.onNodeWithTag("terminal_send").performScrollTo().performClick()
            ui.runOnIdle{assertEquals("ls /data",sent)}
            ui.onNodeWithTag("terminal_interrupt").performScrollTo().performClick()
            ui.onNodeWithTag("terminal_clear").performClick()
            ui.onNodeWithTag("terminal_output",useUnmergedTree=true).assertTextContains("/data",substring=true)
            ui.onRoot().captureToImage().asAndroidBitmap().let {bitmap ->
                File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,if(style)"miuix-terminal.png" else "md3-terminal.png")
                    .outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        ui.runOnIdle{assertEquals(2,interrupts);assertEquals(2,cleared)}
    }
    @Test fun connectionHasNoExtraMenusAndProfileOwnsThem() {
        var miuix by mutableStateOf(true);var mine by mutableStateOf(false)
        ui.setContent {AppTheme(AppSettings(useMiuix=miuix,dynamicColor=false)) {
            if(mine){if(miuix)MiuixProfile({},{},{}) else Md3Profile({},{},{})}
            else if(miuix)MiuixConnection(BluetoothState(),{},{},SessionState(),{},{},{})
            else Md3Connection(BluetoothState(),{},{},SessionState(),{},{},{})
        }}
        for(style in listOf(true,false)) {
            ui.runOnIdle{miuix=style;mine=false}
            ui.onNodeWithTag("pair_protocol").assertDoesNotExist()
            ui.onNodeWithTag("connection_theme").assertDoesNotExist()
            ui.runOnIdle{mine=true}
            ui.onNodeWithTag("profile_settings").performScrollTo().assertExists()
            ui.onNodeWithTag("pair_protocol").assertExists()
            ui.onNodeWithTag("open_about").assertExists()
        }
    }
}
