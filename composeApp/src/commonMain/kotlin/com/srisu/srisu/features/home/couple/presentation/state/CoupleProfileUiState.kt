package com.srisu.srisu.features.home.couple.presentation.state

import kotlinx.serialization.Serializable

@Serializable
data class CoupleProfileUiState(
    val id: Long? = null,
    val coupleConnectionId: Long? = null,
    val partnerId: Long? = null,
    val profileComplete: Boolean = false,
    val coupleTitle: String = "",
    val partnerNames: String = "",
    val tagline: String = "",
    val coverPhotoUrl: String? = null,
    val firstPartnerPhotoUrl: String? = null,
    val secondPartnerPhotoUrl: String? = null,
    val daysTogether: Int = 0,
    val anniversaryDate: String? = null,
    val anniversary: String = "—",
    val sharedDreams: List<String> = emptyList(),
    val sharedInterests: List<String> = emptyList(),
    val relationshipStrength: Int = 0,
    val journeyStory: String = "",
) {
    companion object {
        fun preview() = CoupleProfileUiState(
            id = 7,
            coupleConnectionId = 12,
            partnerId = 172,
            profileComplete = true,
            coupleTitle = "The Soulmates",
            partnerNames = "Sri & Su",
            tagline = "Two hearts, one journey.",
            daysTogether = 1_452,
            anniversaryDate = "2022-10-12",
            anniversary = "Oct 12",
            sharedDreams = listOf("See the northern lights", "Build a home"),
            sharedInterests = listOf(
                "Travel",
                "Coffee",
                "Photography",
                "Hiking",
                "Art Museums",
            ),
            relationshipStrength = 98,
            journeyStory = "It started with a chance encounter at a local coffee shop on a rainy Tuesday. What was supposed to be a quick hello turned into hours of conversation about our shared dreams, favorite books, and love for unexpected adventures. Over the years, we've built a life filled with quiet mornings, spontaneous road trips, and endless support for one another.",
        )
    }
}
