package com.moonraker.android

import android.app.Application
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.settings.SettingsRepository

class MoonrakerApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var moonrakerRepository: MoonrakerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        moonrakerRepository = MoonrakerRepository(settingsRepository)
    }

    companion object {
        lateinit var instance: MoonrakerApp
            private set
    }
}
