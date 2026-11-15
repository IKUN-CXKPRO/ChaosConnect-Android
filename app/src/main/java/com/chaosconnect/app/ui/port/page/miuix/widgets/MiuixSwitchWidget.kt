// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2025-2026 InstallerX Revived contributors
// Modified for ChaosConnect: extracted switch widget and added Modifier support.
package com.chaosconnect.app.ui.port.page.miuix.widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch

@Composable
fun MiuixSwitchWidget(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val toggleAction = {
        if (enabled) {
            onCheckedChange(!checked)
        }
    }

    BasicComponent(
        modifier = modifier,
        title = title,
        summary = description,
        enabled = enabled,
        onClick = toggleAction,
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}
