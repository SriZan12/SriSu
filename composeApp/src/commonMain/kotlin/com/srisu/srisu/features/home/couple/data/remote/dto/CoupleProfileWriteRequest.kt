package com.srisu.srisu.features.home.couple.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoupleProfileWriteRequest(
    @SerialName("partner_id")
    val partnerId: Long? = null,
    @SerialName("title")
    val title: String,
    @SerialName("anniversary_date")
    val anniversaryDate: String?,
    @SerialName("shared_dreams")
    val sharedDreams: List<String>,
    @SerialName("shared_interests")
    val sharedInterests: List<String>,
    @SerialName("relationship_tagline")
    val relationshipTagline: String,
    @SerialName("journey_story")
    val journeyStory: String,
    @SerialName("relationship_strength")
    val relationshipStrength: Int,
)
