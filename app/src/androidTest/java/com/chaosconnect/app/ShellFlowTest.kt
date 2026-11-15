package com.chaosconnect.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
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
        if(!tag.startsWith("tab_") && tag!="open_settings") {
            node.performScrollTo()
            // Floating Dock overlays the viewport edge; center the target before a physical tap.
            val delta=node.fetchSemanticsNode().boundsInRoot.center.y-ui.onRoot().fetchSemanticsNode().boundsInRoot.center.y
            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasTestTag(tag)))
                .performSemanticsAction(SemanticsActions.ScrollBy) {it(0f,delta)}
            ui.waitForIdle()
        }
        node.performClick()
        ui.waitForIdle()
    }
    private fun waitSetting(predicate: (AppSettings) -> Boolean) = ui.waitUntil(5_000) { predicate(stored()) }
    private fun back() {
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
    }
    private fun theme() { tap("tab_3"); tap("profile_settings"); tap("open_theme") }
    private fun screenshot(name:String) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/chaos${BuildConfig.VERSION_NAME.replace(".","")}-$name.png").use {descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
            }
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
            ui.onNodeWithTag("tab_4").assertDoesNotExist()
            ui.onNodeWithTag("dock").assertHeightIsEqualTo(64.dp).assertWidthIsEqualTo(312.dp)
            screenshot(if(miuix) "miuix-home" else "md3-home")
            runBlocking {app.settings.update(stored().copy(darkMode=DarkMode.DARK))}
            ui.waitForIdle()
            screenshot(if(miuix) "miuix-home-dark" else "md3-home-dark")
            runBlocking {app.settings.update(stored().copy(darkMode=DarkMode.LIGHT))}
            ui.waitForIdle()
            tap("home_device_card")
            ui.onNodeWithTag("tab_1").assertIsSelected()
            ui.onNodeWithTag("pair_connection_title").assertIsDisplayed()
            ui.onNodeWithTag("pair_protocol").assertDoesNotExist()
            tap("tab_3");tap("pair_protocol");tap("check_protocol")
            ui.onNodeWithText("帧与承载向量一致").assertIsDisplayed()
            back()
            tap("tab_2");tap("functions_files")
            ui.onNodeWithText("连接手环后管理文件").assertIsDisplayed()
            back();ui.onNodeWithTag("tab_2").assertIsSelected()
            tap("functions_terminal")
            ui.onNodeWithTag("terminal_status",useUnmergedTree=true).assertTextEquals("请先连接手环")
            tap("terminal_connect");ui.onNodeWithTag("tab_1").assertIsSelected()
            tap("tab_0");tap("home_files")
            ui.onNodeWithText("连接手环后管理文件").assertIsDisplayed()
            tap("open_settings");tap("settings_about")
            ui.onNodeWithText("${BuildConfig.VERSION_NAME} · 预览版").assertIsDisplayed()
            back();back();back()
            ui.onNodeWithTag("tab_0").assertIsSelected()
            tap("home_terminal")
            ui.onNodeWithTag("terminal_status",useUnmergedTree=true).assertTextEquals("请先连接手环")
            back();ui.onNodeWithTag("tab_0").assertIsSelected()
            tap("tab_3");tap("open_about")
            ui.onNodeWithText("${BuildConfig.VERSION_NAME} · 预览版").assertIsDisplayed()
            back();ui.onNodeWithTag("connection_theme").assertDoesNotExist()
            tap("profile_settings");tap("open_speed_test")
            ui.onNodeWithTag("speed_value",useUnmergedTree=true).assertTextEquals("0.0 KiB/s")
            tap("speed_connect");ui.onNodeWithTag("tab_1").assertIsSelected()
            tap("tab_3");tap("profile_settings");tap("open_theme")
            ui.onNodeWithTag(if(miuix) "style_miuix" else "style_md3").assertIsSelected()
            back();back()
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
            dock.performTouchInput {down(Offset(width/8f,height/2f));moveTo(Offset(width*7/8f,height/2f),500)}
            ui.waitForIdle()
            ui.onNodeWithTag("tab_3").assertIsSelected()
            ui.onNodeWithTag("profile_settings").assertIsDisplayed()
            dock.performTouchInput {moveTo(Offset(width*5/8f,height/2f),500)}
            ui.waitForIdle()
            ui.onNodeWithTag("tab_2").assertIsSelected()
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
        dock.performTouchInput {down(Offset(width*7/8f,height/2f));moveTo(Offset(width/8f,height/2f),500)}
        ui.waitForIdle()
        ui.onNodeWithTag("tab_3").assertIsSelected()
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
