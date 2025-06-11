package com.srisu.srisu.utils

import com.srisu.srisu.utils.Constants.COUNTRY_JSON_FILE_NAME
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CountryModel(
    @SerialName("name")
    val name: String?,
    @SerialName("prefix")
    val prefix: String?,
    @SerialName("code")
    val code: String?
)

object Country {
    fun getAllCountriesFromJson(): List<CountryModel>? {
        val json = readJsonFromAssets(COUNTRY_JSON_FILE_NAME)
        return json?.let {
            Json.decodeFromString<List<CountryModel>>(it)
        }
    }

}
