package com.chaosconnect.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chaosconnect.app.protocol.*
import com.chaosconnect.app.ui.BandSettingsState
import com.chaosconnect.app.ui.theme.LocalShellColors

/** Shared catalogue UI; the outer page and switch widgets follow the selected style. */
@Composable
fun BandSettingsContent(state:BandSettingsState,protocol:SessionState,onRefresh:()->Unit,onWrite:(BandSetting,String)->Unit,onConnect:()->Unit,miuix:Boolean) {
    val c=LocalShellColors.current
    val connected=protocol.phase==SessionPhase.CONNECTED
    val permitted=protocol.access?.allows(MobilePermission.CONTROL)==true
    val supported=protocol.negotiation?.capabilities?.and(64)==64
    var group by remember {mutableStateOf<String?>(null)}
    var editing by remember {mutableStateOf<BandSetting?>(null)}
    LaunchedEffect(permitted,connected){if(!permitted||!connected)editing=null}
    val scheme=if(c.background.luminance()<.5f)darkColorScheme(primary=c.accent,surface=c.card,onSurface=c.text,onSurfaceVariant=c.muted,background=c.background)
        else lightColorScheme(primary=c.accent,surface=c.card,onSurface=c.text,onSurfaceVariant=c.muted,background=c.background)
    MaterialTheme(colorScheme=scheme) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("设置由手环执行，支持Chaos的显示、系统美化、模糊、截图录屏、表盘、应用管理和网络服务。ChaosConnect的连接与权限设置仅在手环修改。",color=c.muted)
            when {
                !connected->Button(onClick=onConnect){Text("连接手环")}
                !permitted->Text("请在手环的ChaosConnect → 权限设置 → Android端中开启“设备控制”。",color=c.text)
                !supported->Text("请更新手环Chaos模块后使用。",color=c.text)
                else->Button(onClick=onRefresh,enabled=!state.busy){Text(if(state.busy)"正在同步…" else "刷新手环设置")}
            }
            Text(state.message,color=c.muted)
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(connected&&permitted&&supported)for((name,items)in state.items.groupBy{it.group}) {
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=c.card)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable{group=if(group==name)null else name}.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text(if(group==name)"收起" else "展开",color=c.accent)
                        }
                        if(group==name)for(item in items) {
                            HorizontalDivider(color=c.outline)
                            if(item.kind==0){
                                if(miuix)com.chaosconnect.app.ui.port.page.miuix.widgets.MiuixSwitchWidget(title=item.label,description=null,checked=item.value=="1",enabled=!state.busy,onCheckedChange={onWrite(item,if(it)"1" else "0")})
                                else Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(item.label,Modifier.weight(1f));Switch(item.value=="1",{onWrite(item,if(it)"1" else "0")},enabled=!state.busy)}
                            }else Column(Modifier.fillMaxWidth().clickable(enabled=!state.busy&&item.kind!=5){editing=item}.padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(item.label,color=c.text)
                                val display=if(item.kind==2)item.options.getOrNull(item.value.toIntOrNull()?:-1)?:item.value else item.value
                                Text(if(item.kind==4)"点击执行" else if(display.isBlank())if(item.kind==5)"暂无数据" else "点击设置" else display,color=c.muted)
                            }
                        }
                    }
                }
            }
        }
        editing?.let {item->
            var input by remember(item){mutableStateOf(if(item.value=="0"&&item.minimum>0)"" else item.value)}
            val action=item.kind==4
            AlertDialog(onDismissRequest={editing=null},title={Text(item.label)},text={
                if(action)Text("确认执行“${item.label}”？")
                else if(item.kind==2)Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){item.options.forEachIndexed{i,label->Row(Modifier.fillMaxWidth().clickable{input=i.toString()}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(input==i.toString(),{input=i.toString()});Text(label)}}}
                else Column {OutlinedTextField(input,{input=it},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(item.kind==1)KeyboardType.Number else KeyboardType.Text));Text(if(item.kind==1)"范围：${item.minimum}～${item.maximum}" else "长度：${item.minimum}～${item.maximum}字节");if(item.label.contains("删除")||item.options.getOrNull(input.toIntOrNull()?:-1)?.contains("卸载")==true)Text("此操作会删除对应内容，请核对后确认。")}
            },confirmButton={TextButton(enabled=!state.busy&&(action||item.validate(input)),onClick={editing=null;onWrite(item,if(action)"" else input)}){Text(if(action||item.label.contains("删除")||item.options.getOrNull(input.toIntOrNull()?:-1)?.contains("卸载")==true)"确认执行" else "应用")}},dismissButton={TextButton(onClick={editing=null}){Text("取消")}})
        }
    }
}
