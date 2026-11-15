// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

// Modified for ChaosConnect: package isolation and stable Miuix 0.9.4 API compatibility.
package com.chaosconnect.app.ui.port.library.liquid

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/** Lightweight stand-in for Kyant's `vibrancy()`. */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
