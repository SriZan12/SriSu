package com.srisu.srisu.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.srisu.srisu.core.data.local.CatalogueDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.onClose
import org.koin.dsl.module

actual val platformNetworkModule = module {
    single<HttpClientEngine> { OkHttp.create { config { retryOnConnectionFailure(false); pingInterval(25, java.util.concurrent.TimeUnit.SECONDS) } } } onClose { it?.close() }
    single {
        val context: Context = get()
        Room.databaseBuilder<CatalogueDatabase>(context, context.getDatabasePath("srisu-public-catalogue.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    } onClose { it?.close() }
}
