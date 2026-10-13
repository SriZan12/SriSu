package com.srisu.srisu.features.couplechat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoupleChatRoom(
    val id: String,
    @SerialName("relationship_id") val relationshipId: Long,
    @SerialName("participant_ids") val participantIds: List<Long>,
    val sequence: Long,
    @SerialName("security_state") val securityState: String,
    @SerialName("can_send") val canSend: Boolean,
    @SerialName("participant_names") val participantNames: Map<String, String?> = emptyMap(),
)

@Serializable
data class CoupleChatSnapshot(val contract: String, val room: CoupleChatRoom?, val devices: ChatDevices? = null, val capabilities: List<String> = emptyList())

@Serializable
data class ChatPublicKey(val id: Int, @SerialName("public_key") val publicKey: String, val signature: String? = null)

@Serializable
data class ChatDevice(
    val id: String,
    @SerialName("user_id") val userId: Long,
    @SerialName("registration_id") val registrationId: Int,
    @SerialName("identity_key") val identityKey: String,
    @SerialName("signed_prekey") val signedPrekey: ChatPublicKey,
    @SerialName("prekeys_remaining") val prekeysRemaining: Map<String, Int> = emptyMap(),
)

@Serializable
data class ChatDevices(
    @SerialName("current_device") val currentDevice: ChatDevice?,
    @SerialName("session_owns_device") val sessionOwnsDevice: Boolean,
    @SerialName("partner_device") val partnerDevice: ChatDevice?,
)

@Serializable
data class ChatRegistration(
    @SerialName("device_id") val deviceId: String,
    @SerialName("replace_device_id") val replaceDeviceId: String? = null,
    @SerialName("registration_id") val registrationId: Int,
    @SerialName("identity_key") val identityKey: String,
    @SerialName("signed_prekey") val signedPrekey: ChatPublicKey,
    @SerialName("ec_prekeys") val ecPrekeys: List<ChatPublicKey>,
    @SerialName("kyber_prekeys") val kyberPrekeys: List<ChatPublicKey>,
)

@Serializable
data class ChatKeyPublication(
    @SerialName("device_id") val deviceId: String,
    @SerialName("operation_id") val operationId: String,
    @SerialName("signed_prekey") val signedPrekey: ChatPublicKey,
    @SerialName("ec_prekeys") val ecPrekeys: List<ChatPublicKey>,
    @SerialName("kyber_prekeys") val kyberPrekeys: List<ChatPublicKey>,
)

@Serializable
data class ChatBundleClaim(
    @SerialName("device_id") val deviceId: String,
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("operation_id") val operationId: String,
)

@Serializable
data class ChatBundle(
    val id: String,
    @SerialName("user_id") val userId: Long,
    @SerialName("registration_id") val registrationId: Int,
    @SerialName("identity_key") val identityKey: String,
    @SerialName("signed_prekey") val signedPrekey: ChatPublicKey,
    @SerialName("ec_prekey") val ecPrekey: ChatPublicKey,
    @SerialName("kyber_prekey") val kyberPrekey: ChatPublicKey,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ChatEnvelope(
    @SerialName("device_id") val deviceId: String,
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("operation_id") val operationId: String,
    val kind: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("reply_to") val replyTo: String? = null,
    val version: Long = 0,
    @SerialName("signal_type") val signalType: Int,
    val ciphertext: String,
    val attachments: List<String> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("notification_plan_id") val notificationPlanId: Long? = null,
)

@Serializable
data class ChatAcceptance(
    @SerialName("operation_id") val operationId: String,
    val sequence: Long,
    val applied: Boolean,
    @SerialName("accepted_at") val acceptedAt: String,
)

@Serializable
data class ChatReceivedOperation(
    @SerialName("operation_id") val operationId: String,
    @SerialName("actor_id") val actorId: Long,
    @SerialName("sender_device_id") val senderDeviceId: String,
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    val kind: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("reply_to") val replyTo: String? = null,
    val version: Long,
    val applied: Boolean,
    @SerialName("signal_type") val signalType: Int,
    val ciphertext: String?,
    val unavailable: Boolean,
    @SerialName("target_deleted") val targetDeleted: Boolean,
    val attachments: List<String> = emptyList(),
)

@Serializable
data class ChatChange(
    val sequence: Long,
    val kind: String,
    @SerialName("created_at") val createdAt: String,
    val operation: ChatReceivedOperation? = null,
)

@Serializable
data class ChatChanges(
    val changes: List<ChatChange>,
    val cursor: Long,
    @SerialName("high_watermark") val highWatermark: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
internal data class StateChanged(val version: Int, val type: String)
