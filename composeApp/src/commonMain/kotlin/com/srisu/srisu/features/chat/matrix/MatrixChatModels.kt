package com.srisu.srisu.features.chat.matrix

import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import okio.ByteString.Companion.encodeUtf8
import com.srisu.srisu.features.couplechat.data.PreparedChatMedia
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile

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
    val attachment: MatrixChatAttachment? = null,
    val replyTo: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val ownReactionId: String? = null,
    val read: Boolean = false,
    val deleted: Boolean = false,
    val uploadProgress: Float? = null,
    val planReference: com.srisu.srisu.features.plans.data.PlanReference? = null,
    val challengeReference: com.srisu.srisu.features.challenges.data.ChallengeReference? = null,
    val sparkReference: com.srisu.srisu.features.sparks.data.SparkReference? = null,
    val momentReference: com.srisu.srisu.features.moments.data.MomentReference? = null,
) {
    /** List identity that stays stable while a local send is acknowledged by the server. */
    val stableKey: String get() = planReference?.let { "plan:${it.couple}:${it.id}:$senderMatrixId" } ?: challengeReference?.let { "challenge:${it.couple}:${it.id}:$senderMatrixId" } ?: sparkReference?.let { "spark:${it.couple}:${it.id}:$senderMatrixId" } ?: transactionId?.let { "txn:$it" } ?: id
}

/** File keys are decrypted Matrix event content, held in memory; never route arguments. */
data class MatrixChatAttachment(
    val kind: String, val mime: String, val file: EncryptedFile,
    val width: Int = 0, val height: Int = 0, val durationMs: Long = 0,
    val thumbnail: EncryptedFile? = null,
)

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
    val sparkBackupPending: Boolean = false,
    val syncing: Boolean = false,
    val sending: Boolean = false,
    val composerError: String? = null,
    val accountId: Long? = null,
    val hasOlder: Boolean = false,
    /** Failed reaction/redaction outbox operations; never silently discard these hidden events. */
    val failedChangeIds: List<String> = emptyList(),
)

/**
 * Opaque screen-owner identity for the singleton Matrix repository.
 *
 * Navigation transitions can briefly keep two ViewModels for the same room alive.
 * Only the most recent owner may close or retry that shared room session.
 */
class MatrixChatRoomOwner

interface MatrixChatRepository {
    fun observeRoom(roomId: String): Flow<MatrixChatRoomState>
    fun openRoom(roomId: String, owner: MatrixChatRoomOwner)
    fun closeRoom(roomId: String, owner: MatrixChatRoomOwner)
    suspend fun sendText(roomId: String, text: String): Result<String>
    suspend fun encryptSparkAnswer(session: com.srisu.srisu.features.sparks.data.SparkSession, text: String): Result<kotlinx.serialization.json.JsonObject> = Result.failure(UnsupportedOperationException())
    suspend fun decryptSparkAnswer(session: com.srisu.srisu.features.sparks.data.SparkSession, answer: com.srisu.srisu.features.sparks.data.SparkAnswer): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun sparkRecoveryStatus(session: com.srisu.srisu.features.sparks.data.SparkSession): Result<SparkRecoveryStatus> = Result.success(SparkRecoveryStatus.NotSetUp)
    suspend fun enableSparkRecovery(session: com.srisu.srisu.features.sparks.data.SparkSession, key: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun resumeSparkRecovery(session: com.srisu.srisu.features.sparks.data.SparkSession): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun unlockSparkRecovery(session: com.srisu.srisu.features.sparks.data.SparkSession, key: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun backUpSparkAnswers(session: com.srisu.srisu.features.sparks.data.SparkSession): Result<Int> = Result.failure(UnsupportedOperationException())
    suspend fun publishPlanCard(plan: com.srisu.srisu.features.plans.data.Plan): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun publishChallengeCard(session: com.srisu.srisu.features.challenges.data.ChallengeSession): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun publishSparkCard(session: com.srisu.srisu.features.sparks.data.SparkSession): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun retryMessage(roomId: String, transactionId: String): Result<Unit>
    suspend fun sendMomentReply(roomId: String, text: String, reference: com.srisu.srisu.features.moments.data.MomentReference): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun sendReply(roomId: String, text: String, replyTo: String): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun sendMedia(roomId: String, media: PreparedChatMedia, caption: String, replyTo: String?): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun react(roomId: String, messageId: String, reaction: String?): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun attachment(roomId: String, messageId: String, thumbnail: Boolean = false): Result<ByteArray> = Result.failure(UnsupportedOperationException())
    suspend fun markRead(roomId: String, messageId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    fun loadOlder(roomId: String) {}
    fun retryRoom(roomId: String, owner: MatrixChatRoomOwner)
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
