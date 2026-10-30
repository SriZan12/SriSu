package com.srisu.srisu.features.moments.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class MomentPerson(val id: Long, val name: String = "", @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("active_count") val activeCount: Int = 0, @SerialName("unseen_count") val unseenCount: Int = 0,
    @SerialName("next_expiry") val nextExpiry: String? = null)
@Serializable data class MomentCouple(val id: Long, @SerialName("anniversary_date") val anniversary: String? = null,
    val members: List<MomentPerson> = emptyList())
@Serializable data class MomentHome(val user: MomentPerson, val couple: MomentCouple? = null,
    @SerialName("server_time") val serverTime: String)
@Serializable data class MomentPhoto(val id: Long, val image: String, val order: Int = 0,
    val width: Int? = null, val height: Int? = null)
@Serializable data class Moment(
    val id: Long, val couple: Long, @SerialName("created_by") val createdBy: Long,
    val creator: MomentPerson, val caption: String = "", val visibility: String = "private",
    val photos: List<MomentPhoto> = emptyList(), val revision: Long = 1,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("server_time") val serverTime: String,
    @SerialName("has_opened") val hasOpened: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_delete") val canDelete: Boolean = false,
    @SerialName("can_reply") val canReply: Boolean = false,
    @SerialName("partner_seen_at") val partnerSeenAt: String? = null,
    val reacted: Boolean = false,
    @SerialName("partner_reacted") val partnerReacted: Boolean = false,
)
@Serializable data class MomentPage(val results: List<Moment> = emptyList(), val next: String? = null)
@Serializable data class StagedPhoto(val id: String, val image: String, val width: Int, val height: Int)
@Serializable data class MomentDraft(val id: String, val couple: Long, val revision: Long,
    val photos: List<StagedPhoto> = emptyList(), @SerialName("moment_id") val momentId: Long? = null,
    @SerialName("published_at") val publishedAt: String? = null)
@Serializable data class PublishMoment(@SerialName("expected_revision") val revision: Long, val caption: String,
    val visibility: String, @SerialName("photo_ids") val photoIds: List<String>)
@Serializable data class MomentDraftRecord(val id: String, val couple: Long, val caption: String = "",
    val visibility: String = "private", val order: List<String> = emptyList(),
    val publication: PublishMoment? = null)
@Serializable data class MomentNote(val id: Long, val message: String, val sender: Long? = null,
    val replies: List<MomentNoteReply> = emptyList())
@Serializable data class MomentNoteReply(val id: Long, val message: String)
@Serializable data class MomentNotePage(val results: List<MomentNote> = emptyList(), val next: String? = null)
@Serializable data class MomentDiscoveryCouple(val id: Long)
@Serializable data class MomentPreview(val id: Long, val caption: String = "", val photos: List<MomentPhoto> = emptyList(),
    @SerialName("expires_at") val expiresAt: String)
@Serializable data class MomentDiscoveryCard(val couple: MomentDiscoveryCouple, val preview: MomentPreview,
    @SerialName("is_faved") val isFaved: Boolean = false, @SerialName("has_unopened") val hasUnopened: Boolean = false)
@Serializable data class MomentDiscoveryPage(@SerialName("server_time") val serverTime: String, val results: List<MomentDiscoveryCard> = emptyList(), val next: String? = null)

/** Only IDs survive in an encrypted chat event. No photo/caption is copied into history. */
@Serializable data class MomentReference(val id: Long, val couple: Long) {
    val url get() = "srisu://moments/$id/couples/$couple"
    companion object {
        fun parse(value: String?): MomentReference? {
            val match = value?.let { Regex("^srisu://moments/([1-9][0-9]{0,18})/couples/([1-9][0-9]{0,18})$").matchEntire(it) } ?: return null
            val id = match.groupValues[1].toLongOrNull() ?: return null
            val couple = match.groupValues[2].toLongOrNull() ?: return null
            return MomentReference(id, couple)
        }
    }
}
