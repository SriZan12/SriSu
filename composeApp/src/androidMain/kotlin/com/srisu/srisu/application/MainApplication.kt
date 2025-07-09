package com.srisu.srisu.application

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.srisu.srisu.utils.AppContext
import com.srisu.srisu.utils.ConnectivityObserver
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class MainApplication : Application(), SingletonImageLoader.Factory {
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

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(
                        this,
                        0.25
                    ) // Use 25% of the app's available memory for the memory cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(5 * 1024 * 1024) // 5MB disk cache
                    .build()
            }
            // You can add more configurations here, e.g., crossfade animations
            .crossfade(true)
            .build()
    }
}
