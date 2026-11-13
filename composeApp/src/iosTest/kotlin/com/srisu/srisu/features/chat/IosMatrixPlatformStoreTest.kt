@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.srisu.srisu.features.chat

import com.srisu.srisu.features.chat.matrix.IosMatrixPlatformStore
import com.srisu.srisu.features.chat.matrix.MatrixStorageScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

class IosMatrixPlatformStoreTest {
    private fun store() = IosMatrixPlatformStore(legacyKeyCleaner = {})

    @Test
    fun retiredCoupleChatCleanupIsExactIdempotentAndDoesNotTrustItsMarker() = runBlocking {
        val fileManager = NSFileManager.defaultManager
        val appSupport = requireNotNull(
            fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        )
        val appSupportPath = requireNotNull(appSupport.path)
        val scope = MatrixStorageScope.create(
            2801,
            "https://api-ios-legacy-cleanup.test/",
            "https://matrix-ios-legacy-cleanup.test/",
            3801,
            1,
            "!couple:matrix-ios-legacy-cleanup.test",
        )
        val firstStore = store()
        firstStore.reconcileAccountData(currentAccountId = scope.accountId)
        val matrixDeviceId = firstStore.deviceId(scope)
        val matrixSentinel = "$appSupportPath/SriSuMatrix/sentinel".toPath()
        FileSystem.SYSTEM.write(matrixSentinel) { writeUtf8("matrix") }
        val legacyRecord = "$appSupportPath/couple-chat-v1/2801/records.sqlite".toPath()
        FileSystem.SYSTEM.createDirectories(requireNotNull(legacyRecord.parent))
        FileSystem.SYSTEM.write(legacyRecord) { writeUtf8("legacy") }
        val neighbor = "$appSupportPath/couple-chat-v1-neighbor/sentinel".toPath()
        FileSystem.SYSTEM.createDirectories(requireNotNull(neighbor.parent))
        FileSystem.SYSTEM.write(neighbor) { writeUtf8("keep") }
        NSUserDefaults.standardUserDefaults.setBool(
            true,
            forKey = "srisu.legacy-couple-chat-v1-cleanup-v1",
        )

        val restarted = store()
        assertTrue(FileSystem.SYSTEM.exists(requireNotNull(legacyRecord.parent?.parent)))
        restarted.reconcileAccountData(currentAccountId = scope.accountId)

        assertFalse(FileSystem.SYSTEM.exists(requireNotNull(legacyRecord.parent?.parent)))
        assertTrue(FileSystem.SYSTEM.exists(neighbor))
        assertEquals("matrix", FileSystem.SYSTEM.read(matrixSentinel) { readUtf8() })
        assertEquals(matrixDeviceId, restarted.deviceId(scope))

        FileSystem.SYSTEM.createDirectories(requireNotNull(legacyRecord.parent))
        FileSystem.SYSTEM.write(legacyRecord) { writeUtf8("restored") }
        val restartedAgain = store()
        restartedAgain.reconcileAccountData(currentAccountId = scope.accountId)
        assertFalse(FileSystem.SYSTEM.exists(requireNotNull(legacyRecord.parent?.parent)))
        assertTrue(FileSystem.SYSTEM.exists(neighbor))
        assertEquals(matrixDeviceId, restartedAgain.deviceId(scope))
    }

    @Test
    fun legacyCleanupFailureIsDeferredToReconciliation() {
        val store = IosMatrixPlatformStore { error("synthetic cleanup failure") }

        val failure = assertFailsWith<IllegalStateException> {
            runBlocking { store.reconcileAccountData(currentAccountId = null) }
        }

        assertEquals("synthetic cleanup failure", failure.message)
    }
}
