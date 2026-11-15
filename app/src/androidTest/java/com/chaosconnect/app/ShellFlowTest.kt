package com.chaosconnect.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaosconnect.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Bitmap
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShellFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as ChaosApplication
    private fun stored() = runBlocking { app.settings.settings.first() }
    private fun tap(tag: String) {
        val node=ui.onNodeWithTag(tag)
        if(!tag.startsWith("tab_")) node.performScrollTo()
        node.performClick()
        ui.waitForIdle()
    }
    private fun waitSetting(predicate: (AppSettings) -> Boolean) = ui.waitUntil(5_000) { predicate(stored()) }
    private fun back() {
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
    }
    private fun theme() { tap("tab_2"); tap("open_settings"); tap("open_theme") }
    private fun screenshot(name:String) {
        ui.onRoot().captureToImage().asAndroidBitmap().let {bitmap ->
            File(ui.activity.filesDir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }

    @Before fun reset() {
        runBlocking { app.settings.update(AppSettings(dynamicColor=false,haptics=false)) }
        ui.waitForIdle()
    }

    @Test fun shellRoutesAreUsableInBothDesignSystems() {
        for(miuix in listOf(true,false)) {
            runBlocking {app.settings.update(stored().copy(useMiuix=miuix,darkMode=DarkMode.LIGHT))}
            ui.waitForIdle()
            tap("tab_0")
            screenshot(if(miuix) "miuix-home" else "md3-home")
            tap("add_device")
            ui.onNodeWithTag("pair_protocol").assertIsDisplayed()
            tap("pair_protocol")
            tap("check_protocol")
            ui.onNodeWithText("帧与承载向量一致").assertIsDisplayed()
            screenshot(if(miuix) "miuix-protocol" else "md3-protocol")
            back()
            back()
            tap("tab_1")
            ui.onNodeWithText("等待第一台设备").assertIsDisplayed()
            tap("tab_2")
            tap("open_about")
            ui.onNodeWithText("0.2.0 · 预览版").assertIsDisplayed()
            back()
        }
        assertTrue(runBlocking {app.database.devices().observeAll().first()}.isEmpty())
    }

    @Test fun styleAndAppearanceSurviveActivityRecreation() {
        theme()
        tap("style_md3")
        waitSetting {!it.useMiuix}
        // Switching the entire design tree must retain the theme page.
        ui.onNodeWithTag("style_md3").assertIsSelected()
        tap("dark_DARK")
        tap("pure_black")
        tap("seed_1")
        waitSetting {it.darkMode==DarkMode.DARK && it.pureBlack && it.seedColor==0xFF6750A4}
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("style_md3").assertIsSelected()
        ui.onNodeWithTag("dark_DARK").assertIsSelected()
        ui.onNodeWithTag("pure_black").performScrollTo().assertIsOn()
        tap("style_miuix")
        waitSetting {it.useMiuix}
        ui.onNodeWithTag("style_miuix").assertIsSelected()
        assertTrue(stored().pureBlack)
    }

    @Test fun heldDockContinuouslyChangesPagesInBothStyles() {
        for(miuix in listOf(true,false)) {
            runBlocking { app.settings.update(AppSettings(useMiuix=miuix,dynamicColor=false,haptics=false)) }
            ui.waitForIdle()
            val dock=ui.onNodeWithTag("dock")
            dock.performTouchInput {down(Offset(width/6f,height/2f));moveTo(Offset(width*5/6f,height/2f),500)}
            ui.waitForIdle()
            ui.onNodeWithTag("tab_2").assertIsSelected()
            ui.onNodeWithTag("open_settings").assertIsDisplayed()
            dock.performTouchInput {moveTo(Offset(width/2f,height/2f),500)}
            ui.waitForIdle()
            ui.onNodeWithTag("tab_1").assertIsSelected()
            dock.performTouchInput {up()}
            ui.waitForIdle()
        }
    }

    @Test fun disabledInstantDockCommitsOnlyOnReleaseAndPersists() {
        theme()
        tap("instant_dock")
        waitSetting {!it.instantDock}
        back();back()
        val dock=ui.onNodeWithTag("dock")
        dock.performTouchInput {down(Offset(width*5/6f,height/2f));moveTo(Offset(width/6f,height/2f),500)}
        ui.waitForIdle()
        ui.onNodeWithTag("tab_2").assertIsSelected()
        dock.performTouchInput {up()}
        ui.waitForIdle()
        ui.onNodeWithTag("tab_0").assertIsSelected()
        assertFalse(stored().instantDock)
    }

    @Test fun nativeAndFloatingBarsSwitchInBothStyles() {
        for(miuix in listOf(true,false)) {
            runBlocking {app.settings.update(AppSettings(useMiuix=miuix,dynamicColor=false,haptics=false))}
            ui.waitForIdle()
            theme()
            tap("floating_dock")
            waitSetting {!it.floatingDock}
            back();back()
            ui.onNodeWithTag("dock").assertDoesNotExist()
            tap("tab_1")
            ui.onNodeWithTag("tab_1").assertIsSelected()
            theme()
            tap("floating_dock")
            waitSetting {it.floatingDock}
            tap("blur")
            waitSetting {!it.blur}
            back();back()
            ui.onNodeWithTag("dock").assertIsDisplayed()
            tap("tab_0")
            ui.onNodeWithTag("tab_0").assertIsSelected()
        }
    }
}
