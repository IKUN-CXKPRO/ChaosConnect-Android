package com.chaosconnect.app.ui

import com.chaosconnect.app.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BandSettingsState(val items:List<BandSetting> = emptyList(),val busy:Boolean=false,val loaded:Boolean=false,val message:String="连接手环并允许设备控制权限后读取")
class BandSettingsController(private val scope:CoroutineScope) {
    private var session:ChaosSession?=null
    private var client:ChaosSettings?=null
    @Volatile private var generation=0
    private var job:Job?=null
    private val mutable=MutableStateFlow(BandSettingsState())
    val state=mutable.asStateFlow()
    fun bind(active:ChaosSession?){generation++;job?.cancel();job=null;session=active;client=active?.let{ChaosSettings(it)};mutable.value=BandSettingsState()}
    fun revoke(){generation++;job?.cancel();job=null;mutable.value=BandSettingsState(message="设备控制权限已撤销")}
    fun load(){run(null,null)}
    fun write(setting:BandSetting,value:String){run(setting,value)}
    private fun run(setting:BandSetting?,value:String?){
        if(mutable.value.busy)return
        val active=session?:return
        if(active.state.value.phase!=SessionPhase.CONNECTED||active.state.value.access?.allows(MobilePermission.CONTROL)!=true){revoke();return}
        val settings=client?:return;val epoch=generation
        mutable.update{it.copy(busy=true,message=if(setting==null)"正在读取手环设置…" else "正在修改${setting.label}…")}
        job=scope.launch(Dispatchers.IO){try{
            if(setting==null){
                val items=settings.load {partial,total->if(epoch==generation)mutable.update{it.copy(items=partial,message="正在读取手环设置：${partial.size}/$total")}}
                if(epoch==generation)mutable.value=BandSettingsState(items,loaded=true,message="已读取手环设置")
            }else{
                val result=settings.apply(setting,value!!)
                if(epoch==generation)mutable.update{it.copy(items=result.merge(it.items),busy=false,message=when{
                    result.incomplete->"设置已生效；部分列表可通过刷新更新"
                    setting.id==150->"字体切换已提交，退出手环Chaos后生效"
                    setting.kind==4||setting.id in 152..153->"操作已提交，手环会继续执行"
                    else->"已更新${setting.label}"
                })}
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==generation)mutable.update{it.copy(busy=false,message=e.message?:"设置操作失败")}}}
    }
}
