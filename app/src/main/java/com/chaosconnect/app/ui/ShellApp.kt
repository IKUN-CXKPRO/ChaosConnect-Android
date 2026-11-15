package com.chaosconnect.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.chaosconnect.app.data.*
import com.chaosconnect.app.protocol.SessionState
import com.chaosconnect.app.protocol.BluetoothState
import com.chaosconnect.app.ui.components.LiquidDock
import com.chaosconnect.app.ui.page.md3.*
import com.chaosconnect.app.ui.page.miuix.*
import com.chaosconnect.app.ui.theme.LocalShellColors
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.*
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

@Serializable
sealed interface Route : NavKey {
    @Serializable data object Main : Route
    @Serializable data object Settings : Route
    @Serializable data object Theme : Route
    @Serializable data object Pairing : Route
    @Serializable data object About : Route
    @Serializable data object Protocol : Route
}

@Composable
fun ShellApp(settings: AppSettings, devices: List<SavedDevice>, update: ((AppSettings) -> AppSettings) -> Unit, navigation: ShellNavigation,
    protocol:SessionState,protocolCheck:Boolean?,onCheck:()->Unit,onDisconnect:()->Unit,bluetooth:BluetoothState=BluetoothState(),onScan:()->Unit={},onConnect:(String)->Unit={},onDeveloper:()->Unit={}) {
    val c=LocalShellColors.current
    val stack=navigation.stack
    val pager=navigation.pager
    val selected=navigation.selected
    val select=navigation::select
    val navigate=navigation::navigate
    val back=navigation::back
    BackHandler(stack.size==1 && selected!=0) {select(0)}
    NavDisplay(stack,Modifier.fillMaxSize(),onBack=back,transition=NavTransitions.MiuixDefault,
        effects=NavDisplayEffects(cornerClipRadius=rememberNavSystemCornerRadius(),backdropColor=c.background)) {
        entry<Route.Main> {
            val backdrop=rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().background(c.background).then(if(stack.lastOrNull()==Route.Main)Modifier else Modifier.clearAndSetSemantics {})) {
                HorizontalPager(pager,Modifier.fillMaxSize().layerBackdrop(backdrop),beyondViewportPageCount=1,key={it}) {index ->
                    Box(Modifier.fillMaxSize().graphicsLayer {clip=true}.then(if(index==pager.currentPage)Modifier else Modifier.clearAndSetSemantics {})) {
                        if(settings.useMiuix) when(index) {
                            0 -> MiuixHome({navigate(Route.Pairing)},{select(1)})
                            1 -> MiuixDevices(devices) {navigate(Route.Pairing)}
                            else -> MiuixProfile({navigate(Route.Settings)},{navigate(Route.About)})
                        } else when(index) {
                            0 -> Md3Home({navigate(Route.Pairing)},{select(1)})
                            1 -> Md3Devices(devices) {navigate(Route.Pairing)}
                            else -> Md3Profile({navigate(Route.Settings)},{navigate(Route.About)})
                        }
                    }
                }
                if(settings.floatingDock) {
                    LiquidDock(settings,selected,backdrop,select,Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(bottom=12.dp))
                } else if(settings.useMiuix) {
                    MiuixNavigation(selected,select,Modifier.align(Alignment.BottomCenter))
                } else {
                    Md3Navigation(selected,select,Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        entry<Route.Settings> {
            if(settings.useMiuix) MiuixSettings(settings,back,{navigate(Route.Theme)},{navigate(Route.About)},{navigate(Route.Protocol)})
            else Md3Settings(settings,back,{navigate(Route.Theme)},{navigate(Route.About)},{navigate(Route.Protocol)})
        }
        entry<Route.Theme> {if(settings.useMiuix)MiuixThemeScreen(settings,update,back) else Md3ThemeScreen(settings,update,back)}
        entry<Route.Pairing> {if(settings.useMiuix)MiuixPairing(back,{navigate(Route.Protocol)},bluetooth,onScan,onConnect) else Md3Pairing(back,{navigate(Route.Protocol)},bluetooth,onScan,onConnect)}
        entry<Route.About> {if(settings.useMiuix)MiuixAbout(back) else Md3About(back)}
        entry<Route.Protocol> {if(settings.useMiuix)MiuixProtocol(protocol,protocolCheck,onCheck,onDisconnect,back,onDeveloper) else Md3Protocol(protocol,protocolCheck,onCheck,onDisconnect,back,onDeveloper)}
    }
}
