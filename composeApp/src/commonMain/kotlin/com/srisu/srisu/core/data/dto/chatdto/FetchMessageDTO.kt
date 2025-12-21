package com.srisu.srisu.core.data.dto.chatdto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FetchMessageDTO(
    @SerialName("action")
    val action: String,
    @SerialName("page")
    val page: Long?,
    @SerialName("page_size")
    val page_size: Int?
)