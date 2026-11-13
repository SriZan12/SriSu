package com.srisu.srisu.features.chat

import android.content.Context
import com.srisu.srisu.features.chat.matrix.AndroidMatrixPlatformStore
import com.srisu.srisu.features.chat.matrix.MatrixStorageScope
import com.srisu.srisu.features.chat.matrix.isLegacyCoupleChatKeyAlias
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidMatrixPlatformStoreTest {
    private fun store(context: Context = RuntimeEnvironment.getApplication()) =
        AndroidMatrixPlatformStore(context, legacyKeyCleaner = {})

    @Test
    fun deviceIdentityIsStablePerScopeAndRotatesWithDeletedCryptoState() = runBlocking {
        val store = store()
        fun scope(
            accountId: Long = 101,
            api: String = "https://api-device.test/",
            relationshipId: Long = 501,
        ) = MatrixStorageScope.create(
            accountId,
            api,
            "https://matrix-device.test/",
            relationshipId,
            1,
            "!couple:matrix-device.test",
        )
        val scope = scope()
        val otherAccount = scope(accountId = 102)
        val otherEnvironment = scope(api = "https://staging-device.test/")
        val otherRelationship = scope(relationshipId = 502)
        store.reconcileAccountData(currentAccountId = scope.accountId)

        val first = store.deviceId(scope)
        assertEquals(first, store.deviceId(scope))
        assertNotEquals(first, store.deviceId(otherAccount))
        assertNotEquals(first, store.deviceId(otherEnvironment))
        assertNotEquals(first, store.deviceId(otherRelationship))

        store.deleteAccountData(scope)
        val rotated = store.rotateDeviceId(scope)
        assertNotEquals(first, rotated)
        assertEquals(rotated, store.deviceId(scope))
    }

    @Test
    fun accountSweepDeletesOrphanedRelationshipScopesButPreservesSelectedScope() = runBlocking {
        val store = store()
        fun scope(accountId: Long = 301, relationshipId: Long) = MatrixStorageScope.create(
            accountId,
            "https://api-sweep.test/",
            "https://matrix-sweep.test/",
            relationshipId,
            1,
            "!couple-$relationshipId:matrix-sweep.test",
        )
        val selected = scope(relationshipId = 701)
        val orphaned = scope(relationshipId = 702)
        val otherAccount = scope(accountId = 302, relationshipId = 703)
        store.reconcileAccountData(currentAccountId = selected.accountId)
        val selectedId = store.deviceId(selected)
        val orphanedId = store.deviceId(orphaned)
        val otherAccountId = store.deviceId(otherAccount)

        store.deleteAllAccountData(selected.accountId, preserving = selected)

        assertEquals(selectedId, store.deviceId(selected))
        assertNotEquals(orphanedId, store.deviceId(orphaned))
        assertEquals(otherAccountId, store.deviceId(otherAccount))
    }

    @Test
    fun durableCleanupJournalSurvivesStoreRestartAndOverridesCurrentAccountPreservation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        fun scope(accountId: Long) = MatrixStorageScope.create(
            accountId,
            "https://api-journal.test/",
            "https://matrix-journal.test/",
            901,
            1,
            "!couple:matrix-journal.test",
        )
        val doomed = scope(401)
        val retained = scope(402)
        val firstStore = store(context)
        firstStore.reconcileAccountData(currentAccountId = doomed.accountId)
        val doomedId = firstStore.deviceId(doomed)
        val retainedId = firstStore.deviceId(retained)
        firstStore.recordAccountCleanupRequired(doomed.accountId)

        val restartedStore = store(context)
        restartedStore.reconcileAccountData(currentAccountId = doomed.accountId)

        assertNotEquals(doomedId, restartedStore.deviceId(doomed))
        assertNotEquals(retainedId, restartedStore.deviceId(retained))
    }

    @Test
    fun signedOutStartupSweepDeletesEveryDiscoveredMatrixAccount() = runBlocking {
        val store = store()
        fun scope(accountId: Long, relationshipId: Long) = MatrixStorageScope.create(
            accountId,
            "https://api-signed-out.test/",
            "https://matrix-signed-out.test/",
            relationshipId,
            1,
            "!couple-$relationshipId:matrix-signed-out.test",
        )
        val first = scope(501, 1001)
        val second = scope(502, 1002)
        store.reconcileAccountData(currentAccountId = first.accountId)
        val firstId = store.deviceId(first)
        val secondId = store.deviceId(second)

        store.reconcileAccountData(currentAccountId = null)

        assertNotEquals(firstId, store.deviceId(first))
        assertNotEquals(secondId, store.deviceId(second))
    }

    @Test
    fun constructorDefersLegacyCleanupUntilReconciliation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val names = listOf(
            "srisu-private-chat-v2.db",
            "srisu-private-chat-v2.db-wal",
            "srisu-private-chat-v2.db-shm",
            "srisu-private-chat-v2.db-journal",
        )
        context.getSharedPreferences("srisu-matrix-install", Context.MODE_PRIVATE)
            .edit().putBoolean("legacy-chat-v2-db-cleanup-v1", true).commit()
        val files = names.map { File(context.noBackupFilesDir, it).apply { writeText("synthetic") } }
        assertTrue(files.all(File::exists))

        val store = store(context)
        assertTrue(files.all(File::exists))
        store.reconcileAccountData(currentAccountId = null)

        assertFalse(files.any(File::exists))
    }

    @Test
    fun legacyCleanupFailureIsDeferredToReconciliation() {
        val context = RuntimeEnvironment.getApplication()
        val store = AndroidMatrixPlatformStore(context) { error("synthetic cleanup failure") }

        val failure = assertFailsWith<IllegalStateException> {
            runBlocking { store.reconcileAccountData(currentAccountId = null) }
        }

        assertEquals("synthetic cleanup failure", failure.message)
    }

    @Test
    fun retiredCoupleChatCleanupIsExactIdempotentAndDoesNotTrustItsMarker() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val scope = MatrixStorageScope.create(
            801,
            "https://api-legacy-cleanup.test/",
            "https://matrix-legacy-cleanup.test/",
            1801,
            1,
            "!couple:matrix-legacy-cleanup.test",
        )
        val firstStore = store(context)
        firstStore.reconcileAccountData(currentAccountId = scope.accountId)
        val matrixDeviceId = firstStore.deviceId(scope)
        val matrixSentinel = File(context.noBackupFilesDir, "SriSuMatrix/sentinel").apply {
            parentFile?.mkdirs()
            writeText("matrix")
        }
        val legacyRoot = File(context.noBackupFilesDir, "couple-chat-v1").apply {
            resolve("801/media/message.bin").apply {
                parentFile?.mkdirs()
                writeText("legacy")
            }
        }
        val neighbor = File(context.noBackupFilesDir, "couple-chat-v1-neighbor/sentinel").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }
        context.getSharedPreferences("srisu-matrix-install", Context.MODE_PRIVATE)
            .edit().putBoolean("legacy-couple-chat-v1-cleanup-v1", true).commit()

        val restarted = store(context)
        assertTrue(legacyRoot.exists())
        restarted.reconcileAccountData(currentAccountId = scope.accountId)

        assertFalse(legacyRoot.exists())
        assertTrue(neighbor.isFile)
        assertEquals("matrix", matrixSentinel.readText())
        assertEquals(matrixDeviceId, restarted.deviceId(scope))

        legacyRoot.resolve("801/records.sqlite").apply {
            parentFile?.mkdirs()
            writeText("restored")
        }
        val restartedAgain = store(context)
        restartedAgain.reconcileAccountData(currentAccountId = scope.accountId)
        assertFalse(legacyRoot.exists())
        assertTrue(neighbor.isFile)
        assertEquals(matrixDeviceId, restartedAgain.deviceId(scope))
    }

    @Test
    fun retiredCoupleChatKeyAliasMatcherRejectsNeighborsAndInvalidAccounts() {
        assertTrue(isLegacyCoupleChatKeyAlias("com.srisu.couplechat.v1.1"))
        assertTrue(isLegacyCoupleChatKeyAlias("com.srisu.couplechat.v1.9223372036854775807"))
        listOf(
            "com.srisu.couplechat.v1.0",
            "com.srisu.couplechat.v1.-1",
            "com.srisu.couplechat.v1.abc",
            "com.srisu.couplechat.v1.1.extra",
            "prefix.com.srisu.couplechat.v1.1",
            "com.srisu.couplechat.v2.1",
            "com.srisu.matrix.v1.1",
            "com.srisu.couplechat.v1.9223372036854775808",
        ).forEach { alias -> assertFalse(isLegacyCoupleChatKeyAlias(alias), alias) }
    }
}
