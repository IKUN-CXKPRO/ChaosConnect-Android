package com.chaosconnect.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaosconnect.app.data.*
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
class SpeedUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun bothStylesShowMeasuredBytesAndDispatchStartAndStop() {
        var miuix by mutableStateOf(true);var running by mutableStateOf(false)
        var starts=0;var stops=0
        ui.setContent {AppTheme(AppSettings(useMiuix=miuix,dynamicColor=false)) {
            val state=SpeedTestState(running=running,measured=true,progress=SpeedProgress(204800,204800,1000),message="测速完成")
            val protocol=SessionState(phase=SessionPhase.CONNECTED)
            if(miuix)MiuixSpeedTest(state,protocol,{starts++},{stops++},{},{})
            else Md3SpeedTest(state,protocol,{starts++},{stops++},{},{})
        }}
        for(style in listOf(true,false)) {
            ui.runOnIdle{miuix=style;running=false}
            ui.onNodeWithTag("speed_value",useUnmergedTree=true).assertTextEquals("200.0 KiB/s")
            ui.onNodeWithTag("speed_start_stop").performScrollTo().performClick()
            ui.runOnIdle{running=true}
            ui.onNodeWithTag("speed_start_stop").performClick()
        }
        ui.runOnIdle{assertEquals(2,starts);assertEquals(2,stops)}
    }
}
