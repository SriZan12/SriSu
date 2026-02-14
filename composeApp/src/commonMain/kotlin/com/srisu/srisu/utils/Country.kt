package com.srisu.srisu.utils

import androidx.compose.runtime.Stable
import com.srisu.srisu.utils.Constants.Auth.COUNTRY_JSON_FILE_NAME
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
@Stable
data class CountryModel(
    @SerialName("name")
    val name: String?,
    @SerialName("prefix")
    val prefix: String?,
    @SerialName("code")
    val code: String?
)

object Country {
    private var cachedCountries: List<CountryModel>? = null

    fun getAllCountriesFromJson(): List<CountryModel>? {
        if (cachedCountries != null) return cachedCountries
        
        val json = readJsonFromAssets(COUNTRY_JSON_FILE_NAME)
        cachedCountries = json?.let {
            Json.decodeFromString<List<CountryModel>>(it)
        }
        return cachedCountries
    }

    fun getCountryModelFromName(country: String?): CountryModel? {
        return getAllCountriesFromJson()?.find { it.name == country }
    }

    fun getCountryModelFromPrefix(prefix: String?): CountryModel? {
        return getAllCountriesFromJson()?.find { it.prefix == prefix }
    }

}
