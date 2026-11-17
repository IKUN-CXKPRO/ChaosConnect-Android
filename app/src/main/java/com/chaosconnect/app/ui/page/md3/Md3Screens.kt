// SPDX-License-Identifier: GPL-3.0-only
// ChaosConnect content; Scaffold and preference patterns adapted from InstallerX Revived.
// Copyright (C) 2023-2026 iamr0s, InstallerX Revived contributors
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.chaosconnect.app.ui.page.md3

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.chaosconnect.app.ui.port.page.main.widget.setting.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chaosconnect.app.data.*
import com.chaosconnect.app.protocol.*
import com.chaosconnect.app.ui.components.*
import com.chaosconnect.app.ui.ConnectionStatus
import com.chaosconnect.app.ui.FileBrowserState
import com.chaosconnect.app.ui.homeDeviceName
import com.chaosconnect.app.ui.pairingStatus
import com.chaosconnect.app.ui.connectionTitle
import com.chaosconnect.app.ui.theme.LocalShellColors
import androidx.compose.material3.*

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = LocalShellColors.current.card), content = content)
}

@Composable
private fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 52.dp)) { Text(text) }
}

@Composable
private fun Toggle(title: String, summary: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    SwitchWidget(modifier=Modifier.testTag(tag),title=title,description=summary,iconPlaceholder=false,checked=checked,onCheckedChange=onChange)
}

@Composable
private fun Menu(title: String, summary: String, icon: Glyph, tag: String, onClick: () -> Unit) {
    BaseWidget(modifier=Modifier.testTag(tag),title=title,description=summary,iconPlaceholder=false,onClick=onClick) {
        GlyphIcon(Glyph.NEXT,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Page(title: String, subtitle: String, back: (() -> Unit)? = null, onSettings: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c=LocalShellColors.current
    val scrollBehavior=TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier=Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),containerColor=c.background,
        contentWindowInsets=WindowInsets.safeDrawing,
        topBar={LargeFlexibleTopAppBar(title={Text(title)},navigationIcon={if(back!=null) IconButton(onClick=back,modifier=Modifier.semantics {contentDescription="返回"}) {GlyphIcon(Glyph.BACK)}},
            actions={if(onSettings!=null) Row(
                Modifier.clip(RoundedCornerShape(20.dp)).testTag("open_settings").clickable(role=Role.Button,onClick=onSettings)
                    .heightIn(min=48.dp).padding(horizontal=16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                GlyphIcon(Glyph.SETTINGS,Modifier.size(20.dp)); Label("设置",15)
            }},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=c.background,scrolledContainerColor=c.background),scrollBehavior=scrollBehavior)}) {padding ->
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState())
            .padding(horizontal=16.dp).padding(top=12.dp,bottom=132.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}

@Composable
fun Md3Home(deviceName:String, status:ConnectionStatus, onConnect:()->Unit, onSettings:()->Unit, onFiles:()->Unit={}, onTerminal:()->Unit={}) {
    val c=LocalShellColors.current
    Page("主页","CHAOSCONNECT",onSettings=onSettings) {
        Panel(Modifier.testTag("home_device_card").clip(RoundedCornerShape(28.dp)).clickable(role=Role.Button,onClick=onConnect)) {
            Column(Modifier.fillMaxWidth().heightIn(min=240.dp).padding(24.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(24.dp)).background(c.accent.copy(alpha=.1f)),contentAlignment=Alignment.Center) {
                    GlyphIcon(Glyph.DEVICES,Modifier.size(32.dp),c.accent)
                }
                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Label(deviceName,28,bold=true,modifier=Modifier.testTag("home_device_name"))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).background(c.accent,CircleShape))
                        Label(status.title,16,muted=true,modifier=Modifier.testTag("home_connection_status"))
                    }
                }
            }
        }
        Label("常用功能",16,bold=true,modifier=Modifier.padding(start=8.dp,top=4.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            QuickFeature("文件管理","浏览手环文件",Glyph.FILES,"home_files",Modifier.weight(1f),onFiles)
            QuickFeature("NSH终端","运行系统命令",Glyph.TERMINAL,"home_terminal",Modifier.weight(1f),onTerminal)
        }
    }
}

