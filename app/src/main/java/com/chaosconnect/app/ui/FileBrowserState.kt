package com.chaosconnect.app.ui

import com.chaosconnect.app.protocol.RemoteEntry
import com.chaosconnect.app.protocol.RemoteStat

data class FileBrowserState(
    val path:String="/data",
    val entries:List<RemoteEntry> = emptyList(),
    val total:Int=0,
    val loading:Boolean=false,
    val loaded:Boolean=false,
    val message:String?=null,
    val selectedName:String?=null,
    val selectedStat:RemoteStat?=null,
) {
    val hasMore get()=loaded && entries.size<total
    fun child(name:String)=(if(path=="/")"" else path)+"/"+name
    val parent get()=path.substringBeforeLast('/').ifEmpty {"/"}
}
