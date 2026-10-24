package com.srisu.srisu.features.couplechat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatQuote(val messageId: String, val authorId: Long, val preview: String)

/** Presentation content only; Matrix owns serialization on the wire. */
@Serializable
sealed interface ChatContent {
    @Serializable @SerialName("plan")
    data class Plan(val body: String, val reference: com.srisu.srisu.features.plans.data.PlanReference) : ChatContent
    @Serializable @SerialName("challenge")
    data class Challenge(val body: String, val reference: com.srisu.srisu.features.challenges.data.ChallengeReference) : ChatContent
    @Serializable @SerialName("text")
    data class Text(val body: String, val quote: ChatQuote? = null) : ChatContent
    @Serializable @SerialName("spark")
    data class Spark(val body: String, val reference: com.srisu.srisu.features.sparks.data.SparkReference) : ChatContent
    @Serializable @SerialName("moment")
    data class Moment(val body: String, val reference: com.srisu.srisu.features.moments.data.MomentReference) : ChatContent
    @Serializable @SerialName("media")
    data class Media(val items: List<ChatMedia>, val caption: String = "", val quote: ChatQuote? = null) : ChatContent

}

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
)

@Serializable
data class ConversationPreferences(val nickname: String = "", val readReceipts: Boolean = true, val presence: Boolean = true, val chatTheme: String = ChatThemePreset.DEFAULT.id)

enum class ConversationSecurity { SETTING_UP, READY }

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
    val hasOlder: Boolean = false,
    val error: String? = null,
    val mediaDraft: MediaDraft? = null,
    val uploadProgress: Map<String, Float> = emptyMap(),
    val openedMedia: OpenedChatMedia? = null,
    val mediaLoading: Boolean = false,
    val mediaRequest: ChatMediaRequest? = null,
    val mediaRequestError: String? = null,
    val offlineMedia: Set<String> = emptySet(),
)

@Serializable
data class MediaDraft(val messageId: String, val items: List<ChatMedia>)
data class OpenedChatMedia(val messageId: String, val media: ChatMedia, val bytes: ByteArray)

/** Identifies the attachment being opened without retaining its plaintext bytes. */
data class ChatMediaRequest(val messageId: String, val attachmentId: String, val kind: String)

/** Personal chat palette; independent of system light/dark appearance. Unknown saved IDs fall back without migration. */
enum class ChatThemePreset(val id: String, val label: String, val description: String) {
    ORIGINAL("original", "Original SriSu", "Warm neutrals · soft violet"),
    BLEND("blend", "SriSu Blend", "Violet flowing into warm peach"),
    CALM("calm", "SriSu Calm", "Quiet blue · gentle contrast"),
    IRIS("iris", "SriSu Iris", "Soft lavender blending into mist blue"),
    DAWN("dawn", "SriSu Dawn", "Warm peach blending into cream");
    companion object {
        val DEFAULT = IRIS
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}
