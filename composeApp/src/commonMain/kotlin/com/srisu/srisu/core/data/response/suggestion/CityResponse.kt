package com.srisu.srisu.core.data.response.suggestion


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class City(
    @SerialName("data")
    val data: List<String>? = null,
)
