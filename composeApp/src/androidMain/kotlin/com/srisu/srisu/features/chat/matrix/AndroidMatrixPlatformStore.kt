package com.srisu.srisu.features.chat.matrix

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import de.connect2x.trixnity.client.store.repository.room.room
import java.io.File
import java.security.KeyStore
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

class AndroidMatrixPlatformStore internal constructor(
    context: Context,
    private val legacyKeyCleaner: (() -> Unit)? = null,
) : MatrixPlatformStore {
    private val appContext = context.applicationContext
    private val root: File by lazy { File(appContext.noBackupFilesDir, "SriSuMatrix") }
    private val preferences by lazy {
        appContext.getSharedPreferences("srisu-matrix-install", Context.MODE_PRIVATE)
    }
    private val deviceIdLock = Any()
    private val cleanupLock = Any()

    override fun deviceId(scope: MatrixStorageScope): String = synchronized(deviceIdLock) {
        readDeviceId(scope) ?: persistFreshDeviceId(scope)
    }

    override fun repositories(scope: MatrixStorageScope): RepositoriesModule {
        val directory = accountDirectory(scope)
        val database = File(directory, "trixnity-room.db")
        return RepositoriesModule.room(
            Room.databaseBuilder<TrixnityRoomDatabase>(appContext, database.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
        )
    }

    override fun media(scope: MatrixStorageScope): MediaStoreModule {
        val directory = File(accountDirectory(scope), "media")
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create Matrix media storage." }
        return MediaStoreModule.okio(directory.absolutePath.toPath())
    }

    override suspend fun deleteAccountData(scope: MatrixStorageScope) = withContext(Dispatchers.IO) {
        val directory = accountDirectory(scope, create = false)
        if (directory.exists()) check(directory.deleteRecursively()) { "Unable to delete Matrix account storage." }
    }

    override suspend fun deleteAllAccountData(
        accountId: Long,
        preserving: MatrixStorageScope?,
    ) = withContext(Dispatchers.IO) {
        synchronized(cleanupLock) { deleteAllAccountDataLocked(accountId, preserving) }
    }

    override fun recordAccountCleanupRequired(accountId: Long) = synchronized(cleanupLock) {
        require(accountId > 0)
        val marker = AtomicFile(cleanupMarker(accountId))
        if (marker.baseFile.isFile) return@synchronized
        val output = marker.startWrite()
        try {
            output.write(accountId.toString().toByteArray(Charsets.UTF_8))
            marker.finishWrite(output)
        } catch (failure: Throwable) {
            marker.failWrite(output)
            throw failure
        }
    }

    override suspend fun reconcileAccountData(currentAccountId: Long?): Unit = withContext(Dispatchers.IO) {
        require(currentAccountId == null || currentAccountId > 0)
        synchronized(cleanupLock) {
            // This store is created eagerly by Koin. Keep every fallible filesystem
            // migration in this suspend reconciliation path so startup can surface a
            // retryable cleanup-blocked state instead of crashing the application.
            prepareStorageRoot()
            cleanupLegacyChatV2Database()
            cleanupLegacyCoupleChatV1()
            val entries = checkNotNull(root.listFiles()) { "Unable to enumerate Matrix storage." }
            val markedAccounts = entries.mapNotNull { entry ->
                CLEANUP_MARKER_PATTERN.matchEntire(entry.name)?.groupValues?.get(1)?.toLongOrNull()
            }.toSet()
            val discoveredAccounts = entries
                .filter { it.isDirectory && it.name.matches(SCOPE_DIRECTORY_PATTERN) }
                .flatMap { scopeDirectory ->
                    checkNotNull(scopeDirectory.listFiles()) { "Unable to enumerate Matrix scope storage." }
                        .mapNotNull { accountDirectory ->
                            ACCOUNT_DIRECTORY_PATTERN.matchEntire(accountDirectory.name)
                                ?.groupValues?.get(1)?.toLongOrNull()
                        }
                }
                .filter { it != currentAccountId }
                .toSet()
            var firstFailure: Throwable? = null
            (markedAccounts + discoveredAccounts).forEach { accountId ->
                try {
                    deleteAllAccountDataLocked(accountId, preserving = null)
                    cleanupMarkerVariants(accountId).forEach { marker ->
                        if (marker.exists()) check(marker.delete()) { "Unable to clear Matrix cleanup journal." }
                    }
                } catch (failure: Throwable) {
                    if (firstFailure == null) firstFailure = failure
                }
            }
            firstFailure?.let { throw it }
            Unit
        }
    }

    override suspend fun rotateDeviceId(scope: MatrixStorageScope): String = withContext(Dispatchers.IO) {
        synchronized(deviceIdLock) {
            persistFreshDeviceId(scope)
        }
    }

    private fun accountDirectory(scope: MatrixStorageScope, create: Boolean = true): File {
        val environment = File(root, "scope-${scope.namespace}")
        val account = File(environment, "account-${scope.accountId}")
        if (create) check(account.isDirectory || account.mkdirs()) { "Unable to create Matrix account storage." }
        return account
    }

    private fun deviceIdFile(scope: MatrixStorageScope) = AtomicFile(File(accountDirectory(scope), DEVICE_ID_FILE))

    private fun deleteAllAccountDataLocked(accountId: Long, preserving: MatrixStorageScope?) {
        require(accountId > 0)
        require(preserving == null || preserving.accountId == accountId)
        checkNotNull(root.listFiles()) { "Unable to enumerate Matrix storage." }
            .filter { it.isDirectory && it.name.matches(SCOPE_DIRECTORY_PATTERN) }
            .forEach { scopeDirectory ->
                if (scopeDirectory.name != preserving?.let { "scope-${it.namespace}" }) {
                    val account = File(scopeDirectory, "account-$accountId")
                    if (account.exists()) check(account.deleteRecursively()) {
                        "Unable to delete Matrix account storage."
                    }
                    if (checkNotNull(scopeDirectory.listFiles()) {
                            "Unable to enumerate Matrix scope storage."
                        }.isEmpty()
                    ) {
                        check(scopeDirectory.delete()) { "Unable to delete empty Matrix scope." }
                    }
                }
            }
    }

    private fun cleanupMarker(accountId: Long) = File(root, "cleanup-account-$accountId.pending")

    private fun cleanupMarkerVariants(accountId: Long): List<File> {
        val base = cleanupMarker(accountId)
        return listOf(base, File(base.path + ".new"), File(base.path + ".bak"))
    }

    private fun readDeviceId(scope: MatrixStorageScope): String? = runCatching {
        deviceIdFile(scope).openRead().bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
            .takeIf { it.matches(DEVICE_ID_PATTERN) }
    }.getOrNull()

    private fun persistFreshDeviceId(scope: MatrixStorageScope): String {
        val value = "SRISU_" + UUID.randomUUID().toString().replace("-", "")
        val deviceIdFile = deviceIdFile(scope)
        val output = deviceIdFile.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            deviceIdFile.finishWrite(output)
        } catch (failure: Throwable) {
            deviceIdFile.failWrite(output)
            throw failure
        }
        return value
    }

    /**
     * The retired custom encrypted-chat runtime stored its account records and
     * media below this exact directory and its wrapping keys under this exact
     * AndroidKeyStore alias format. There is no compatible import into Matrix,
     * so remove both before opening the replacement store. The preference is an
     * audit marker only: exact targets are checked on every start so a restored
     * or partially deleted legacy store cannot survive an earlier marker.
     */
    private fun cleanupLegacyCoupleChatV1() {
        (legacyKeyCleaner ?: ::cleanupLegacyCoupleChatKeys).invoke()

        val legacyRoot = File(appContext.noBackupFilesDir, LEGACY_COUPLE_CHAT_DIRECTORY)
        if (legacyRoot.exists()) {
            check(legacyRoot.deleteRecursively()) { "Unable to remove legacy couple-chat storage." }
        }
        check(preferences.edit().putBoolean(LEGACY_COUPLE_CHAT_CLEANUP_KEY, true).commit())
    }

    private fun cleanupLegacyCoupleChatKeys() {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        Collections.list(keyStore.aliases())
            .filter(::isLegacyCoupleChatKeyAlias)
            .forEach { alias ->
                keyStore.deleteEntry(alias)
                check(!keyStore.containsAlias(alias)) { "Unable to remove legacy couple-chat key." }
            }
    }

    /**
     * Builds before this format marker could persist Matrix authentication in Room.
     * Wipe that exact root once; the marker lives with the protected data instead of
     * independently restorable preferences.
     */
    private fun prepareStorageRoot() {
        val marker = File(root, STORAGE_FORMAT_MARKER)
        if (root.exists() && !marker.isFile) {
            check(root.deleteRecursively()) { "Unable to remove the previous Matrix storage format." }
        }
        check(root.isDirectory || root.mkdirs()) { "Unable to create Matrix storage." }
        check(marker.isFile || marker.createNewFile()) { "Unable to mark the Matrix storage format." }
    }

    /** One exact, versioned deletion; never scans or deletes outside noBackupFilesDir. */
    private fun cleanupLegacyChatV2Database() {
        val names = listOf(
            "srisu-private-chat-v2.db",
            "srisu-private-chat-v2.db-wal",
            "srisu-private-chat-v2.db-shm",
            "srisu-private-chat-v2.db-journal",
        )
        val results = names.map { name ->
            val file = File(appContext.noBackupFilesDir, name)
            !file.exists() || file.delete()
        }
        check(results.all { it }) { "Unable to remove legacy private-chat storage." }
        check(preferences.edit().putBoolean(LEGACY_CLEANUP_KEY, true).commit())
    }

    private companion object {
        const val DEVICE_ID_FILE = "matrix-device-id-v1"
        const val STORAGE_FORMAT_MARKER = "matrix-storage-format-v2-volatile-auth"
        const val LEGACY_CLEANUP_KEY = "legacy-chat-v2-db-cleanup-v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val LEGACY_COUPLE_CHAT_DIRECTORY = "couple-chat-v1"
        const val LEGACY_COUPLE_CHAT_CLEANUP_KEY = "legacy-couple-chat-v1-cleanup-v1"
        val DEVICE_ID_PATTERN = Regex("SRISU_[A-Fa-f0-9]{32}")
        val SCOPE_DIRECTORY_PATTERN = Regex("scope-[0-9a-f]{32}")
        val ACCOUNT_DIRECTORY_PATTERN = Regex("account-([1-9][0-9]*)")
        val CLEANUP_MARKER_PATTERN = Regex("cleanup-account-([1-9][0-9]*)\\.pending(?:\\.(?:new|bak))?")
    }
}

private val LEGACY_COUPLE_CHAT_KEY_ALIAS =
    Regex("com\\.srisu\\.couplechat\\.v1\\.([1-9][0-9]{0,18})")

internal fun isLegacyCoupleChatKeyAlias(alias: String): Boolean =
    LEGACY_COUPLE_CHAT_KEY_ALIAS.matchEntire(alias)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
        ?.let { it > 0 } == true