@Composable
fun Md3Files(state:FileBrowserState,protocol:SessionState,onConnect:()->Unit,onRefresh:()->Unit,onParent:()->Unit,onMore:()->Unit,onEntry:(RemoteEntry)->Unit,onSettings:(()->Unit)?=null,back:(()->Unit)?=null) {
    val c=LocalShellColors.current
    val connected=protocol.phase==SessionPhase.CONNECTED
    val canRead=connected && (protocol.access?.allows(MobilePermission.READ)!=false)
    Page("文件管理","FILES",back=back,onSettings=onSettings) {
        if(!canRead) {
            Panel {
                Column(Modifier.fillMaxWidth().padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    GlyphIcon(Glyph.FILES,Modifier.size(48.dp),c.accent)
                    Label(if(connected)"尚未获得文件读取权限" else "连接手环后管理文件",22,bold=true)
                    Label(if(connected)"请在手环的 ChaosConnect 权限设置中允许读取文件。" else "浏览手环目录，查看文件与文件夹。",14,muted=true)
                    Action(if(connected)"查看连接状态" else "前往连接",onConnect,Modifier.fillMaxWidth().testTag("files_connect"))
                }
            }
        } else {
            Panel {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Label(state.path,18,bold=true,modifier=Modifier.testTag("file_path"))
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(state.path!="/") Action("上一级",onParent,Modifier.testTag("files_parent"))
                        Action(if(state.loading)"正在读取…" else "刷新",{if(!state.loading)onRefresh()},Modifier.testTag("files_refresh"))
                    }
                    Label(if(state.loading)"正在读取目录" else "${state.entries.size} / ${state.total} 个项目",12,muted=true)
                }
            }
            state.message?.let {Label(it,14,muted=true,modifier=Modifier.testTag("files_error"))}
            if(state.loaded && state.entries.isEmpty()) Panel {Label("此文件夹为空",16,muted=true,modifier=Modifier.padding(24.dp))}
            state.entries.forEach {entry ->
                Panel {
                    Menu(entry.name,if(entry.directory)"文件夹" else "${entry.size} 字节",if(entry.directory)Glyph.FILES else Glyph.INFO,"file_${entry.name}") {if(!state.loading)onEntry(entry)}
                }
            }
            if(state.hasMore)Action("加载更多",{if(!state.loading)onMore()},Modifier.fillMaxWidth().testTag("files_more"))
            state.selectedStat?.let {stat -> Panel {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Label(state.selectedName.orEmpty(),20,bold=true)
                    Label("大小：${stat.size} 字节",14)
                    Label("修改时间：${java.time.Instant.ofEpochSecond(stat.modifiedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}",13,muted=true)
                }
            }}
        }
    }
}

@Composable
fun Md3Settings(settings: AppSettings, back: () -> Unit, onTheme: () -> Unit, onAbout: () -> Unit, onProtocol: () -> Unit, onSpeed: () -> Unit = {}) {
    Page("设置", "PREFERENCES",back) {
        Panel { Menu("主题","${if(settings.useMiuix) "Miuix" else "Material 3"} · ${settings.darkMode.label}",Glyph.PALETTE,"open_theme",onTheme) }
        Panel { Menu("连接测速","测试当前蓝牙连接的传输速度",Glyph.CONNECT,"open_speed_test",onSpeed) }
        Panel { Menu("ChaosConnect 协议","CCN1 v1 · 文件桥",Glyph.CONNECT,"open_protocol",onProtocol) }
        Panel { Menu("关于","ChaosConnect ${com.chaosconnect.app.BuildConfig.VERSION_NAME}",Glyph.INFO,"settings_about",onAbout) }
    }
}

@Composable
private fun Options(title:String, content: SegmentedColumnScope.() -> Unit) {
    SegmentedColumn(title=title,contentPadding=PaddingValues(vertical=8.dp),content=content)
}

@Composable
private fun Choice(title:String, summary:String, selected:Boolean, tag:String, onClick:()->Unit) {
    RadioButtonWidget(title=title,description=summary.takeIf {it.isNotBlank()},selected=selected,iconPlaceholder=false,modifier=Modifier.testTag(tag),onClick=onClick)
}

