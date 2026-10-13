package com.srisu.srisu.features.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.*

@Serializable data class InstallationRequest(
    @SerialName("installation_id") val installationId: String,
    val proof: String, val version: Long, val platform: String,
    @SerialName("target_kind") val targetKind: String, val target: String, val permitted: Boolean,
)
@Serializable data class InstallationBinding(@SerialName("installation_id") val installationId: String, val version: Long)
@Serializable data class NotificationPreferences(
    val contract: String = "notifications-1", val push: Boolean = true, val chat: Boolean = true,
    val activity: Boolean = true, @SerialName("mute_until") val muteUntil: String? = null,
    @SerialName("mute_forever") val muteForever: Boolean = false, val revision: Long = 1,
    val capabilities: List<String> = emptyList(),
)
@Serializable data class PreferenceWrite(
    @SerialName("expected_revision") val expectedRevision: Long,
    val push: Boolean, val chat: Boolean, val activity: Boolean, val mute: String = "unchanged",
)
@Serializable data class NotificationDestination(
    val kind: String, @SerialName("room_id") val roomId: String? = null,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("couple_id") val coupleId: Long? = null, @SerialName("plan_id") val planId: Long? = null,
) {
    fun route(): Route? = when (kind) {
        "conversation" -> if (validNotificationId(roomId) && validNotificationId(messageId)) ChatNav.CoupleConversation(roomId, messageId, "notification") else null
        "invitations" -> ChatNav.PartnerRequests
        "couple" -> coupleId?.takeIf { it > 0 }?.let { CoupleProfileDestination(it) }
        "sharing" -> coupleId?.takeIf { it > 0 }?.let { CoupleProfileDestination(it, CoupleSection.SHARING) }
        "plan" -> if ((coupleId ?: 0) > 0 && (planId ?: 0) > 0) CoupleProfileDestination(coupleId, CoupleSection.PLAN, planId) else null
        else -> null
    }
}
fun validNotificationId(value: String?): Boolean = value != null && Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(value)
