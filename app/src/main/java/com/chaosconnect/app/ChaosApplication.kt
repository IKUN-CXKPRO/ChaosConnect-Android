package com.chaosconnect.app

import android.app.Application
import com.chaosconnect.app.data.DeviceDatabase
import com.chaosconnect.app.data.SettingsRepository

class ChaosApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val database by lazy { DeviceDatabase.create(this) }
}
