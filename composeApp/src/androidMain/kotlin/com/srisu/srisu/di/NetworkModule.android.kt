package com.srisu.srisu.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.srisu.srisu.core.data.local.CatalogueDatabase
import com.srisu.srisu.features.chat.matrix.AndroidMatrixPlatformStore
import com.srisu.srisu.features.chat.matrix.MatrixPlatformStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.onClose
import org.koin.dsl.module

actual val platformNetworkModule = module {
    single<com.srisu.srisu.features.plans.data.PlanDevice> { com.srisu.srisu.features.plans.data.AndroidPlanDevice(get()) }
    single<HttpClientEngine> { OkHttp.create { config { retryOnConnectionFailure(false); pingInterval(25, java.util.concurrent.TimeUnit.SECONDS) } } } onClose { it?.close() }
    single {
        val context: Context = get()
        Room.databaseBuilder<CatalogueDatabase>(context, context.getDatabasePath("srisu-public-catalogue.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    } onClose { it?.close() }
    single<MatrixPlatformStore>(createdAtStart = true) { AndroidMatrixPlatformStore(get()) }
}
