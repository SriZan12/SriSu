package com.srisu.srisu.utils

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class CountryModel(
    // Keep the server-facing name separate from translated/modern presentation.
    @SerialName("name") val name: String?,
    @SerialName("prefix") val prefix: String?,
    @SerialName("code") val code: String?,
    val displayName: String = name.orEmpty(),
    val aliases: List<String> = emptyList(),
    val legacy: Boolean = false,
)

object Country {
    // Generated, shared and offline: no asset read/JSON parsing on the UI thread.
    private val all: List<CountryModel> by lazy { bundledCountries().sortedBy { searchKey(it.displayName) } }
    private val current by lazy { all.filterNot { it.legacy } }
    private val byCode by lazy { all.associateBy { it.code } }
    private val searchIndex by lazy { all.associate { it.code to searchKey(listOf(it.displayName, it.name.orEmpty(), it.code.orEmpty()).plus(it.aliases).joinToString(" ")) } }

    // Name retained for existing ViewModel/provider compatibility.
    fun getAllCountriesFromJson(): List<CountryModel> = current
    fun getCountryModelFromCode(code: String?): CountryModel? = byCode[code?.uppercase()]

    fun getCountryModelFromName(country: String?): CountryModel? {
        if (country.isNullOrBlank()) return null
        val key = searchKey(country)
        val found = all.firstOrNull { searchKey(it.name.orEmpty()) == key }
            ?: all.firstOrNull { searchKey(it.displayName) == key || it.code.equals(country, true) || it.aliases.any { alias -> searchKey(alias) == key } }
        // Preserve exact saved values, including retired/unknown countries. Merely
        // editing another profile field must never erase or rename its country.
        return found?.copy(name = country) ?: CountryModel(country, null, null)
    }

    fun getCountryModelFromPrefix(prefix: String?): CountryModel? =
        current.firstOrNull { it.prefix == prefix }

    fun search(countries: List<CountryModel>, query: String, callingCodes: Boolean): List<CountryModel> {
        val available = if (callingCodes) countries.filter { !it.prefix.isNullOrBlank() && !it.legacy } else countries
        if (query.isBlank()) return available
        val key = searchKey(query)
        if (key.isBlank()) return emptyList()
        val tokens = key.split(' ').filter { it.isNotBlank() }
        val digits = query.filter(Char::isDigit)
        val isDialQuery = digits.isNotEmpty() && query.all { it.isDigit() || it in "+- ()" }
        return available.filter { country ->
            val text = searchIndex[country.code] ?: searchKey("${country.displayName} ${country.name.orEmpty()} ${country.code.orEmpty()}")
            if (isDialQuery) callingCodes && country.prefix?.removePrefix("+")?.startsWith(digits) == true
            else tokens.all { it in text }
        }
    }

    internal fun searchKey(value: String): String = buildString {
        for (c in value.lowercase()) append(when (c) {
            'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a'
            'ç' -> 'c'
            'è', 'é', 'ê', 'ë' -> 'e'
            'ì', 'í', 'î', 'ï' -> 'i'
            'ñ' -> 'n'
            'ò', 'ó', 'ô', 'õ', 'ö', 'ø' -> 'o'
            'ù', 'ú', 'û', 'ü' -> 'u'
            'ý', 'ÿ' -> 'y'
            else -> if (c.isLetterOrDigit()) c else ' '
        })
    }.trim()
}
