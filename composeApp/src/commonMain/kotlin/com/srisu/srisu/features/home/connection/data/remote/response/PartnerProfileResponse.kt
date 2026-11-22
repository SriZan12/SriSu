package com.srisu.srisu.features.home.connection.data.remote.response

import com.srisu.srisu.features.auth.data.remote.response.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PartnerProfileResponse(
    val user: User,
    @SerialName("details_visible") val detailsVisible: Boolean = false,
    @SerialName("connection_state") val connectionState: String = "unavailable",
    @SerialName("request_id") val requestId: Long? = null,
)