@Composable
fun Md3ThemeScreen(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, back: () -> Unit) {
    val c=LocalShellColors.current
    Page("主题", "APPEARANCE",back) {
        Options("界面风格") {
            item { Choice("Material 3","Google 的生动设计",!settings.useMiuix,"style_md3") {update {it.copy(useMiuix=false)}} }
            item { Choice("Miuix","熟悉的 HyperOS 风格",settings.useMiuix,"style_miuix") {update {it.copy(useMiuix=true)}} }
            item { Toggle("悬浮底栏","将导航栏悬浮显示",settings.floatingDock,"floating_dock") {v->update {it.copy(floatingDock=v)}} }
        }
        Options("外观") {
            DarkMode.entries.forEach {mode -> item { Choice(mode.label,"",settings.darkMode==mode,"dark_${mode.name}") {update {it.copy(darkMode=mode)}} } }
            item { Toggle("纯黑背景","仅在深色模式下生效",settings.pureBlack,"pure_black") {v->update {it.copy(pureBlack=v)}} }
            item { Toggle("模糊效果","为悬浮导航启用背景效果",settings.blur,"blur") {v->update {it.copy(blur=v)}} }
        }
        Options("色彩") {
            item { Toggle("动态取色","使用系统壁纸的配色",settings.dynamicColor,"dynamic") {v->update {it.copy(dynamicColor=v)}} }
            item(animatedVisibility=!settings.dynamicColor) { Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(0xFF2B82E9,0xFF6750A4,0xFF137C66,0xFFB64768,0xFF976325).forEachIndexed {i,seed ->
                    Box(Modifier.size(44.dp).testTag("seed_$i").selectable(settings.seedColor==seed,role=Role.RadioButton){update {it.copy(seedColor=seed)}}
                        .semantics {contentDescription=listOf("蓝色","紫色","绿色","粉色","棕色")[i]}.padding(4.dp)
                        .border(if(settings.seedColor==seed)2.dp else 0.dp,if(settings.seedColor==seed)c.text else Color.Transparent,CircleShape).padding(5.dp).background(Color(seed),CircleShape))
                }
            }
            }
            item { Toggle("2025 色彩规范","使用新版 Material 配色规则",settings.spec2025,"spec_2025") {v->update {it.copy(spec2025=v)}} }
        }
        Options("调色板风格") {
            Palette.entries.forEach {palette -> item { Choice(palette.label,"",settings.palette==palette,"palette_${palette.name}") {update {it.copy(palette=palette)}} } }
        }
        Options("导航与动效") {
            item { Toggle("滑动即时切换页面","按住 Dock，滑过图标即可切页",settings.instantDock,"instant_dock") {v->update {it.copy(instantDock=v)}} }
            item { Toggle("触感反馈","切换 Dock 标签时轻触反馈",settings.haptics,"haptics") {v->update {it.copy(haptics=v)}} }
        }
    }
}


@Composable
fun Md3Connection(bluetooth:BluetoothState,onScan:()->Unit,onConnect:(String)->Unit,protocol:SessionState,onDisconnect:()->Unit,onProtocol:()->Unit,onSettings:()->Unit,onTheme:()->Unit={},onAbout:()->Unit={}) {
    val status=pairingStatus(bluetooth,protocol)
    val peers=BandDiscovery.visible(bluetooth.peers)
    val c=LocalShellColors.current
    Page("连接","CONNECT",onSettings=onSettings) {
        Panel {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Label(status.title,22,bold=true,modifier=Modifier.testTag("pair_connection_title"))
                Label(status.message,14,muted=true,modifier=Modifier.testTag("pair_connection_status"))
                if(protocol.phase!=SessionPhase.DISCONNECTED || bluetooth.connecting) {
                    bluetooth.selectedPeer?.let {Label(it.name,16,bold=true)}
                    Action("断开连接",onDisconnect,Modifier.fillMaxWidth().testTag("disconnect_bluetooth"))
                } else {
                    Action(if(bluetooth.scanning)"正在搜索…" else "搜索手环",{if(!bluetooth.scanning)onScan()},Modifier.fillMaxWidth().testTag("scan_bluetooth"))
                }
            }
        }
        Label("附近的手环",16,bold=true,modifier=Modifier.padding(start=8.dp,top=8.dp))
        if(peers.isEmpty()) {
            Panel {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    GlyphIcon(Glyph.CONNECT,Modifier.size(32.dp),c.accent)
                    Label(if(bluetooth.scanning)"正在寻找手环" else "未发现手环",18,bold=true)
                    Label("请在手环开启“发起蓝牙配对”，然后搜索。",14,muted=true)
                }
            }
        } else peers.forEach {peer ->
            val active=bluetooth.selectedPeer?.address==peer.address
            Panel {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Label(peer.name,18,bold=true)
                    Label(peer.address,12,muted=true)
                    Label(if(active && (protocol.attempted || bluetooth.connecting))status.title else if(peer.bonded)"已配对" else "未配对",13,muted=true)
                    if(!bluetooth.connecting && protocol.phase==SessionPhase.DISCONNECTED)
                        Action(if(peer.bonded)"连接" else "配对并连接",{onConnect(peer.address)},Modifier.fillMaxWidth().testTag("connect_${peer.address}"))
                }
            }
        }
        Label(BandDiscovery.HINT,12,muted=true,modifier=Modifier.padding(horizontal=8.dp))

    }
}

