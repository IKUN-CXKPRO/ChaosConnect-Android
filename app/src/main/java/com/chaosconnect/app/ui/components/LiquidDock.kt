package com.chaosconnect.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.ui.theme.LocalShellColors
import com.chaosconnect.app.ui.port.library.*
import top.yukonga.miuix.kmp.blur.Backdrop

/** Chaos content adapter for InstallerX Revived's ported floating navigation. */
@Composable
fun LiquidDock(settings: AppSettings, selected: Int, backdrop: Backdrop, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalShellColors.current
    val haptic = LocalHapticFeedback.current
    FloatingBottomBar(
        items = listOf("首页" to Glyph.HOME, "设备" to Glyph.DEVICES, "我的" to Glyph.PROFILE),
        selectedIndex = { selected },
        onSelected = {
            if(settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelect(it)
        },
        backdrop = backdrop,
        modifier = modifier.testTag("dock"),
        instantSwitch = settings.instantDock,
        mode = when {
            !settings.blur -> FloatingBottomBarMode.None
            settings.useMiuix -> FloatingBottomBarMode.LiquidGlass
            else -> FloatingBottomBarMode.Blur
        },
        colors = FloatingBottomBarDefaults.colors(c.card, c.accent, c.text, c.accent),
        iconContent = { item, _ -> GlyphIcon(item.second, color = LocalFloatingBottomBarContentColor.current) },
        labelContent = { item, _ -> Label(item.first, 11, color = LocalFloatingBottomBarContentColor.current) },
    )
}
