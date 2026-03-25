package com.srisu.srisu.features.home.suggestions.data.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CityResponse(
    @SerialName("data")
    val data: List<String?>? = null,
    @SerialName("error")
    val error: Boolean? = null,
    @SerialName("msg")
    val msg: String? = null
)