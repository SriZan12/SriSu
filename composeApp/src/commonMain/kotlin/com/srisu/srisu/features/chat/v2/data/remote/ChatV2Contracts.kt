package com.srisu.srisu.features.chat.v2.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

object ChatV2Routes {
    const val CONTRACT_HEADER = "X-SriSu-Chat-Contract"
    const val CONTRACT_VERSION = "chat-2"
    const val CAPABILITIES = "api/chat/v2/capabilities/"

    fun messages(roomId: String) = "api/chat/v2/rooms/${validatedRoomId(roomId)}/messages/"
    fun changes(roomId: String) = "api/chat/v2/rooms/${validatedRoomId(roomId)}/changes/"
    fun operations(roomId: String) = "api/chat/v2/rooms/${validatedRoomId(roomId)}/operations/"
    fun receipts(roomId: String) = "api/chat/v2/rooms/${validatedRoomId(roomId)}/receipts/"

    fun validatedRoomId(roomId: String): String {
        require(roomId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid room identifier" }
        return roomId.lowercase()
    }
}

@Serializable
data class ChatV2CapabilitiesDto(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("encrypted_writes_enabled") val encryptedWritesEnabled: Boolean = false,
    @SerialName("protocol_status") val protocolStatus: String = "adapter_required",
    @SerialName("max_message_bytes") val maxMessageBytes: Long? = null,
    @SerialName("max_envelope_bytes") val maxEnvelopeBytes: Long? = null,
    @SerialName("max_attachment_bytes") val maxAttachmentBytes: Long? = null,
    @SerialName("supported_kinds") val supportedKinds: List<String> = emptyList(),
    /** Defaults closed so a legacy token never queues a mutation the server must reject. */
    @SerialName("requires_device_session") val requiresDeviceSession: Boolean = true,
)

@Serializable
data class ChatV2ReactionDto(
    @SerialName("user_id") val userId: Long,
    val revision: Long,
    val envelope: JsonObject? = null,
    @SerialName("is_removed") val isRemoved: Boolean = false,
)

@Serializable
data class ChatV2MessageDto(
    val id: Long,
    /** Present for client-created messages so history/change sync can close a lost ACK. */
    @SerialName("operation_id") val operationId: String? = null,
    val sequence: Long? = null,
    val revision: Long,
    @SerialName("sender_id") val senderId: Long,
    @SerialName("content_kind") val contentKind: String,
    val envelope: JsonObject? = null,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("attachment_ids") val attachmentIds: List<String> = emptyList(),
    val reactions: List<ChatV2ReactionDto> = emptyList(),
    @SerialName("is_tombstone") val isTombstone: Boolean = false,
    @SerialName("hidden_for_viewer") val hiddenForViewer: Boolean = false,
    @SerialName("legacy_plaintext") val legacyPlaintext: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
)

@Serializable
data class ChatV2MessagesPageDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("high_watermark") val highWatermark: Long,
    /** The server sends newest first. Mapping reverses this before persistence. */
    val messages: List<ChatV2MessageDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_before_sequence") val nextBeforeSequence: Long? = null,
)

@Serializable
data class ChatV2ChangeDto(
    val sequence: Long,
    val kind: String,
    @SerialName("message_id") val messageId: Long? = null,
    @SerialName("message_revision") val messageRevision: Long? = null,
    @SerialName("actor_id") val actorId: Long? = null,
    @SerialName("created_at") val createdAt: String,
    val message: ChatV2MessageDto? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ChatV2ChangesPageDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("high_watermark") val highWatermark: Long,
    /** The server contract guarantees ascending order; clients still sort and dedupe defensively. */
    val changes: List<ChatV2ChangeDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_after_sequence") val nextAfterSequence: Long? = null,
)

@Serializable
data class ChatV2SendMessageRequest(
    @SerialName("operation_id") val operationId: String,
    @SerialName("content_kind") val contentKind: String,
    val envelope: JsonObject,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("attachment_ids") val attachmentIds: List<String> = emptyList(),
)

@Serializable
data class ChatV2OperationRequest(
    @SerialName("operation_id") val operationId: String,
    val action: String,
    @SerialName("message_id") val messageId: Long,
    @SerialName("expected_revision") val expectedRevision: Long? = null,
    val envelope: JsonObject? = null,
)

@Serializable
data class ChatV2OperationResultDto(
    @SerialName("operation_id") val operationId: String,
    val replayed: Boolean = false,
    @SerialName("change_sequence") val changeSequence: Long? = null,
    val message: ChatV2MessageDto,
)

@Serializable
data class ChatV2ReceiptRequest(
    @SerialName("operation_id") val operationId: String,
    @SerialName("delivered_through") val deliveredThrough: Long,
    @SerialName("read_through") val readThrough: Long,
)

@Serializable
data class ChatV2ReceiptResultDto(
    @SerialName("operation_id") val operationId: String,
    val replayed: Boolean = false,
    @SerialName("room_id") val roomId: String,
    @SerialName("delivered_through") val deliveredThrough: Long? = null,
    @SerialName("read_through") val readThrough: Long? = null,
    @SerialName("change_sequence") val changeSequence: Long? = null,
)
