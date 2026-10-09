@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.srisu.srisu.features.chat.matrix

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import de.connect2x.trixnity.client.store.repository.room.room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile

class IosMatrixPlatformStore : MatrixPlatformStore {
    private val fileManager = NSFileManager.defaultManager
    private val preferences = NSUserDefaults.standardUserDefaults
    private val cleanupLock = SynchronizedObject()
    private val appSupport = requireNotNull(
        fileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    ) { "Application support directory unavailable." }
    private val root = prepareStorageRoot(requireNotNull(appSupport.URLByAppendingPathComponent("SriSuMatrix")))

    init {
        cleanupLegacyChatV2Database()
    }

    override fun deviceId(scope: MatrixStorageScope): String =
        readDeviceId(scope) ?: persistFreshDeviceId(scope)

    override fun repositories(scope: MatrixStorageScope): RepositoriesModule {
        val directory = accountDirectory(scope)
        return RepositoriesModule.room(
            Room.databaseBuilder<TrixnityRoomDatabase>(name = requireNotNull(directory.path) + "/trixnity-room.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
        )
    }

    override fun media(scope: MatrixStorageScope): MediaStoreModule =
        MediaStoreModule.okio((requireNotNull(accountDirectory(scope).path) + "/media").toPath())

    override suspend fun deleteAccountData(scope: MatrixStorageScope) = withContext(Dispatchers.IO) {
        val directory = accountDirectory(scope, create = false)
        if (fileManager.fileExistsAtPath(requireNotNull(directory.path))) {
            check(fileManager.removeItemAtURL(directory, null)) { "Unable to delete Matrix account storage." }
        }
    }

    override suspend fun deleteAllAccountData(
        accountId: Long,
        preserving: MatrixStorageScope?,
    ) = withContext(Dispatchers.IO) {
        synchronized(cleanupLock) { deleteAllAccountDataLocked(accountId, preserving) }
    }

    override fun recordAccountCleanupRequired(accountId: Long) = synchronized(cleanupLock) {
        require(accountId > 0)
        val destination = cleanupMarkerPath(accountId).toPath()
        if (FileSystem.SYSTEM.exists(destination)) return@synchronized
        val temporary = (cleanupMarkerPath(accountId) + CLEANUP_MARKER_TEMP_SUFFIX).toPath()
        runCatching { FileSystem.SYSTEM.delete(temporary) }
        try {
            FileSystem.SYSTEM.write(temporary) { writeUtf8(accountId.toString()); flush() }
            FileSystem.SYSTEM.atomicMove(temporary, destination)
        } finally {
            runCatching { FileSystem.SYSTEM.delete(temporary) }
        }
    }

    override suspend fun reconcileAccountData(currentAccountId: Long?): Unit = withContext(Dispatchers.IO) {
        require(currentAccountId == null || currentAccountId > 0)
        synchronized(cleanupLock) {
            val entries = rootEntries()
            val markedAccounts = entries.mapNotNull { entry ->
                CLEANUP_MARKER_PATTERN.matchEntire(entry)?.groupValues?.get(1)?.toLongOrNull()
            }.toSet()
            val discoveredAccounts = entries
                .filter { it.matches(SCOPE_DIRECTORY_PATTERN) }
                .flatMap { scopeName ->
                    val scopeDirectory = requireNotNull(root.URLByAppendingPathComponent(scopeName))
                    checkNotNull(fileManager.contentsOfDirectoryAtPath(requireNotNull(scopeDirectory.path), null)) {
                        "Unable to enumerate Matrix scope storage."
                    }.mapNotNull { accountDirectory ->
                        (accountDirectory as? String)?.let(ACCOUNT_DIRECTORY_PATTERN::matchEntire)
                            ?.groupValues?.get(1)?.toLongOrNull()
                    }
                }
                .filter { it != currentAccountId }
                .toSet()
            var firstFailure: Throwable? = null
            (markedAccounts + discoveredAccounts).forEach { accountId ->
                try {
                    deleteAllAccountDataLocked(accountId, preserving = null)
                    cleanupMarkerVariants(accountId).forEach { markerPath ->
                        if (fileManager.fileExistsAtPath(markerPath)) {
                            check(fileManager.removeItemAtPath(markerPath, null)) {
                                "Unable to clear Matrix cleanup journal."
                            }
                        }
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
        persistFreshDeviceId(scope)
    }

    private fun accountDirectory(scope: MatrixStorageScope, create: Boolean = true): NSURL {
        val environment = requireNotNull(root.URLByAppendingPathComponent("scope-${scope.namespace}"))
        val account = requireNotNull(environment.URLByAppendingPathComponent("account-${scope.accountId}"))
        return if (create) protectedDirectory(account) else account
    }

    private fun deviceIdPath(scope: MatrixStorageScope): String =
        requireNotNull(accountDirectory(scope).path) + "/$DEVICE_ID_FILE"

    private fun deleteAllAccountDataLocked(accountId: Long, preserving: MatrixStorageScope?) {
        require(accountId > 0)
        require(preserving == null || preserving.accountId == accountId)
        rootEntries()
            .filter { it.matches(SCOPE_DIRECTORY_PATTERN) }
            .forEach { scopeName ->
                if (scopeName != preserving?.let { "scope-${it.namespace}" }) {
                    val scopeDirectory = requireNotNull(root.URLByAppendingPathComponent(scopeName))
                    val account = requireNotNull(scopeDirectory.URLByAppendingPathComponent("account-$accountId"))
                    if (fileManager.fileExistsAtPath(requireNotNull(account.path))) {
                        check(fileManager.removeItemAtURL(account, null)) {
                            "Unable to delete Matrix account storage."
                        }
                    }
                    val remaining = checkNotNull(
                        fileManager.contentsOfDirectoryAtPath(requireNotNull(scopeDirectory.path), null)
                    ) { "Unable to enumerate Matrix scope storage." }
                    if (remaining.isEmpty()) {
                        check(fileManager.removeItemAtURL(scopeDirectory, null)) {
                            "Unable to delete empty Matrix scope."
                        }
                    }
                }
            }
    }

    private fun rootEntries(): List<String> = checkNotNull(
        fileManager.contentsOfDirectoryAtPath(requireNotNull(root.path), null)
    ) { "Unable to enumerate Matrix storage." }.mapNotNull { it as? String }

    private fun cleanupMarkerPath(accountId: Long): String =
        requireNotNull(root.path) + "/cleanup-account-$accountId.pending"

    private fun cleanupMarkerVariants(accountId: Long): List<String> {
        val base = cleanupMarkerPath(accountId)
        return listOf(base, base + CLEANUP_MARKER_TEMP_SUFFIX)
    }

    private fun readDeviceId(scope: MatrixStorageScope): String? = runCatching {
        NSString.stringWithContentsOfFile(deviceIdPath(scope), NSUTF8StringEncoding, null)
            ?.trim()
            ?.takeIf { it.matches(DEVICE_ID_PATTERN) }
    }.getOrNull()

    private fun persistFreshDeviceId(scope: MatrixStorageScope): String {
        val value = "SRISU_" + NSUUID().UUIDString.replace("-", "")
        val destination = deviceIdPath(scope).toPath()
        val temporary = (deviceIdPath(scope) + DEVICE_ID_TEMP_SUFFIX).toPath()
        // Same-directory atomic replacement means a crash leaves either the old
        // complete identity or the new complete identity, never a truncated ID
        // alongside an otherwise valid crypto/account database.
        runCatching { FileSystem.SYSTEM.delete(temporary) }
        try {
            FileSystem.SYSTEM.write(temporary) {
                writeUtf8(value)
                flush()
            }
            FileSystem.SYSTEM.atomicMove(temporary, destination)
        } finally {
            runCatching { FileSystem.SYSTEM.delete(temporary) }
        }
        check(root.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
            "Unable to exclude Matrix identity from backup."
        }
        return value
    }

    private fun protectedDirectory(url: NSURL): NSURL {
        if (!fileManager.fileExistsAtPath(requireNotNull(url.path))) {
            check(fileManager.createDirectoryAtURL(
                url,
                withIntermediateDirectories = true,
                attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
                error = null,
            )) { "Unable to create protected Matrix storage." }
        }
        check(url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
            "Unable to exclude Matrix storage from backup."
        }
        return url
    }

    /** Wipes only the pre-volatile-auth Matrix root, keyed by a co-located format marker. */
    private fun prepareStorageRoot(url: NSURL): NSURL {
        val marker = requireNotNull(url.URLByAppendingPathComponent(STORAGE_FORMAT_MARKER))
        val rootPath = requireNotNull(url.path)
        if (fileManager.fileExistsAtPath(rootPath) &&
            !fileManager.fileExistsAtPath(requireNotNull(marker.path))
        ) {
            check(fileManager.removeItemAtURL(url, null)) {
                "Unable to remove the previous Matrix storage format."
            }
        }
        val protected = protectedDirectory(url)
        if (!fileManager.fileExistsAtPath(requireNotNull(marker.path))) {
            FileSystem.SYSTEM.write(requireNotNull(marker.path).toPath()) { writeUtf8("2") }
        }
        return protected
    }

    /** One exact, versioned deletion; never scans outside Application Support/SriSuChatV2. */
    private fun cleanupLegacyChatV2Database() {
        val legacy = requireNotNull(appSupport.URLByAppendingPathComponent("SriSuChatV2"))
        val names = listOf(
            "srisu-private-chat-v2.db",
            "srisu-private-chat-v2.db-wal",
            "srisu-private-chat-v2.db-shm",
            "srisu-private-chat-v2.db-journal",
        )
        val results = names.map { name ->
            val file = requireNotNull(legacy.URLByAppendingPathComponent(name))
            !fileManager.fileExistsAtPath(requireNotNull(file.path)) || fileManager.removeItemAtURL(file, null)
        }
        check(results.all { it }) { "Unable to remove legacy private-chat storage." }
        preferences.setBool(true, forKey = LEGACY_CLEANUP_KEY)
    }

    private companion object {
        const val DEVICE_ID_FILE = "matrix-device-id-v1"
        const val DEVICE_ID_TEMP_SUFFIX = ".tmp"
        const val CLEANUP_MARKER_TEMP_SUFFIX = ".tmp"
        const val STORAGE_FORMAT_MARKER = "matrix-storage-format-v2-volatile-auth"
        const val LEGACY_CLEANUP_KEY = "srisu.legacy-chat-v2-db-cleanup-v1"
        val DEVICE_ID_PATTERN = Regex("SRISU_[A-Fa-f0-9]{32}")
        val SCOPE_DIRECTORY_PATTERN = Regex("scope-[0-9a-f]{32}")
        val ACCOUNT_DIRECTORY_PATTERN = Regex("account-([1-9][0-9]*)")
        val CLEANUP_MARKER_PATTERN = Regex("cleanup-account-([1-9][0-9]*)\\.pending(?:\\.tmp)?")
    }
}
