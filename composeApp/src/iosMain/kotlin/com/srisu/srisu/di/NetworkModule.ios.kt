@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.srisu.srisu.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.srisu.srisu.core.data.local.CatalogueDatabase
import com.srisu.srisu.features.chat.matrix.IosMatrixPlatformStore
import com.srisu.srisu.features.chat.matrix.MatrixPlatformStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import org.koin.dsl.onClose
import org.koin.dsl.module

actual val platformNetworkModule = module {
    single<com.srisu.srisu.features.plans.data.PlanDevice> { checkNotNull(com.srisu.srisu.features.plans.data.NativePlanRuntime.device) }
    single<HttpClientEngine> { Darwin.create() } onClose { it?.close() }
    single {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            NSApplicationSupportDirectory, NSUserDomainMask, null, true, null
        ) ?: error("Application support directory unavailable")
        Room.databaseBuilder<CatalogueDatabase>(name = directory.path + "/srisu-public-catalogue.db")
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    } onClose { it?.close() }
    single<MatrixPlatformStore>(createdAtStart = true) { IosMatrixPlatformStore() }
}
