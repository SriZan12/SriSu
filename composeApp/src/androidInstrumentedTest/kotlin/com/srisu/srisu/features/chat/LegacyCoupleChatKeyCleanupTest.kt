package com.srisu.srisu.features.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.chat.matrix.AndroidMatrixPlatformStore
import java.security.KeyStore
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyCoupleChatKeyCleanupTest {
    @Test
    fun cleanupDeletesOnlyExactRetiredAliases() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val aliases = listOf(
            "com.srisu.couplechat.v1.7001",
            "com.srisu.couplechat.v1.7002",
        )
        val neighbor = "com.srisu.couplechat.v1.neighbor"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            (aliases + neighbor).forEach { alias ->
                keyStore.deleteEntry(alias)
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                    generateKey()
                }
            }

            AndroidMatrixPlatformStore(context).reconcileAccountData(currentAccountId = null)

            aliases.forEach { alias -> assertFalse(alias, keyStore.containsAlias(alias)) }
            assertTrue(keyStore.containsAlias(neighbor))
        } finally {
            (aliases + neighbor).forEach(keyStore::deleteEntry)
        }
    }
}
