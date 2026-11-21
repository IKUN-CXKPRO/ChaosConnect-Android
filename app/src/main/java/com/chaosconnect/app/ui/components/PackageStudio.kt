package com.chaosconnect.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chaos.bandpack.ui.*
import com.chaos.bandpack.ui.theme.UiPrefs

/** Entire local maker workspace; no dependency on a connected watch. */
@Composable
fun PackageStudio(back: () -> Unit) {
    val context=LocalContext.current
    remember(context.applicationContext) { UiPrefs.init(context.applicationContext); true }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width=when {maxWidth>=840.dp->WidthClass.EXPANDED;maxWidth>=600.dp->WidthClass.MEDIUM;else->WidthClass.COMPACT}
        CompositionLocalProvider(LocalWidthClass provides width) { ChaosApp(onExit=back) }
    }
}
