package com.example

import android.app.Application
import com.example.util.CrashLogger

class KrizApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
    }
}
