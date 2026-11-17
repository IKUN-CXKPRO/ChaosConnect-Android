package com.chaosconnect.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chaosconnect.app.ui.PackageInstallState
import com.chaosconnect.app.ui.theme.LocalShellColors

@Composable
fun PackageInstallContent(state:PackageInstallState,connected:Boolean,onChoose:()->Unit,onInstall:()->Unit,onConfirm:()->Unit,onCancel:()->Unit,onStop:()->Unit,onConnect:()->Unit) {
    val c=LocalShellColors.current
    @Composable fun action(title:String,tag:String,enabled:Boolean=true,click:()->Unit){
        Box(Modifier.fillMaxWidth().testTag(tag).background(c.accentContainer,RoundedCornerShape(18.dp)).clickable(enabled=enabled,onClick=click).padding(18.dp)){Label(title,16,color=if(enabled)c.text else c.muted)}
    }
    Column(Modifier.fillMaxWidth().background(c.card,RoundedCornerShape(24.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Label(state.message,15,modifier=Modifier.testTag("package_status"))
        if(state.name.isNotEmpty()){
            Label(state.name,24,bold=true,modifier=Modifier.testTag("package_name"))
            Label("${state.kind} · ${state.files} 个资源 · ${state.total} 字节",13,muted=true)
            if(state.busy)Label("已传输：${state.done} / ${state.total} 字节",14)
        }
        Label("只提取字体和图标资源，不执行包内脚本。安装后可在 Chaos 的字体／图标包列表中选择。",13,muted=true)
    }
    if(!connected)action("前往连接","package_connect",click=onConnect)
    action("选择 .bin 投递包","package_choose",!state.busy,onChoose)
    if(state.busy)action("取消安装","package_stop",click=onStop)
    else action("安装投递包","package_install",connected&&state.name.isNotEmpty(),onInstall)
    if(state.confirming)Dialog(onDismissRequest=onCancel){
        Column(Modifier.fillMaxWidth().background(c.card,RoundedCornerShape(24.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Label("是否安装投递包：${state.name}",20,bold=true,modifier=Modifier.testTag("package_confirmation"))
            Label("手环可能会再次询问是否允许安装，请核对投递包名称。",14,muted=true)
            action("取消","package_cancel_confirmation",click=onCancel)
            action("安装","package_confirm",click=onConfirm)
        }
    }
}