@Composable
fun Md3Protocol(state: SessionState, checked: Boolean?, onCheck: () -> Unit, onDisconnect: () -> Unit, back: () -> Unit, onDeveloper:()->Unit={}) {
    Page("ChaosConnect 协议", "CCN1",back) {
        Panel {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Label("CCN1 v1",28,bold=true)
                Label(state.connectionTitle,18,bold=true)
                Label(state.message,14,muted=true)
                Label("承载：WearPacket OTHER / ChaosConnect",12,muted=true)
            }
        }
        Panel {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Label("会话与权限",19,bold=true)
                Label("数据块：${state.negotiation?.chunk?.let {"$it B"} ?: "等待协商"}")
                Label("传输窗口：${state.negotiation?.window?.let {"$it 块"} ?: "等待协商"}")
                Label("手机权限：${state.access?.let{MobilePermission.labels(it.permissions)} ?: "等待手环状态"}")
                if(state.phase==SessionPhase.CONNECTED && state.access?.allows(MobilePermission.DEVELOPER)==true)
                    Action("请求开发者模式",onDeveloper,Modifier.fillMaxWidth())
                Label("开发者模式：${state.permission?.let {if(it.developer) "已开启" else "已关闭"} ?: "等待设备状态"}")
                Label("开发者模式授权：${state.permission?.let {when(it.authorization){1->"等待手环确认";2->"本次已允许";3->"本次已拒绝";4->"授权超时";else->"无待处理请求"}} ?: "等待设备状态"}")
            }
        }
        Panel {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Label("协议互通检查",19,bold=true)
                Label("使用模块仓库共享的 HELLO 帧与 Protobuf 固定向量校验本地实现。此检查不建立蓝牙连接。",13,muted=true)
                Action("校验协议向量",onCheck,Modifier.fillMaxWidth().testTag("check_protocol"))
                checked?.let {Label(if(it) "帧与承载向量一致" else "协议向量校验失败",14,bold=true,modifier=Modifier.testTag("protocol_result"))}
            }
        }
        Panel {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Label("已实现的客户端协议",19,bold=true)
                Label("目录分页、文件详情、新建、删除、复制、移动、上传、下载、CRC 提交、取消、链路回显、开发者授权与心跳。",14,muted=true)
                Label("上传／下载按设备协商窗口传输；未收到全部确认不会提交。",12,muted=true)
            }
        }
        if(state.phase!=SessionPhase.DISCONNECTED) Action("断开会话",onDisconnect,Modifier.fillMaxWidth())
    }
}

@Composable
fun Md3About(back: () -> Unit) {
    Page("关于", "ABOUT CHAOSCONNECT",back) {
        Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Logo(Modifier.size(104.dp));Label("ChaosConnect",28,bold=true);Label("CONNECT YOUR CHAOS.",12,muted=true);Label("${com.chaosconnect.app.BuildConfig.VERSION_NAME} · 预览版",13,muted=true)
        }
        Panel {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Label("为连接而生",20,bold=true);Label("Chaos 生态的全新设备入口。这个版本从一个简单、流畅、可定制的空间开始。",14,muted=true)}}
        Panel {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Label("开源组件",16,bold=true);Label("Jetpack Compose · Material 3 · Miuix\nAndroidX Room · DataStore\nMiuix 使用 Apache License 2.0\n界面组件移植自 InstallerX Revived（GPL-3.0）",13,muted=true)}}
    }
}

