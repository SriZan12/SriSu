package com.srisu.srisu.features.home.couple.data.remote.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoupleProfileData(
    @SerialName("couple_profile")
    val coupleProfile: CoupleProfileResponse? = null,
)

@Serializable
data class CoupleProfileResponse(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("couple_connection")
    val coupleConnectionId: Long? = null,
    @SerialName("members")
    val members: List<CoupleMembershipResponse> = emptyList(),
    @SerialName("partner")
    val partner: CoupleProfileUserResponse? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("anniversary_date")
    val anniversaryDate: String? = null,
    @SerialName("days_together")
    val daysTogether: Int? = null,
    @SerialName("shared_dreams")
    val sharedDreams: List<String> = emptyList(),
    @SerialName("shared_interests")
    val sharedInterests: List<String> = emptyList(),
    @SerialName("relationship_tagline")
    val relationshipTagline: String? = null,
    @SerialName("journey_story")
    val journeyStory: String? = null,
    @SerialName("relationship_strength")
    val relationshipStrength: Int? = null,
    @SerialName("cover_photo")
    val coverPhoto: String? = null,
    @SerialName("cover_photo_url")
    val coverPhotoUrl: String? = null,
    @SerialName("profile_complete")
    val profileComplete: Boolean = false,
    @SerialName("profile_completed_at")
    val profileCompletedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
data class CoupleMembershipResponse(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("position")
    val position: Int? = null,
    @SerialName("nickname")
    val nickname: String? = null,
    @SerialName("is_owner")
    val isOwner: Boolean = false,
    @SerialName("joined_at")
    val joinedAt: String? = null,
    @SerialName("user")
    val user: CoupleProfileUserResponse? = null,
)

@Serializable
data class CoupleProfileUserResponse(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("profile_photo")
    val profilePhoto: String? = null,
)
