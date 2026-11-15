package com.chaosconnect.app.ui.page.miuix

import com.chaosconnect.app.ui.components.ShellIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

@Composable
fun MiuixNavigation(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier=modifier) {
        listOf("首页" to ShellIcons.Home,"设备" to ShellIcons.Devices,"我的" to ShellIcons.Profile).forEachIndexed {i,(title,icon) ->
            NavigationBarItem(selected=selected==i,onClick={onSelect(i)},icon=icon,label=title,modifier=Modifier.testTag("tab_$i"))
        }
    }
}
