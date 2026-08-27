package com.example.privateagent

import android.app.Application
import com.example.privateagent.data.network.NetworkModule

class PrivateAgentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.initialize(applicationContext)
    }
}