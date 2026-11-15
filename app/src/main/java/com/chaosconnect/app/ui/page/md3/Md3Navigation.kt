package com.chaosconnect.app.ui.page.md3

import androidx.compose.material3.*
import com.chaosconnect.app.ui.components.ShellIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Md3Navigation(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    ShortNavigationBar(modifier=modifier,arrangement=ShortNavigationBarArrangement.EqualWeight) {
        listOf("主页" to ShellIcons.Home,"文件管理" to ShellIcons.Files,"终端" to ShellIcons.Terminal,"连接" to ShellIcons.Connect,"我的" to ShellIcons.Profile).forEachIndexed {i,(title,icon) ->
            ShortNavigationBarItem(selected=selected==i,onClick={onSelect(i)},icon={Icon(icon,null)},label={Text(title)},modifier=Modifier.testTag("tab_$i"))
        }
    }
}
