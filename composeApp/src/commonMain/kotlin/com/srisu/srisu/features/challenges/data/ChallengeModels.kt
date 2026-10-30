package com.srisu.srisu.features.challenges.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable data class ChallengeActivity(val id: String, val collection: String, val title: String,
    val description: String, val minutes: Int, val mode: String, val cost: String,
    val steps: List<String>, val alternative: String)
@Serializable data class ChallengeCollection(val id: String, val name: String, val subtitle: String,
    val activities: List<ChallengeActivity>)
@Serializable data class ChallengeMember(val id: Long, val name: String, val photo: String? = null)
@Serializable data class ChallengeHome(val collections: List<ChallengeCollection> = emptyList(),
    val couple: Long? = null, val members: List<ChallengeMember> = emptyList(), val current: ChallengeSession? = null)
@Serializable data class ChallengeHistory(val items: List<ChallengeSession> = emptyList(), val next: String? = null)
@Serializable data class ChallengeSession(val id: String, val couple: Long, val creator: Long,
    val activity: ChallengeActivity, val state: String, val revision: Long,
    @SerialName("accepted_by") val acceptedBy: List<Long> = emptyList(),
    @SerialName("confirmed_by") val confirmedBy: List<Long> = emptyList(),
    @SerialName("paused_by") val pausedBy: List<Long> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("matrix_room_id") val matrixRoomId: String? = null,
    @SerialName("membership_epoch") val epoch: Long? = null,
    @SerialName("card_event_id") val cardEventId: String = "") {
    val live get() = state in listOf("invited", "active", "paused")
    fun allows(action: String, account: Long?) = when (action) {
        "accept", "decline" -> state == "invited" && account != creator
        "cancel" -> live && (state != "invited" || account == creator)
        "confirm" -> state == "active" && account !in confirmedBy
        "unconfirm" -> state == "active" && account in confirmedBy
        "pause" -> state in listOf("active", "paused") && account !in pausedBy
        "resume" -> state == "paused" && account in pausedBy
        else -> false
    } && account != null
}

/** Persisted BEFORE transmission, scoped to account and API origin. */
@Serializable data class ChallengeIntent(val requestId: String, val couple: Long, val templateId: String? = null,
    val sessionId: String? = null, val action: String? = null, val revision: Long? = null)

@Serializable data class ChallengeReference(val id: String, val couple: Long) {
    val url get() = "https://srisu.app/challenges/$couple/$id"
    companion object {
        private val pattern = Regex("^https://srisu\\.app/challenges/([1-9][0-9]*)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")
        fun parse(value: String?): ChallengeReference? {
            val match = pattern.matchEntire(value ?: return null) ?: return null
            return ChallengeReference(match.groupValues[2], match.groupValues[1].toLongOrNull() ?: return null)
        }
    }
}
