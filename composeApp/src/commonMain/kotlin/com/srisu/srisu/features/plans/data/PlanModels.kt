package com.srisu.srisu.features.plans.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable data class PlanMember(val id: Long, val name: String, val photo: String? = null)
@Serializable data class PlanHome(val couple: Long? = null, val members: List<PlanMember> = emptyList(),
    @SerialName("next_plan") val nextPlan: Plan? = null, val invitations: Int = 0)
@Serializable data class PlanPage(val items: List<Plan> = emptyList(), val next: String? = null)
@Serializable data class PlanChange(val title: String, @SerialName("starts_at") val startsAt: String,
    val place: String = "", @SerialName("time_zone") val timeZone: String = "UTC", val author: Long)
@Serializable data class PlanItem(val id: String, val text: String, @SerialName("assigned_to") val assignedTo: Long? = null,
    val checked: Boolean = false, @SerialName("checked_by") val checkedBy: Long? = null)
@Serializable data class Plan(val id: Long, val couple: Long, val creator: Long, val title: String,
    @SerialName("starts_at") val startsAt: String, @SerialName("time_zone") val timeZone: String = "UTC", val place: String = "",
    val response: String, val state: String, val revision: Long,
    @SerialName("response_note") val responseNote: String = "",
    @SerialName("pending_change") val change: PlanChange? = null, val checklist: List<PlanItem> = emptyList(),
    @SerialName("cancelled_by") val cancelledBy: Long? = null, @SerialName("completed_by") val completedBy: Long? = null,
    @SerialName("room_id") val roomId: String? = null, @SerialName("matrix_room_id") val matrixRoomId: String? = null,
    @SerialName("membership_epoch") val epoch: Long? = null, @SerialName("card_event_id") val cardEventId: String = "") {
    val ended get() = state in listOf("cancelled", "declined", "completed")
    val instant get() = Instant.parse(startsAt)
    val proposedBy get() = change?.author ?: creator
    fun allows(action: String, account: Long?, now: Instant = Clock.System.now()): Boolean = account != null && when(action) {
        "accept", "decline" -> !ended && proposedBy != account && (change != null || response != "yes") && (action == "decline" || Instant.parse(change?.startsAt ?: startsAt) > now)
        "propose" -> !ended && (change == null || change.author == account)
        "withdraw_change" -> !ended && change?.author == account
        "cancel" -> !ended
        "complete" -> !ended && response == "yes" && instant <= now && change == null
        "undo_complete" -> state == "completed"
        "add_item", "set_item", "remove_item" -> !ended && response == "yes"
        else -> false
    }
}
@Serializable data class PlanDraft(val couple: Long, val title: String = "", val startsAt: String = "", val place: String = "",
    val timeZone: String = "UTC", val planId: Long? = null, val revision: Long? = null)
@Serializable data class PlanIntent(val requestId: String, val couple: Long, val planId: Long? = null,
    val revision: Long? = null, val action: String? = null, val values: JsonObject = buildJsonObject {})
@Serializable data class PlanReference(val id: Long, val couple: Long) {
    val url get() = "https://srisu.app/plans/$couple/$id"
    companion object {
        fun parse(value: String?): PlanReference? {
            val m = Regex("^https://srisu\\.app/plans/([1-9][0-9]{0,18})/([1-9][0-9]{0,18})$").matchEntire(value ?: return null) ?: return null
            return PlanReference(m.groupValues[2].toLongOrNull() ?: return null, m.groupValues[1].toLongOrNull() ?: return null)
        }
    }
}
