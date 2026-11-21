package com.chaosconnect.app.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BandSetting(val id:Int,val kind:Int,val minimum:Long,val maximum:Long,val group:String,val label:String,val value:String,val options:List<String>) {
    fun validate(value:String):Boolean=when(kind){
        0,1,2->value.isNotEmpty()&&value.all{it in '0'..'9'}&&value.toLongOrNull()?.let{it in minimum..maximum}==true
        3->value.toByteArray(Charsets.UTF_8).size.toLong() in minimum..maximum && '\u0000' !in value
        4->value.isEmpty()
        else->false
    }
}
data class SettingPatch(val id:Int,val value:String?=null,val label:String?=null,val options:List<String>?=null,val removed:Boolean=false)
data class SettingsUpdate(val patches:List<SettingPatch>,val incomplete:Boolean=false) {
    fun merge(items:List<BandSetting>):List<BandSetting> {
        val byId=patches.associateBy{it.id}
        return items.mapNotNull {item->val patch=byId[item.id]?:return@mapNotNull item
            if(patch.removed)null else item.copy(value=patch.value?:item.value,label=patch.label?:item.label,options=patch.options?:item.options)
        }
    }
}
internal data class SettingsPage(val total:Int,val next:Int,val items:List<BandSetting>)
/** Explicit catalogue, never remote writes to preference files or ChaosConnect policy. */
class ChaosSettings(private val session:ChaosSession) {
    private val mutex=Mutex()
    private val fastSync get()=session.state.value.negotiation?.capabilities?.and(128)==128
    private fun supported(){checkWire(session.state.value.negotiation?.capabilities?.and(64)==64,"请更新支持手环设置的Chaos模块")}
    private suspend fun command(op:Int,payload:ByteArray):ByteArray {
        supported()
        val token=withTimeout(8_000){while(true){try {val r=WireReader(session.request(op,payload).payload);val t=r.u32();r.end();checkWire(t!=0L,"设置请求凭据无效");return@withTimeout t}catch(e:DeviceStatusException){if(e.code!=8)throw e;delay(50)}};error("unreachable")}
        return withTimeout(20_000){
            var pending=0
            while(true){
                val frame=try{session.request(Op.SETTINGS_RESULT,WireWriter().u32(token).build())}catch(e:DeviceStatusException){if(e.code!=8)throw e;delay(50);continue}
                val r=WireReader(frame.payload);val done=r.u8();val status=r.u8();checkWire(done in 0..1,"设置回执无效")
                if(done==0){r.end();pending++;delay(if(pending<=3)20 else if(pending<10)50 else 100);continue}
                if(status!=0)throw DeviceStatusException(status,op)
                return@withTimeout r.bytes(r.remaining)
            };error("unreachable")
        }
    }
    suspend fun load(onPage:(List<BandSetting>,Int)->Unit={_,_->}):List<BandSetting> = mutex.withLock {
        var offset=0;var total:Int?=null;val all=mutableListOf<BandSetting>()
        do {
            val request=WireWriter().u16(offset).apply{if(fastSync)u8(32)}.build()
            val page=decode(command(Op.SETTINGS_LIST,request),offset,if(fastSync)32 else 8)
            checkWire(total==null||total==page.total,"设置目录发生变化，请刷新")
            total=page.total;all+=page.items;offset=page.next
            checkWire(all.map{it.id}.toSet().size==all.size,"设置ID重复");onPage(all.toList(),page.total)
        }while(offset<total)
        checkWire(all.map{it.id}.toSet().size==all.size,"设置ID重复");all
    }
    suspend fun apply(setting:BandSetting,value:String):SettingsUpdate = mutex.withLock {
        checkWire(setting.validate(value),"输入不符合设置范围")
        val raw=value.toByteArray(Charsets.UTF_8);checkWire(raw.size<=240,"内容不能超过240字节")
        val body=command(if(fastSync)Op.SETTINGS_APPLY else Op.SETTINGS_WRITE,WireWriter().u16(setting.id).u16(raw.size).bytes(raw).build())
        if(fastSync)decodeUpdate(body).also{update->checkWire(update.patches.any{it.id==setting.id&&it.value!=null},"设置回执缺少修改项")}
        else {
            val r=WireReader(body);checkWire(r.u16()==setting.id,"设置回执不匹配");val actual=r.text(r.u16());r.end()
            // Legacy modules still return the actual written value. Do not reload
            // the entire catalogue or silently hold up the next user operation.
            SettingsUpdate(listOf(SettingPatch(setting.id,value=actual)))
        }
    }
    suspend fun write(setting:BandSetting,value:String):String = apply(setting,value).patches.first{it.id==setting.id}.value!!
    companion object {
        internal fun decodeUpdate(bytes:ByteArray):SettingsUpdate {
            val r=WireReader(bytes);checkWire(r.u8()==1,"设置回执版本不支持")
            val state=r.u8();val count=r.u16();checkWire(state in 0..1&&count in 1..256,"设置回执数量无效")
            val ids=mutableSetOf<Int>();fun text()=r.text(r.u16())
            val patches=List(count){
                val id=r.u16();val flags=r.u8();checkWire(id>0&&ids.add(id)&&flags in 1..15,"设置回执字段无效")
                val value=if(flags and 1!=0)text()else null
                val label=if(flags and 2!=0)text().also{checkWire(it.isNotEmpty(),"设置名称为空")}else null
                val options=if(flags and 4!=0)text().let{if(it.isEmpty())emptyList()else it.split('|')}else null
                SettingPatch(id,value,label,options,flags and 8!=0)
            };r.end();return SettingsUpdate(patches,state==1)
        }
        internal fun decode(bytes:ByteArray,offset:Int,limit:Int=8):SettingsPage {
            val r=WireReader(bytes);checkWire(r.u8()==1,"设置目录版本不支持")
            val total=r.u16();val next=r.u16();val count=r.u8()
            checkWire(total in 1..512&&offset in 0..total&&count in 1..limit&&next==offset+count&&next<=total,"设置目录分页无效")
            fun text()=r.text(r.u16())
            val items=List(count){val id=r.u16();val kind=r.u8();val min=r.u32();val max=r.u32();val group=text();val label=text();val value=text();val opts=text()
                checkWire(id>0&&kind in 0..5&&min<=max&&group.isNotEmpty()&&label.isNotEmpty(),"设置字段无效")
                val options=if(opts.isEmpty())emptyList()else opts.split('|')
                checkWire(kind!=2||(min==0L&&max<256&&options.size.toLong()==max+1),"设置选项无效")
                BandSetting(id,kind,min,max,group,label,value,options)
            };r.end();return SettingsPage(total,next,items)
        }
    }
}
