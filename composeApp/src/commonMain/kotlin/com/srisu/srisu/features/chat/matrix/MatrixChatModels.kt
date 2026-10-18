package com.srisu.srisu.features.chat.matrix

import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import okio.ByteString.Companion.encodeUtf8

enum class MatrixMessageDelivery { SENT, SENDING, FAILED }

/** Leaves headroom under Matrix's event-size ceiling for Megolm/base64 and JSON envelopes. */
internal const val MATRIX_TEXT_MAX_PLAINTEXT_BYTES = 32 * 1024

data class MatrixChatMessage(
    val id: String,
    val body: String,
    val senderMatrixId: String,
    val mine: Boolean,
    val timestampMillis: Long,
    val delivery: MatrixMessageDelivery,
    val retryTransactionId: String? = null,
    /** Encrypted event exists but its content could not be safely rendered. */
    val unavailable: Boolean = false,
    /** Matrix client transaction ID for this device's own sends; survives the outbox-to-timeline handoff. */
    val transactionId: String? = null,
) {
    /** List identity that stays stable while a local send is acknowledged by the server. */
    val stableKey: String get() = transactionId?.let { "txn:$it" } ?: id
}

sealed interface MatrixChatAvailability {
    data object Loading : MatrixChatAvailability
    data class Ready(val protocolId: String = MATRIX_PROTOCOL_ID) : MatrixChatAvailability
    data class Offline(val message: String) : MatrixChatAvailability
    data class Unavailable(val message: String) : MatrixChatAvailability
    data class Error(val message: String, val retryable: Boolean = true) : MatrixChatAvailability
}

data class MatrixChatRoomState(
    /** Stable Django ChatRoom UUID used by navigation and bootstrap only. */
    val roomId: String,
    val messages: List<MatrixChatMessage> = emptyList(),
    val availability: MatrixChatAvailability = MatrixChatAvailability.Loading,
    val syncing: Boolean = false,
    val sending: Boolean = false,
    val composerError: String? = null,
)

interface MatrixChatRepository {
    fun observeRoom(roomId: String): Flow<MatrixChatRoomState>
    fun openRoom(roomId: String)
    fun closeRoom(roomId: String)
    suspend fun sendText(roomId: String, text: String): Result<String>
    suspend fun retryMessage(roomId: String, transactionId: String): Result<Unit>
    fun retryRoom(roomId: String)
    fun close()
}

/** Platform-owned protected paths and stable, non-secret per-install device identity. */
data class MatrixStorageScope internal constructor(
    val accountId: Long,
    val namespace: String,
) {
    init {
        require(accountId > 0)
        require(namespace.matches(Regex("[0-9a-f]{32}")))
    }

    companion object {
        fun create(
            accountId: Long,
            apiBaseUrl: String,
            homeserverUrl: String,
            relationshipId: Long,
            membershipEpoch: Long,
            matrixRoomId: String,
        ): MatrixStorageScope {
            require(relationshipId > 0)
            require(membershipEpoch > 0)
            require(matrixRoomId.isNotBlank())
            val apiOrigin = Url(apiBaseUrl).let { "${it.protocol.name}://${it.host}:${it.port}" }
            val matrixOrigin = Url(homeserverUrl).let { "${it.protocol.name}://${it.host}:${it.port}" }
            // Relationship identity is part of the protected store boundary. A same-account
            // unlink/relink must never reopen an old room database and drain its durable outbox.
            val namespace = listOf(
                apiOrigin,
                matrixOrigin,
                relationshipId.toString(),
                membershipEpoch.toString(),
                matrixRoomId,
            ).joinToString("|").encodeUtf8().sha256().hex().take(32)
            return MatrixStorageScope(accountId, namespace)
        }
    }
}

interface MatrixPlatformStore {
    fun deviceId(scope: MatrixStorageScope): String
    fun repositories(scope: MatrixStorageScope): RepositoriesModule
    fun media(scope: MatrixStorageScope): MediaStoreModule
    suspend fun deleteAccountData(scope: MatrixStorageScope)
    /** Deletes exact Matrix account directories across known relationship scopes. */
    suspend fun deleteAllAccountData(accountId: Long, preserving: MatrixStorageScope? = null)
    /** Durably records wipe intent before asynchronous logout/account cleanup. */
    fun recordAccountCleanupRequired(accountId: Long)
    /** Replays cleanup journals and removes foreign accounts discovered after a crash. */
    suspend fun reconcileAccountData(currentAccountId: Long?)
    /** Persist a fresh device id before another Matrix login can occur. */
    suspend fun rotateDeviceId(scope: MatrixStorageScope): String
}
