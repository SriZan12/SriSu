package com.srisu.srisu.features.home.coupleprofile.presentation.state

data class EditCoupleProfileUiState(
    val coupleTitle: String = "",
    val tagline: String = "",
    val anniversary: String = "",
    val sharedInterests: String = "",
    val relationshipStrength: Float = 0f,
    val journeyStory: String = "",
    val coverPhotoUrl: String? = null,
    val firstPartnerPhotoUrl: String? = null,
    val secondPartnerPhotoUrl: String? = null,
) {
    fun toCoupleProfile(previous: CoupleProfileUiState) = previous.copy(
        coupleTitle = coupleTitle.trim(),
        tagline = tagline.trim(),
        anniversary = anniversary.trim(),
        sharedInterests = sharedInterests
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct(),
        relationshipStrength = relationshipStrength.toInt(),
        journeyStory = journeyStory.trim(),
        coverPhotoUrl = coverPhotoUrl,
        firstPartnerPhotoUrl = firstPartnerPhotoUrl,
        secondPartnerPhotoUrl = secondPartnerPhotoUrl,
    )

    companion object {
        fun from(profile: CoupleProfileUiState) = EditCoupleProfileUiState(
            coupleTitle = profile.coupleTitle,
            tagline = profile.tagline,
            anniversary = profile.anniversary,
            sharedInterests = profile.sharedInterests.joinToString(", "),
            relationshipStrength = profile.relationshipStrength.toFloat(),
            journeyStory = profile.journeyStory,
            coverPhotoUrl = profile.coverPhotoUrl,
            firstPartnerPhotoUrl = profile.firstPartnerPhotoUrl,
            secondPartnerPhotoUrl = profile.secondPartnerPhotoUrl,
        )
    }
}
