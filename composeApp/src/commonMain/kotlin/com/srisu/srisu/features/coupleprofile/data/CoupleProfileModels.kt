package com.srisu.srisu.features.coupleprofile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class CoupleProfile(
    val id: Long,
    val viewer: String,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_fave") val canFave: Boolean = false,
    @SerialName("can_view_moments") val canViewMoments: Boolean = false,
    @SerialName("is_faved") val isFaved: Boolean = false,
    @SerialName("published_sections") val publishedSections: List<String> = emptyList(),
    val members: List<CoupleMember>? = null,
    val story: List<StoryAnswer>? = null,
    val song: CoupleSong? = null,
    val interests: CoupleInterests? = null,
    val cover: CoupleCover? = null,
    @SerialName("anniversary_date") val anniversaryDate: String? = null,
    @SerialName("days_together") val daysTogether: Int? = null,
    @SerialName("visible_moment_count") val visibleMomentCount: Int = 0,
    @SerialName("plan_count") val planCount: Int? = null,
    @SerialName("days_to_anniversary") val daysToAnniversary: Int? = null,
    @SerialName("plans_done") val plansDone: Int? = null,
    @SerialName("plans_upcoming") val plansUpcoming: Int? = null,
    val revisions: Map<String, String> = emptyMap(),
    val sharing: CoupleSharing? = null,
)
@Serializable data class CoupleMember(val id: Long, val name: String, @SerialName("photo_url") val photoUrl: String? = null)
@Serializable data class StoryAnswer(@SerialName("author_id") val authorId: Long, val prompt: String, val answer: String)
@Serializable data class CoupleSong(val title: String, val artist: String = "", val band: String = "", val note: String = "", @SerialName("picked_by_id") val pickedBy: Long? = null)
@Serializable data class CoupleInterests(val shared: List<String> = emptyList(), val mine: List<String>? = null, val partner: List<String>? = null)
@Serializable data class CoupleCover(val url: String? = null, @SerialName("focal_y") val focalY: Float = .5f)
@Serializable data class CoupleSharing(val proposal: List<String> = emptyList(), @SerialName("proposal_version") val version: Long = 0, @SerialName("approved_by") val approvedBy: List<Long> = emptyList(), @SerialName("valid_proposal") val validProposal: Boolean = false)
@Serializable data class ProfileChange(val id: Long, @SerialName("actor_id") val actorId: Long, val section: String, val action: String, @SerialName("created_at") val createdAt: String)
@Serializable data class HistoryPage(val results: List<ProfileChange>, @SerialName("next_before") val nextBefore: Long? = null)
@Serializable data class CoverChoice(val id: Long, val url: String)
@Serializable data class CoverPage(val results: List<CoverChoice>, @SerialName("next_before") val nextBefore: Long? = null)
@Serializable data class CouplePlan(val id: Long, @SerialName("created_by") val createdBy: Long, val title: String, @SerialName("starts_at") val startsAt: String, val response: String, @SerialName("response_note") val responseNote: String = "", val completed: Boolean = false, val revision: Long)
@Serializable data class PlansPage(val results: List<CouplePlan>, @SerialName("next_before") val nextBefore: Long? = null)

@Serializable data class ProfileChatAction(val kind: String, @SerialName("couple_id") val coupleId: Long, val prompt: String? = null, @SerialName("plan_id") val planId: Long? = null)
@Serializable data class ProfileInviteResult(@SerialName("message_id") val messageId: Long, @SerialName("room_id") val roomId: String)
@Serializable data class DiscoveryCouple(val id: Long)
@Serializable data class DiscoveryMoment(val id: Long, val title: String? = null, val caption: String? = null, @SerialName("expires_at") val expiresAt: String)
@Serializable data class DiscoveryCard(val couple: DiscoveryCouple, val preview: DiscoveryMoment)
@Serializable data class DiscoveryPage(val results: List<DiscoveryCard>, val next: String? = null)
