package com.chaosconnect.app.ui

import com.chaosconnect.app.protocol.SpeedProgress

data class SpeedTestState(val running:Boolean=false,val measured:Boolean=false,val progress:SpeedProgress=SpeedProgress(),val message:String="测试当前手环连接的数据传输速度")
fun speedText(bytesPerSecond:Long):String = String.format(java.util.Locale.ROOT,"%.1f KiB/s",bytesPerSecond/1024.0)
