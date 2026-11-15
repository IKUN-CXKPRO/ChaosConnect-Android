package com.chaosconnect.app.ui

import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.chaosconnect.app.protocol.SessionPhase
import com.chaosconnect.app.protocol.RemoteEntry
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
    protocol:SessionState,protocolCheck:Boolean?,onCheck:()->Unit,onDisconnect:()->Unit,bluetooth:BluetoothState=BluetoothState(),onScan:()->Unit={},onConnect:(String)->Unit={},onDeveloper:()->Unit={},
    browser:FileBrowserState=FileBrowserState(),onRefreshFiles:()->Unit={},onParent:()->Unit={},onMore:()->Unit={},onEntry:(RemoteEntry)->Unit={},
    terminal:TerminalState=TerminalState(),onTerminalStart:()->Unit={},onTerminalStop:()->Unit={},onTerminalSend:(String)->Unit={},onTerminalInterrupt:()->Unit={},onTerminalClear:()->Unit={},onTerminalVisible:(Boolean)->Unit={}) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    val c=LocalShellColors.current
    val stack=navigation.stack
    val pager=navigation.pager
    val selected=navigation.selected
    val select=navigation::select
    val navigate=navigation::navigate
    val back=navigation::back
    val openSettings={navigate(Route.Settings)}
    val openProtocol={navigate(Route.Protocol)}
    val openTheme={navigate(Route.Theme)}
    val openAbout={navigate(Route.About)}
    val connectionStatus=pairingStatus(bluetooth,protocol)
    val deviceName=homeDeviceName(bluetooth,devices)
    LaunchedEffect(pager.settledPage,protocol.phase,protocol.access?.permissions,browser.loaded) {
        if(pager.settledPage==1 && protocol.phase==SessionPhase.CONNECTED && !browser.loaded)onRefreshFiles()
    }
    LaunchedEffect(pager.settledPage,protocol.phase,protocol.access?.permissions,stack.lastOrNull(),resumed) {
        onTerminalVisible(pager.settledPage==2 && stack.lastOrNull()==Route.Main && resumed)
        if(pager.settledPage==2 && stack.lastOrNull()==Route.Main && resumed && protocol.phase==SessionPhase.CONNECTED && protocol.access?.allows(com.chaosconnect.app.protocol.MobilePermission.NSH)==true)onTerminalStart()
    }
    DisposableEffect(Unit){onDispose{onTerminalVisible(false)}}
    BackHandler(stack.size==1 && selected!=0) {select(0)}
    NavDisplay(stack,Modifier.fillMaxSize(),onBack=back,transition=NavTransitions.MiuixDefault,
        effects=NavDisplayEffects(cornerClipRadius=rememberNavSystemCornerRadius(),backdropColor=c.background)) {
        entry<Route.Main> {
            val backdrop=rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().imePadding().background(c.background).then(if(stack.lastOrNull()==Route.Main)Modifier else Modifier.clearAndSetSemantics {})) {
                HorizontalPager(pager,Modifier.fillMaxSize().layerBackdrop(backdrop),beyondViewportPageCount=1,key={it}) {index ->
                    Box(Modifier.fillMaxSize().graphicsLayer {clip=true}.then(if(index==pager.currentPage)Modifier else Modifier.clearAndSetSemantics {})) {
                        if(settings.useMiuix) when(index) {
                            0 -> MiuixHome(deviceName,connectionStatus,{select(3)},openSettings)
                            1 -> MiuixFiles(browser,protocol,{select(3)},onRefreshFiles,onParent,onMore,onEntry,openSettings)
                            2 -> MiuixTerminal(terminal,protocol,{select(3)},onTerminalStart,onTerminalStop,onTerminalSend,onTerminalInterrupt,onTerminalClear)
                            3 -> MiuixConnection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings)
                            else -> MiuixProfile(openSettings,openProtocol,openAbout)
                        } else when(index) {
                            0 -> Md3Home(deviceName,connectionStatus,{select(3)},openSettings)
                            1 -> Md3Files(browser,protocol,{select(3)},onRefreshFiles,onParent,onMore,onEntry,openSettings)
                            2 -> Md3Terminal(terminal,protocol,{select(3)},onTerminalStart,onTerminalStop,onTerminalSend,onTerminalInterrupt,onTerminalClear)
                            3 -> Md3Connection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings)
                            else -> Md3Profile(openSettings,openProtocol,openAbout)
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
        entry<Route.Pairing> {if(settings.useMiuix)MiuixConnection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings,openTheme,openAbout) else Md3Connection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings,openTheme,openAbout)}
        entry<Route.About> {if(settings.useMiuix)MiuixAbout(back) else Md3About(back)}
        entry<Route.Protocol> {if(settings.useMiuix)MiuixProtocol(protocol,protocolCheck,onCheck,onDisconnect,back,onDeveloper) else Md3Protocol(protocol,protocolCheck,onCheck,onDisconnect,back,onDeveloper)}
    }
}
