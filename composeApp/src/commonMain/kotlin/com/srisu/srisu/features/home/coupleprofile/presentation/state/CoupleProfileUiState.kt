package com.srisu.srisu.features.home.coupleprofile.presentation.state

import kotlinx.serialization.Serializable

@Serializable
data class CoupleProfileUiState(
    val coupleTitle: String = "The Soulmates",
    val partnerNames: String = "Sri & Su",
    val tagline: String = "Two hearts, one journey.",
    val coverPhotoUrl: String? = null,
    val firstPartnerPhotoUrl: String? = null,
    val secondPartnerPhotoUrl: String? = null,
    val daysTogether: Int = 1_452,
    val anniversary: String = "Oct 12",
    val sharedInterests: List<String> = listOf(
        "Travel",
        "Coffee",
        "Photography",
        "Hiking",
        "Art Museums",
    ),
    val relationshipStrength: Int = 98,
    val journeyStory: String = "It started with a chance encounter at a local coffee shop on a rainy Tuesday. What was supposed to be a quick hello turned into hours of conversation about our shared dreams, favorite books, and love for unexpected adventures. Over the years, we've built a life filled with quiet mornings, spontaneous road trips, and endless support for one another. This is just the beginning of our beautiful story.",
)
