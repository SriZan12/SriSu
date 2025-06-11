package com.srisu.srisu.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
class DefaultResponse<T> {

    @SerialName("message")
    var message: String? = null

    @SerialName("data")
    var data: T? = null

}

@Serializable
data class ErrorResponse(
    @SerialName("error_details") val error_details: Map<String, List<String>>? = null,
    @SerialName("error") val error: Map<String, List<String>>? = null,
    @SerialName("message") val message: String? = null
)