package com.srisu.srisu

/** Offline UI tests retain app context but do not initialize production native providers. */
class IsolatedTestApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        com.srisu.srisu.utils.AppContext.setUp(applicationContext)
    }
}
