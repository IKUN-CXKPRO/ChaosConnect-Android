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
    @Serializable data object Files : Route
    @Serializable data object Terminal : Route
    @Serializable data object BandSettings : Route
    @Serializable data object PackageStudio : Route
    @Serializable data object PackageInstall : Route
    @Serializable data object SpeedTest : Route
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
    terminal:TerminalState=TerminalState(),onTerminalStart:()->Unit={},onTerminalStop:()->Unit={},onTerminalSend:(String)->Unit={},onTerminalInterrupt:()->Unit={},onTerminalClear:()->Unit={},onTerminalVisible:(Boolean)->Unit={},
    speedTest:SpeedTestState=SpeedTestState(),onSpeedStart:()->Unit={},onSpeedStop:()->Unit={},packageInstall:PackageInstallState=PackageInstallState(),onPackageChoose:()->Unit={},onPackageAsk:()->Unit={},onPackageConfirm:()->Unit={},onPackageCancel:()->Unit={},onPackageStop:()->Unit={},bandSettings:BandSettingsState=BandSettingsState(),onBandRefresh:()->Unit={},onBandWrite:(com.chaosconnect.app.protocol.BandSetting,String)->Unit={_,_->}) {
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
    val openFiles={navigate(Route.Files)}
    val openTerminal={navigate(Route.Terminal)}
    val openConnection={navigation.main(1)}
    val openSettings={navigate(Route.Settings)}
    val openProtocol={navigate(Route.Protocol)}
    val openTheme={navigate(Route.Theme)}
    val openAbout={navigate(Route.About)}
    LaunchedEffect(com.chaos.bandpack.data.Incoming.sequence){if(com.chaos.bandpack.data.Incoming.uri!=null)navigate(Route.PackageStudio)}
    val connectionStatus=pairingStatus(bluetooth,protocol)
    val deviceName=homeDeviceName(bluetooth,devices)
    LaunchedEffect(stack.lastOrNull(),protocol.phase,protocol.access?.permissions,browser.loaded) {
        if(stack.lastOrNull()==Route.Files && protocol.phase==SessionPhase.CONNECTED && !browser.loaded)onRefreshFiles()
    }
    LaunchedEffect(protocol.phase,protocol.access?.permissions,stack.lastOrNull(),resumed) {
        onTerminalVisible(stack.lastOrNull()==Route.Terminal && resumed)
        if(stack.lastOrNull()==Route.Terminal && resumed && protocol.phase==SessionPhase.CONNECTED && protocol.access?.allows(com.chaosconnect.app.protocol.MobilePermission.NSH)==true)onTerminalStart()
    }
    LaunchedEffect(stack.lastOrNull(),protocol.phase,protocol.access?.permissions,bandSettings.loaded){
        if(stack.lastOrNull()==Route.BandSettings&&protocol.phase==SessionPhase.CONNECTED&&protocol.access?.allows(com.chaosconnect.app.protocol.MobilePermission.CONTROL)==true&&!bandSettings.loaded)onBandRefresh()
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
                            0 -> MiuixHome(deviceName,connectionStatus,openConnection,openSettings,openFiles,openTerminal)
                            1 -> MiuixConnection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings)
                            2 -> MiuixFunctions(openFiles,openTerminal,{navigate(Route.PackageInstall)},{navigate(Route.BandSettings)},{navigate(Route.PackageStudio)})
                            else -> MiuixProfile(openSettings,openProtocol,openAbout)
                        } else when(index) {
                            0 -> Md3Home(deviceName,connectionStatus,openConnection,openSettings,openFiles,openTerminal)
                            1 -> Md3Connection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings)
                            2 -> Md3Functions(openFiles,openTerminal,{navigate(Route.PackageInstall)},{navigate(Route.BandSettings)},{navigate(Route.PackageStudio)})
                            else -> Md3Profile(openSettings,openProtocol,openAbout)
                        }
                    }
                }
                if(settings.floatingDock) {
                    LiquidDock(settings,selected,backdrop,select,Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(bottom=10.dp))
                } else if(settings.useMiuix) {
                    MiuixNavigation(selected,select,Modifier.align(Alignment.BottomCenter))
                } else {
                    Md3Navigation(selected,select,Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        entry<Route.Files> {
            if(settings.useMiuix) MiuixFiles(browser,protocol,openConnection,onRefreshFiles,onParent,onMore,onEntry,openSettings,back)
            else Md3Files(browser,protocol,openConnection,onRefreshFiles,onParent,onMore,onEntry,openSettings,back)
        }
        entry<Route.Terminal> {
            if(settings.useMiuix) MiuixTerminal(terminal,protocol,openConnection,onTerminalStart,onTerminalStop,onTerminalSend,onTerminalInterrupt,onTerminalClear,back)
            else Md3Terminal(terminal,protocol,openConnection,onTerminalStart,onTerminalStop,onTerminalSend,onTerminalInterrupt,onTerminalClear,back)
        }
        entry<Route.SpeedTest> {
            if(settings.useMiuix) MiuixSpeedTest(speedTest,protocol,onSpeedStart,onSpeedStop,openConnection,back)
            else Md3SpeedTest(speedTest,protocol,onSpeedStart,onSpeedStop,openConnection,back)
        }
        entry<Route.PackageInstall> {
            if(settings.useMiuix)MiuixPackageInstall(packageInstall,protocol.phase==SessionPhase.CONNECTED,onPackageChoose,onPackageAsk,onPackageConfirm,onPackageCancel,onPackageStop,openConnection,back)
            else Md3PackageInstall(packageInstall,protocol.phase==SessionPhase.CONNECTED,onPackageChoose,onPackageAsk,onPackageConfirm,onPackageCancel,onPackageStop,openConnection,back)
        }
        entry<Route.BandSettings> {
            if(settings.useMiuix)MiuixBandSettings(bandSettings,protocol,onBandRefresh,onBandWrite,openConnection,back)
            else Md3BandSettings(bandSettings,protocol,onBandRefresh,onBandWrite,openConnection,back)
        }
        entry<Route.PackageStudio> { com.chaosconnect.app.ui.components.PackageStudio(back) }
        entry<Route.Settings> {
            if(settings.useMiuix) MiuixSettings(settings,back,{navigate(Route.Theme)},{navigate(Route.About)},{navigate(Route.Protocol)},{navigate(Route.SpeedTest)})
            else Md3Settings(settings,back,{navigate(Route.Theme)},{navigate(Route.About)},{navigate(Route.Protocol)},{navigate(Route.SpeedTest)})
        }
        entry<Route.Theme> {if(settings.useMiuix)MiuixThemeScreen(settings,update,back) else Md3ThemeScreen(settings,update,back)}
        entry<Route.Pairing> {if(settings.useMiuix)MiuixConnection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings,openTheme,openAbout) else Md3Connection(bluetooth,onScan,onConnect,protocol,onDisconnect,openProtocol,openSettings,openTheme,openAbout)}
        entry<Route.About> {if(settings.useMiuix)MiuixAbout(back) else Md3About(back)}
        entry<Route.Protocol> {if(settings.useMiuix)MiuixProtocol(protocol,protocolCheck,onCheck,onDisconnect,back,onDeveloper) else Md3Protocol(protocol,protocolCheck,onCheck,onDisconnect,back,onDeveloper)}
    }
}
