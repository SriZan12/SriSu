package com.srisu.srisu.features.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.*

const val NOTIFICATION_CATALOGUE = "notification-catalogue-2"
data class NotificationCategory(val key: String, val label: String, val enabledByDefault: Boolean)
val notificationCategories = listOf(
    NotificationCategory("requests", "Partner requests & sharing", true),
    NotificationCategory("plans", "Plan invitations & changes", true),
    NotificationCategory("questions", "Questions & mutual reveals", true),
    NotificationCategory("challenges", "Challenge invitations & outcomes", true),
    NotificationCategory("moments", "Partner’s new Moments", true),
    NotificationCategory("moment_replies", "Replies to your appreciations", true),
    NotificationCategory("appreciations", "New appreciations", false),
    NotificationCategory("moment_reactions", "Hearts on your Moments", false),
    NotificationCategory("status", "Partner’s status updates", false),
    NotificationCategory("ideas", "Shared date ideas", false),
    NotificationCategory("spark_progress", "Partner’s question progress", false),
    NotificationCategory("challenge_progress", "Challenge progress", false),
)

@Serializable data class InstallationRequest(
    @SerialName("installation_id") val installationId: String,
    val proof: String, val version: Long, val platform: String,
    @SerialName("target_kind") val targetKind: String, val target: String, val permitted: Boolean,
    val capabilities: List<String> = emptyList(),
)
@Serializable data class InstallationBinding(@SerialName("installation_id") val installationId: String, val version: Long)
@Serializable data class NotificationPreferences(
    val contract: String = "notifications-1", val push: Boolean = true, val chat: Boolean = true,
    val activity: Boolean = true, @SerialName("mute_until") val muteUntil: String? = null,
    @SerialName("mute_forever") val muteForever: Boolean = false, val revision: Long = 1,
    val capabilities: List<String> = emptyList(),
    val categories: Map<String, Boolean> = emptyMap(),
)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PreferenceWrite(
    @SerialName("expected_revision") val expectedRevision: Long,
    val push: Boolean, val chat: Boolean, val activity: Boolean,
    // The server requires an explicit command, including when only a switch changes.
    @kotlinx.serialization.EncodeDefault val mute: String = "unchanged",
    val categories: Map<String, Boolean>? = null,
)
@Serializable data class NotificationDestination(
    val kind: String, @SerialName("room_id") val roomId: String? = null,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("couple_id") val coupleId: Long? = null, @SerialName("plan_id") val planId: Long? = null,
    @SerialName("spark_id") val sparkId: String? = null,
    @SerialName("challenge_id") val challengeId: String? = null,
    @SerialName("moment_id") val momentId: Long? = null,
    @SerialName("note_id") val noteId: Long? = null,
    @SerialName("idea_id") val ideaId: String? = null,
) {
    fun route(): Route? = when (kind) {
        "conversation" -> if (validNotificationId(roomId) && validNotificationId(messageId)) ChatNav.CoupleConversation(roomId, messageId, "notification") else null
        "matrix_conversation" -> if (validNotificationId(roomId) && messageId == null)
            ChatNav.CoupleConversation(roomId!!.lowercase(), origin="notification") else null
        "invitations" -> ChatNav.PartnerRequests
        "couple" -> coupleId?.takeIf { it > 0 }?.let { CoupleProfileDestination(it) }
        "sharing" -> coupleId?.takeIf { it > 0 }?.let { CoupleProfileDestination(it, CoupleSection.SHARING) }
        "plan" -> if ((coupleId ?: 0) > 0 && (planId ?: 0) > 0) com.srisu.srisu.features.plans.navigation.PlansDestination(planId) else null
        "spark" -> sparkId?.takeIf(::validNotificationId)?.let { com.srisu.srisu.features.sparks.navigation.SparksDestination(it.lowercase()) }
        "challenge" -> challengeId?.takeIf(::validNotificationId)?.let { com.srisu.srisu.features.challenges.navigation.ChallengesDestination(it.lowercase()) }
        "moment" -> if ((momentId ?: 0) > 0 && (coupleId ?: 0) > 0) com.srisu.srisu.features.moments.navigation.MomentViewerDestination(momentId, coupleId) else null
        "moment_note" -> noteId?.takeIf { it > 0 }?.let { com.srisu.srisu.features.moments.navigation.MomentNoteDestination(it) }
        "idea" -> ideaId?.takeIf(::validNotificationId)?.let { com.srisu.srisu.features.plans.navigation.DateIdeaDestination(it.lowercase()) }
        "home" -> HomeNavigation.Home
        else -> null
    }
}
fun validNotificationId(value: String?): Boolean = value != null && Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(value)
