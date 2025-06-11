package com.srisu.srisu.application

import android.app.Application
import com.srisu.srisu.utils.AppContext
import com.srisu.srisu.utils.ConnectivityObserver
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class MainApplication : Application() {
    private lateinit var connectivityObserver: ConnectivityObserver


    override fun onCreate() {
        super.onCreate()
        /*initKoin {
            androidContext(this@MainApplication)
        }*/
        AppContext.setUp(applicationContext)
        initLogger()
        connectivityObserver = ConnectivityObserver()

    }


    private fun initLogger() {
        Napier.base(DebugAntilog())
    }
}