package com.srisu.srisu.components

import com.srisu.srisu.utils.Country
import kotlin.test.*

class CountryCatalogueTest {
    private val countries = Country.getAllCountriesFromJson()
    @Test fun coversCurrentRegionsWithoutDuplicatingSharedCallingCodes() {
        assertEquals(250, countries.size)
        assertEquals(250, countries.map { it.code }.toSet().size)
        assertSame(countries, Country.getAllCountriesFromJson())
        listOf("SS", "BQ", "CW", "SX", "XK", "BV", "HM", "TF", "UM", "EH").forEach { code -> assertTrue(countries.any { it.code == code }) }
        assertEquals("+357", Country.getCountryModelFromCode("cy")?.prefix)
        assertEquals("+592", Country.getCountryModelFromCode("GY")?.prefix)
        assertEquals("+1345", Country.getCountryModelFromCode("KY")?.prefix)
        assertEquals("+39", Country.getCountryModelFromCode("VA")?.prefix)
        assertTrue(Country.search(countries, "+1", true).size > 1)
        assertTrue(Country.search(countries, "", true).all { !it.prefix.isNullOrBlank() })
        assertTrue(Country.search(countries, "Bouvet", true).isEmpty())
        assertEquals("BV", Country.search(countries, "Bouvet", false).single().code)
    }
    @Test fun searchesNamesAliasesCodesAccentsAndCallingPrefixes() {
        mapOf("UK" to "GB", "USA" to "US", "Cote d Ivoire" to "CI", "Türkiye" to "TR", "turkiye" to "TR", "curacao" to "CW", "+977" to "NP", "9 7 7" to "NP").forEach { (query, code) ->
            assertTrue(Country.search(countries, query, true).any { it.code == code }, query)
        }
        assertTrue(Country.search(countries, "no matching country here", false).isEmpty())
        assertTrue(Country.search(countries, "+++", true).isEmpty())
        assertEquals(countries, Country.search(countries, "  ", false))
    }
    @Test fun oldUnknownAndModernNamesRoundTripWithoutChangingStoredValues() {
        listOf("Czech Republic", "Swaziland", "Turkey", "Netherlands Antilles", "Some legacy place", "Curaçao", "Türkiye", "  Nepal  ").forEach { saved ->
            assertEquals(saved, Country.getCountryModelFromName(saved)?.name)
        }
        assertEquals("TR", Country.getCountryModelFromName("Türkiye")?.code)
        assertTrue(Country.getCountryModelFromName("Netherlands Antilles")!!.legacy)
        assertNull(Country.getCountryModelFromName(" "))
    }
}