@Composable
fun Md3Profile(onSettings:()->Unit,onProtocol:()->Unit,onAbout:()->Unit) {
    Page("我的","PROFILE") {
        Panel {Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Logo(Modifier.size(64.dp));Label("ChaosConnect",24,bold=true)
            Label("连接你的手环，管理你的设备。",14,muted=true)
        }}
        Panel {
            Menu("设置","应用偏好与外观",Glyph.SETTINGS,"profile_settings",onSettings)
            Menu("连接详情","协议状态与手环授权",Glyph.CONNECT,"pair_protocol",onProtocol)
            Menu("关于 ChaosConnect","版本 ${com.chaosconnect.app.BuildConfig.VERSION_NAME} · 开源许可",Glyph.INFO,"open_about",onAbout)
        }
    }
}
@Composable
fun Md3Terminal(state:com.chaosconnect.app.ui.TerminalState,protocol:SessionState,onConnect:()->Unit,onStart:()->Unit,onStop:()->Unit,onSend:(String)->Unit,onInterrupt:()->Unit,onClear:()->Unit,back:(()->Unit)?=null) {
    Page("终端","NSH",back) {
        TerminalConsole(state,protocol,onConnect,onStart,onStop,onSend,onInterrupt,onClear)
    }
}

@Composable
private fun QuickFeature(title:String,summary:String,icon:Glyph,tag:String,modifier:Modifier,onClick:()->Unit) {
    val c=LocalShellColors.current
    Panel(modifier.clip(RoundedCornerShape(24.dp)).testTag(tag).clickable(role=Role.Button,onClick=onClick)) {
        Column(Modifier.fillMaxWidth().heightIn(min=144.dp).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            GlyphIcon(icon,Modifier.size(28.dp),c.accent)
            Spacer(Modifier.height(4.dp));Label(title,18,bold=true);Label(summary,12,muted=true)
        }
    }
}
@Composable
fun Md3Functions(onFiles:()->Unit,onTerminal:()->Unit,onPackage:()->Unit={}) {
    Page("功能列表","FUNCTIONS") {
        Panel {
            Menu("安装投递包","选择制作台输出的字体／图标 .bin",Glyph.PLUS,"functions_package",onPackage)
            Menu("文件管理","浏览目录与查看文件",Glyph.FILES,"functions_files",onFiles)
            Menu("NSH终端","连接手环原生终端",Glyph.TERMINAL,"functions_terminal",onTerminal)
        }
    }
}

@Composable
fun Md3SpeedTest(state:com.chaosconnect.app.ui.SpeedTestState,protocol:SessionState,onStart:()->Unit,onStop:()->Unit,onConnect:()->Unit,back:()->Unit) {
    Page("连接测速","SPEED TEST",back) {
        Panel {Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Label(com.chaosconnect.app.ui.speedText(state.progress.bytesPerSecond),32,bold=true,modifier=Modifier.testTag("speed_value"))
            Label(state.message,14,muted=true,modifier=Modifier.testTag("speed_status"))
            Label("已发送：${state.progress.sent} 字节",14)
            Label("已接收：${state.progress.received} 字节",14)
            Label("耗时：${state.progress.elapsedMillis / 1000.0} 秒",14)
            Label("点击开始后持续测速，点击停止结束，不限时间和数据量。速度按已校验数据量÷耗时计算，包含往返等待，不是单向带宽上限。",13,muted=true)
        }}
        if(protocol.phase!=SessionPhase.CONNECTED)Action("前往连接",onConnect,Modifier.fillMaxWidth().testTag("speed_connect"))
        else Action(if(state.running)"停止测速" else "开始测速",if(state.running)onStop else onStart,Modifier.fillMaxWidth().testTag("speed_start_stop"))
    }
}

@Composable
fun Md3PackageInstall(state:com.chaosconnect.app.ui.PackageInstallState,connected:Boolean,onChoose:()->Unit,onInstall:()->Unit,onConfirm:()->Unit,onCancel:()->Unit,onStop:()->Unit,onConnect:()->Unit,back:()->Unit){
    Page("安装投递包","DELIVERY PACKAGE",back){PackageInstallContent(state,connected,onChoose,onInstall,onConfirm,onCancel,onStop,onConnect)}
}
