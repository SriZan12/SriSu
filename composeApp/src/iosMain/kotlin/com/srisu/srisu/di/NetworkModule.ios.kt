@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.srisu.srisu.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.srisu.srisu.core.data.local.CatalogueDatabase
import com.srisu.srisu.features.chat.v2.data.local.ChatDatabase
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
    single<HttpClientEngine> { Darwin.create() } onClose { it?.close() }
    single {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            NSApplicationSupportDirectory, NSUserDomainMask, null, true, null
        ) ?: error("Application support directory unavailable")
        Room.databaseBuilder<CatalogueDatabase>(name = directory.path + "/srisu-public-catalogue.db")
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    } onClose { it?.close() }
    single {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            NSApplicationSupportDirectory, NSUserDomainMask, null, true, null
        ) ?: error("Application support directory unavailable")
        val chatDirectory = requireNotNull(directory.URLByAppendingPathComponent("SriSuChatV2"))
        check(NSFileManager.defaultManager.createDirectoryAtURL(
            chatDirectory,
            withIntermediateDirectories = true,
            attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
            error = null,
        )) { "Unable to create protected chat storage" }
        check(chatDirectory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
            "Unable to exclude chat storage from backup"
        }
        Room.databaseBuilder<ChatDatabase>(name = chatDirectory.path + "/srisu-private-chat-v2.db")
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    } onClose { it?.close() }
}
