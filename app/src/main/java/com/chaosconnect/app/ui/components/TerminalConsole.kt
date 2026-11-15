package com.chaosconnect.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaosconnect.app.protocol.*
import com.chaosconnect.app.ui.TerminalState
import com.chaosconnect.app.ui.theme.LocalShellColors

@Composable
private fun TerminalAction(title:String,tag:String,enabled:Boolean=true,action:()->Unit) {
    val c=LocalShellColors.current
    Label(title,14,bold=true,modifier=Modifier.testTag(tag).background(c.accent.copy(alpha=if(enabled).12f else .04f),RoundedCornerShape(14.dp))
        .clickable(enabled=enabled,onClick=action).heightIn(min=48.dp).padding(horizontal=16.dp,vertical=13.dp),color=if(enabled)c.accent else c.muted)
}
@Composable
fun TerminalConsole(state:TerminalState,protocol:SessionState,onConnect:()->Unit,onStart:()->Unit,onStop:()->Unit,onSend:(String)->Unit,onInterrupt:()->Unit,onClear:()->Unit) {
    val c=LocalShellColors.current
    val connected=protocol.phase==SessionPhase.CONNECTED
    val allowed=protocol.access?.allows(MobilePermission.NSH)==true
    var command by rememberSaveable {mutableStateOf("")}
    val scroll=rememberScrollState()
    var oldMax by remember {mutableIntStateOf(0)}
    LaunchedEffect(state.output) {val follow=scroll.value>=oldMax-24;withFrameNanos{};if(follow)scroll.scrollTo(scroll.maxValue);oldMax=scroll.maxValue}
    Label(if(!connected)"请先连接手环" else if(!allowed)"NSH终端权限未开启" else state.message,16,bold=true,modifier=Modifier.testTag("terminal_status"))
    if(!connected)TerminalAction("连接手环","terminal_connect",action=onConnect)
    else if(!allowed)Label("请在手环 ChaosConnect → 权限设置 → Android端权限中开启“NSH终端”。",14,muted=true)
    else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TerminalAction(if(state.active)"停止" else "启动NSH","terminal_start_stop",!state.busy,if(state.active)onStop else onStart)
        TerminalAction("Ctrl+C","terminal_interrupt",state.active,onInterrupt)
        TerminalAction("清空","terminal_clear",action=onClear)
    }
    SelectionContainer {
        androidx.compose.foundation.text.BasicText(state.output.ifEmpty {"等待手环NSH输出…"},Modifier.fillMaxWidth().heightIn(min=240.dp,max=360.dp)
            .background(c.card,RoundedCornerShape(18.dp)).verticalScroll(scroll).padding(16.dp).testTag("terminal_output"),
            style=TextStyle(color=c.text,fontSize=13.sp,lineHeight=19.sp,fontFamily=FontFamily.Monospace))
    }
    BasicTextField(command,{if(it.length<=2047)command=it},enabled=connected && allowed && state.active,
        singleLine=true,textStyle=TextStyle(color=c.text,fontSize=15.sp,fontFamily=FontFamily.Monospace),cursorBrush=SolidColor(c.accent),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={if(command.isNotBlank()){onSend(command);command=""}}),
        modifier=Modifier.fillMaxWidth().background(c.card,RoundedCornerShape(14.dp)).padding(16.dp).testTag("terminal_input"),
        decorationBox={inner->if(command.isEmpty())Label("输入NSH命令",15,muted=true);inner()})
    TerminalAction("发送命令","terminal_send",state.active && command.isNotBlank()) {onSend(command);command=""}
}
