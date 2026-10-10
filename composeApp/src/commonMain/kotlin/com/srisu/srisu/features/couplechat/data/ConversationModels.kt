package com.srisu.srisu.features.couplechat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatQuote(val messageId: String, val authorId: Long, val preview: String)

/** Explicit private payload variants; none of these fields belong in routing metadata. */
@Serializable
sealed interface ChatContent {
    @Serializable @SerialName("text")
    data class Text(val body: String, val quote: ChatQuote? = null) : ChatContent
    @Serializable @SerialName("media")
    data class Media(val items: List<ChatMedia>, val caption: String = "", val quote: ChatQuote? = null) : ChatContent
    @Serializable @SerialName("card")
    data class Card(val card: ChatCard) : ChatContent
    @Serializable @SerialName("card_response")
    data class CardResponse(val reply: CardReply) : ChatContent
    @Serializable @SerialName("reaction")
    data class Reaction(val emoji: String?) : ChatContent
    @Serializable @SerialName("delete")
    data object Delete : ChatContent
    @Serializable @SerialName("receipt")
    data object Receipt : ChatContent
}

@Serializable
data class ChatPrivatePayload(val version: Int = 1, val content: ChatContent)

@Serializable
enum class ChatTransport { QUEUED, SENDING, FAILED, SENT }

@Serializable
data class LocalChatMessage(
    val id: String,
    val roomId: String,
    val authorId: Long,
    val createdAt: String,
    val content: ChatContent? = null,
    val transport: ChatTransport = ChatTransport.QUEUED,
    val failure: String? = null,
    val sequence: Long? = null,
    val deleted: Boolean = false,
    val hidden: Boolean = false,
    val unavailable: Boolean = false,
    val replyTo: String? = null,
    val reactions: Map<Long, String> = emptyMap(),
    val delivered: Boolean = false,
    val read: Boolean = false,
    val played: Boolean = false,
    val timelineKey: String,
    val cardResponses: Map<Long,CardReply> = emptyMap(),
)

@Serializable
internal data class QueuedChatOperation(
    val id: String,
    val roomId: String,
    val kind: String,
    val targetId: String,
    val content: ChatPrivatePayload,
    val queueKey: String,
    val replyTo: String? = null,
    val version: Long = 0,
    val senderDeviceId: String? = null,
    val recipientDeviceId: String? = null,
    val attempts: Int = 0,
    val retryAt: Long = 0,
    val failure: String? = null,
    val permanentFailure: Boolean = false,
)

@Serializable
data class ConversationPreferences(val nickname: String = "", val readReceipts: Boolean = true, val presence: Boolean = true)

@Serializable
data class ChatReadingAnchor(val messageId: String,val offset: Int,val windowEnd: String)

enum class ConversationSecurity { SETTING_UP, READY, PARTNER_SETUP, REPLACE_DEVICE, IDENTITY_CHANGED, KEY_LOST, STORAGE_ERROR, ARCHIVED }

data class ConversationState(
    val accountId: Long? = null,
    val roomId: String? = null,
    val partnerId: Long? = null,
    val partnerName: String = "Your partner",
    val security: ConversationSecurity = ConversationSecurity.SETTING_UP,
    val messages: List<LocalChatMessage> = emptyList(),
    val draft: String = "",
    val reply: ChatQuote? = null,
    val preferences: ConversationPreferences = ConversationPreferences(),
    val loading: Boolean = true,
    val synchronizing: Boolean = false,
    val hasOlder: Boolean = false,
    val newMessages: Int = 0,
    val atLatest: Boolean = true,
    val readingAnchor: ChatReadingAnchor? = null,
    val error: String? = null,
    val safetyCode: String? = null,
    val safetyQr: String? = null,
    val safetyVerified: Boolean? = null,
    val mediaDraft: MediaDraft? = null,
    val uploadProgress: Map<String, Float> = emptyMap(),
    val openedMedia: OpenedChatMedia? = null,
    val mediaLoading: Boolean = false,
    val offlineMedia: Set<String> = emptySet(),
    val partnerActivity: PartnerChatActivity = PartnerChatActivity(),
    val sparks: Map<String,LocalSparkState> = emptyMap(),
    val pendingCards: Set<String> = emptySet(),
    val relationshipId: Long? = null,
    val unreadCount: Int = 0,
    val privateDrafts: Map<String,String> = emptyMap(),
)

@Serializable
data class MediaDraft(val messageId: String, val items: List<ChatMedia>)
data class OpenedChatMedia(val messageId: String, val media: ChatMedia, val bytes: ByteArray)
