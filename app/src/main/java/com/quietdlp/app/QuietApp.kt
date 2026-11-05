package com.quietdlp.app

import android.app.Application

class QuietApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
    }
}
