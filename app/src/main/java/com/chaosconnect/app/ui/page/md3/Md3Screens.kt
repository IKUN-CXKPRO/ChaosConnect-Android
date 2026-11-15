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
private fun Page(title: String, subtitle: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c=LocalShellColors.current
    val scrollBehavior=TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier=Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),containerColor=c.background,
        contentWindowInsets=WindowInsets.safeDrawing,
        topBar={LargeFlexibleTopAppBar(title={Text(title)},navigationIcon={if(back!=null) IconButton(onClick=back,modifier=Modifier.semantics {contentDescription="返回"}) {GlyphIcon(Glyph.BACK)}},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=c.background,scrolledContainerColor=c.background),scrollBehavior=scrollBehavior)}) {padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=padding.calculateTopPadding())
            .padding(horizontal=16.dp).padding(top=12.dp,bottom=132.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}

@Composable
fun Md3Home(onAdd: () -> Unit, onDevices: () -> Unit) {
    val c = LocalShellColors.current
    Page("ChaosConnect", "CHAOS ECOSYSTEM") {
        Panel {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(7.dp).background(c.accent,CircleShape)); Label("你的设备连接空间",12,bold=true,color=c.accent)
                }
                Label("连接，\n从这里开始。",32,bold=true)
                Label("让设备彼此相连，让体验更加简单。",14,muted=true)
                ConnectionArtwork(Modifier.fillMaxWidth().height(184.dp))
                Action("＋  添加第一台设备",onAdd,Modifier.fillMaxWidth().testTag("add_device"))
                Label("CCN1 v1 文件桥 · 等待已认证设备链路",11,muted=true)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Label("我的设备",20,bold=true)
            Label("管理设备  ›",12,modifier=Modifier.clip(RoundedCornerShape(8.dp)).clickable(role=Role.Button,onClick=onDevices).padding(8.dp),color=c.accent)
        }
        Panel {
            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                GlyphIcon(Glyph.DEVICES,Modifier.size(32.dp),c.muted)
                Column {Label("还没有设备",16,bold=true);Label("添加后，在这里查看和管理。",13,muted=true)}
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Panel(Modifier.weight(1f)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {GlyphIcon(Glyph.CONNECT,color=c.accent);Label("随心连接",15,bold=true);Label("统一的设备入口",12,muted=true)} }
            Panel(Modifier.weight(1f)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {GlyphIcon(Glyph.PALETTE,color=c.accent);Label("两种风格",15,bold=true);Label("熟悉的个性体验",12,muted=true)} }
        }
    }
}

@Composable
fun Md3Devices(devices: List<SavedDevice>, onAdd: () -> Unit) {
    val c = LocalShellColors.current
    Page("设备", "YOUR DEVICES") {
        Label("所有设备，都在这里。",15,muted=true)
        Panel {
            Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                ConnectionArtwork(Modifier.size(210.dp))
                Label(if(devices.isEmpty()) "等待第一台设备" else "已保存的设备",22,bold=true)
                Label("从一次连接开始，搭建你的 Chaos 生态。",13,muted=true)
                Action("添加设备",onAdd,Modifier.fillMaxWidth().testTag("devices_add"))
            }
        }
        devices.forEach { device -> Panel { Column(Modifier.padding(20.dp)) {Label(device.name,16,bold=true);Label("已保存",12,muted=true)} } }
        Row(Modifier.padding(4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {GlyphIcon(Glyph.INFO,color=c.muted);Label("已实现 CCN1 文件协议，正在等待已认证的小米设备链路。",13,muted=true)}
    }
}

@Composable
fun Md3Profile(onSettings: () -> Unit, onAbout: () -> Unit) {
    Page("我的", "MAKE IT YOURS") {
        Panel {
            Row(Modifier.padding(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Logo(Modifier.size(72.dp))
                Column {Label("欢迎来到 Chaos",22,bold=true);Label("每一次连接，都有新的可能。",13,muted=true)}
            }
        }
        Panel {
            Menu("设置","外观、导航与交互",Glyph.SETTINGS,"open_settings",onSettings)
            Menu("关于 ChaosConnect","版本 ${com.chaosconnect.app.BuildConfig.VERSION_NAME} · 预览版",Glyph.INFO,"open_about",onAbout)
        }
        Label("CONNECT YOUR CHAOS.",12,muted=true,modifier=Modifier.padding(start=8.dp,top=20.dp))
    }
}

@Composable
fun Md3Settings(settings: AppSettings, back: () -> Unit, onTheme: () -> Unit, onAbout: () -> Unit, onProtocol: () -> Unit) {
    Page("设置", "PREFERENCES",back) {
        Panel { Menu("主题","${if(settings.useMiuix) "Miuix" else "Material 3"} · ${settings.darkMode.label}",Glyph.PALETTE,"open_theme",onTheme) }
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
fun Md3Pairing(back: () -> Unit, onProtocol: () -> Unit, bluetooth:BluetoothState=BluetoothState(), onScan:()->Unit={}, onConnect:(String)->Unit={}) {
    Page("连接设备", "CHAOSCONNECT",back) {
        Panel {
            Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                ConnectionArtwork(Modifier.fillMaxWidth().height(180.dp))
                Label("ChaosConnect 文件桥",24,bold=true)
                Label("在手环上打开 Chaos → ChaosConnect → 启用。",15)
                Label("在手环进入“发起蓝牙配对”，保持配对窗口开启。搜索后点击手环，在系统提示中完成普通蓝牙配对，再到手环确认本次ChaosConnect会话。",14,muted=true)
                Label(bluetooth.message,14,muted=true)
                Action(if(bluetooth.scanning)"重新搜索手环" else "搜索手环",onScan,Modifier.fillMaxWidth().testTag("scan_bluetooth"))
                bluetooth.peers.forEach {peer ->
                    Action("${peer.name} · ${if(peer.bonded) "已配对" else "配对并连接"}",{if(!bluetooth.connecting)onConnect(peer.address)},Modifier.fillMaxWidth())
                }
                Action("查看协议状态",onProtocol,Modifier.fillMaxWidth().testTag("pair_protocol"))
                Label("专用普通蓝牙服务，权限由手环设置；配对不会自动授予文件或控制权限。",12,muted=true)
            }
        }
    }
}

@Composable
fun Md3Protocol(state: SessionState, checked: Boolean?, onCheck: () -> Unit, onDisconnect: () -> Unit, back: () -> Unit, onDeveloper:()->Unit={}) {
    Page("ChaosConnect 协议", "CCN1",back) {
        Panel {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Label("CCN1 v1",28,bold=true)
                Label(when(state.phase) {SessionPhase.CONNECTED->"设备已连接";SessionPhase.HANDSHAKING->"正在协商";SessionPhase.AWAITING_APPROVAL->"等待手环确认连接";else->"设备未连接"},18,bold=true)
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
                Label("授权：${state.permission?.let {when(it.authorization){1->"等待手环确认";2->"本次已允许";3->"本次已拒绝";4->"授权超时";else->"无待处理请求"}} ?: "等待设备状态"}")
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
