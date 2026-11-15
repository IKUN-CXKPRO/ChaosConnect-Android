// SPDX-License-Identifier: GPL-3.0-only
// Theme coordination adapted from InstallerX Revived's InstallerTheme.kt.
// Copyright (C) 2025-2026 InstallerX Revived contributors
// Modified for ChaosConnect: shared settings and stable Miuix 0.9.4.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.chaosconnect.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import com.chaosconnect.app.data.AppSettings
import com.chaosconnect.app.data.DarkMode
import com.chaosconnect.app.ui.port.theme.animateAsState
import com.chaosconnect.app.ui.port.theme.Typography
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.ThemeColorSpec

@Immutable
data class ShellColors(val background: Color, val card: Color, val text: Color,
    val muted: Color, val accent: Color, val accentContainer: Color, val onAccent: Color, val outline: Color)
val LocalShellColors = staticCompositionLocalOf<ShellColors> { error("AppTheme required") }

@Composable
fun AppTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val preservedContent = remember { movableContentOf<@Composable () -> Unit> { it() } }
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.darkMode) { DarkMode.SYSTEM -> systemDark; DarkMode.DARK -> true; DarkMode.LIGHT -> false }
    val context = LocalContext.current
    val seed = Color(settings.seedColor)
    val key = if(settings.dynamicColor) colorResource(android.R.color.system_accent1_500) else seed
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    if (settings.useMiuix) {
        val controller = remember(key, settings.palette, settings.spec2025, dark) {
            ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = key,
                paletteStyle = ThemePaletteStyle.valueOf(settings.palette.name),
                colorSpec = if(settings.spec2025) ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021,
            )
        }
        MiuixTheme(controller = controller) {
            val colors = MiuixTheme.colorScheme
            MiuixTheme(colors = if(dark && settings.pureBlack) colors.copy(surface=Color.Black) else colors) {
                val c = MiuixTheme.colorScheme
                CompositionLocalProvider(LocalShellColors provides ShellColors(
                    c.surface, c.surfaceContainer, c.onSurface, c.onSurfaceVariantSummary,
                    c.primary, c.secondaryContainer, if (c.primary.luminance() > .5f) Color.Black else Color.White,
                    c.onSurface.copy(alpha = .09f)
                )) { preservedContent(content) }
            }
        }
    } else {
        val base = remember(key,dark,settings.palette,settings.spec2025) {
            dynamicColorScheme(seedColor=key, isDark=dark,
                style=when(settings.palette.name) {
                    "Neutral" -> PaletteStyle.Neutral; "Vibrant" -> PaletteStyle.Vibrant
                    "Expressive" -> PaletteStyle.Expressive; "Rainbow" -> PaletteStyle.Rainbow
                    "FruitSalad" -> PaletteStyle.FruitSalad; "Monochrome" -> PaletteStyle.Monochrome
                    "Fidelity" -> PaletteStyle.Fidelity; "Content" -> PaletteStyle.Content
                    else -> PaletteStyle.TonalSpot
                },
                specVersion=if(settings.spec2025) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021)
        }
        val scheme = (if(dark && settings.pureBlack) base.copy(background=Color.Black,surface=Color.Black,surfaceContainer=Color.Black) else base).animateAsState()
        MaterialExpressiveTheme(colorScheme=scheme, motionScheme=MotionScheme.expressive(), typography=Typography) {
            CompositionLocalProvider(LocalShellColors provides ShellColors(
                scheme.surfaceContainer, scheme.surfaceBright, scheme.onSurface, scheme.onSurfaceVariant,
                scheme.primary, scheme.primaryContainer, scheme.onPrimary, scheme.outlineVariant.copy(alpha = .5f)
            )) { preservedContent(content) }
        }
    }
}
