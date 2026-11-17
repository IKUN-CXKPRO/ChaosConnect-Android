package com.chaosconnect.app.ui

data class PackageInstallState(val name:String="",val kind:String="",val files:Int=0,val total:Long=0,val done:Long=0,val busy:Boolean=false,val confirming:Boolean=false,val message:String="选择 Chaos 制作台输出的 .bin 投递包")
