package com.srisu.srisu.core.data.network


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DefaultErrorResponse(
    @SerialName("error_details")
    val errorDetails: ErrorDetails?,
    @SerialName("message")
    val message: String?
) {
    @Serializable
    data class ErrorDetails(
        @SerialName("detail")
        val detail: String?
    )
}