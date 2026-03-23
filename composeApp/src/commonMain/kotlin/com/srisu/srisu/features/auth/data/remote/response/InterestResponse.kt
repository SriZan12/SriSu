package com.srisu.srisu.features.auth.data.remote.response


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class InterestResponse(
    @SerialName("interests")
    val interests: List<Interest?>? = null
) {
    @Serializable
    data class Interest(
        @SerialName("category")
        val category: Category? = null,
        @SerialName("created_date")
        val createdDate: String? = null,
        @SerialName("id")
        val id: Int? = null,
        @SerialName("name")
        val name: String? = null,
        @SerialName("updated_date")
        val updatedDate: String? = null
    ) {
        @Serializable
        data class Category(
            @SerialName("id")
            val id: Int? = null,
            @SerialName("label")
            val label: String? = null,
            @SerialName("name")
            val name: String? = null
        )
    }
}