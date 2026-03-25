package com.srisu.srisu.di

import android.content.Context
import com.liftric.kvault.KVault
import com.srisu.srisu.session.AndroidSessionStorage
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.utils.Constants.Auth.SESSION_FILE
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import java.io.File
import androidx.core.content.edit

actual val kVaultPlatformModule = module {
    single {
        createKVaultSafely(
            context = androidContext(),
            fileName = SESSION_FILE
        )
    }
    single<SessionStorage> { AndroidSessionStorage(get()) }
}

private fun createKVaultSafely(context: Context, fileName: String): KVault {
    return try {
        // Try to create KVault normally
        KVault(context = context, fileName = fileName)
    } catch (e: Exception) {
        // Handle the reinstall case - clear corrupted encrypted storage
        clearCorruptedStorage(context, fileName)

        // Create fresh KVault instance
        KVault(context = context, fileName = fileName)
    }
}

private fun clearCorruptedStorage(context: Context, fileName: String) {
    try {
        // Clear the SharedPreferences
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // Delete the physical SharedPreferences file
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(prefsDir, "$fileName.xml")
        if (prefsFile.exists()) {
            prefsFile.delete()
        }

        // Also try to clear any master key preferences that might be corrupted
        val masterKeyPrefs = context.getSharedPreferences(
            "__androidx_security_crypto_encrypted_prefs__",
            Context.MODE_PRIVATE
        )
        masterKeyPrefs.edit { clear() }

    } catch (e: Exception) {
        // If clearing fails, log but don't crash
        // You can add your logging here
        println("Failed to clear corrupted storage: ${e.message}")
    }
}
