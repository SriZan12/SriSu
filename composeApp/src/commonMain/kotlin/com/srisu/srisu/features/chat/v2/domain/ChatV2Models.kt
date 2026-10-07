package com.srisu.srisu.features.chat.v2.domain

import com.srisu.srisu.features.chat.v2.data.local.ChatMessageEntity
import com.srisu.srisu.features.chat.v2.data.local.ChatStorageStates

data class ChatV2Scope(
    val environmentKey: String,
    val accountId: Long,
    val roomId: String,
    val sessionGeneration: Long,
)

enum class ChatDeliveryState {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED;

    companion object {
        fun fromStorage(value: String): ChatDeliveryState = when (value) {
            ChatStorageStates.PENDING -> PENDING
            ChatStorageStates.SENDING -> SENDING
            ChatStorageStates.SENT -> SENT
            ChatStorageStates.DELIVERED -> DELIVERED
            ChatStorageStates.READ -> READ
            ChatStorageStates.FAILED -> FAILED
            else -> FAILED
        }
    }
}

data class ChatV2Message(
    val id: String,
    val operationId: String?,
    val sequence: Long,
    val revision: Long,
    val senderId: Long?,
    val contentKind: String,
    val replyToId: String?,
    val isTombstone: Boolean,
    val createdAt: String?,
    val editedAt: String?,
    val deliveryState: ChatDeliveryState,
)

internal fun ChatMessageEntity.toDomain() = ChatV2Message(
    id = messageId,
    operationId = operationId,
    sequence = sequence,
    revision = revision,
    senderId = senderId,
    contentKind = contentKind,
    replyToId = replyToId,
    isTombstone = isTombstone,
    createdAt = createdAt,
    editedAt = editedAt,
    deliveryState = ChatDeliveryState.fromStorage(deliveryState),
)

sealed interface SecureChatAvailability {
    data object Loading : SecureChatAvailability
    data class Ready(val protocolId: String) : SecureChatAvailability
    data class Unavailable(val reason: Reason) : SecureChatAvailability
    data class Error(val message: String) : SecureChatAvailability

    enum class Reason {
        SIGN_IN_REQUIRED,
        ACCESS_REVOKED,
        DEVICE_SESSION_REQUIRED,
        CRYPTO_ADAPTER_REQUIRED,
        SERVER_DISABLED,
        UNSUPPORTED_PROTOCOL,
    }
}

data class ChatV2RoomState(
    val roomId: String,
    val messages: List<ChatV2Message> = emptyList(),
    val availability: SecureChatAvailability = SecureChatAvailability.Loading,
    val syncing: Boolean = false,
    val queuedOperationCount: Int = 0,
    val errorMessage: String? = null,
)

/**
 * The current Room database is intentionally not an encrypted-at-rest draft store.
 * Drafts therefore remain ephemeral and are not represented by a database entity.
 */
object ChatDraftPolicy {
    const val PERSIST_PLAINTEXT_DRAFTS: Boolean = false
    const val REASON: String = "Protected draft storage is not available."
}
