package com.srisu.srisu.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.DrawableResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.aquarus
import srisu.composeapp.generated.resources.aries
import srisu.composeapp.generated.resources.cancer
import srisu.composeapp.generated.resources.capricorn
import srisu.composeapp.generated.resources.gemini
import srisu.composeapp.generated.resources.leo
import srisu.composeapp.generated.resources.libra
import srisu.composeapp.generated.resources.pisces
import srisu.composeapp.generated.resources.sagittarus
import srisu.composeapp.generated.resources.scorpio
import srisu.composeapp.generated.resources.taurus
import srisu.composeapp.generated.resources.virgo

object ZodiacUtils {

    data class ZodiacSign(
        val key: String,
        val name: String,
        val symbol: String,
        val logo: DrawableResource,
        val title: String,
        val dateRange: String,
        val description: String,
        val traits: List<String>,
        val startMonth: Int,
        val startDay: Int,
        val endMonth: Int,
        val endDay: Int
    )

    fun getZodiacSignList(): List<ZodiacSign> {
        return listOf(
            ZodiacSign(
                key = "ARIES",
                name = "Aries",
                symbol = "♈",
                logo = Res.drawable.aries,
                title = "Your zodiac sign",
                dateRange = "March 21 – April 19",
                description = "You're a force of nature. Bold, passionate, and the first to leap before looking. People are drawn to your fire.",
                traits = listOf("Adventurous", "Confident", "Energetic"),
                startMonth = 3,
                startDay = 21,
                endMonth = 4,
                endDay = 19
            ),
            ZodiacSign(
                key = "TAURUS",
                name = "Taurus",
                symbol = "♉",
                logo = Res.drawable.taurus,
                title = "Your zodiac sign",
                dateRange = "April 20 – May 20",
                description = "You're grounded, loyal, and deeply steady. People trust your calm energy and feel safe around your presence.",
                traits = listOf("Loyal", "Patient", "Grounded"),
                startMonth = 4,
                startDay = 20,
                endMonth = 5,
                endDay = 20
            ),
            ZodiacSign(
                key = "GEMINI",
                name = "Gemini",
                symbol = "♊",
                logo = Res.drawable.gemini,
                title = "Your zodiac sign",
                dateRange = "May 21 – June 20",
                description = "You're curious, expressive, and full of ideas. Your energy keeps conversations alive and hearts interested.",
                traits = listOf("Curious", "Playful", "Expressive"),
                startMonth = 5,
                startDay = 21,
                endMonth = 6,
                endDay = 20
            ),
            ZodiacSign(
                key = "CANCER",
                name = "Cancer",
                symbol = "♋",
                logo = Res.drawable.cancer,
                title = "Your zodiac sign",
                dateRange = "June 21 – July 22",
                description = "You're caring, intuitive, and emotionally deep. You love with sincerity and protect what matters most.",
                traits = listOf("Caring", "Intuitive", "Protective"),
                startMonth = 6,
                startDay = 21,
                endMonth = 7,
                endDay = 22
            ),
            ZodiacSign(
                key = "LEO",
                name = "Leo",
                symbol = "♌",
                logo = Res.drawable.leo,
                title = "Your zodiac sign",
                dateRange = "July 23 – August 22",
                description = "You're warm, magnetic, and born to shine. Your confidence makes people feel inspired and seen.",
                traits = listOf("Radiant", "Brave", "Generous"),
                startMonth = 7,
                startDay = 23,
                endMonth = 8,
                endDay = 22
            ),
            ZodiacSign(
                key = "VIRGO",
                name = "Virgo",
                symbol = "♍",
                logo = Res.drawable.virgo,
                title = "Your zodiac sign",
                dateRange = "August 23 – September 22",
                description = "You're thoughtful, practical, and quietly powerful. You notice the little things that make love feel intentional.",
                traits = listOf("Thoughtful", "Reliable", "Practical"),
                startMonth = 8,
                startDay = 23,
                endMonth = 9,
                endDay = 22
            ),
            ZodiacSign(
                key = "LIBRA",
                name = "Libra",
                symbol = "♎",
                logo = Res.drawable.libra,
                title = "Your zodiac sign",
                dateRange = "September 23 – October 22",
                description = "You're charming, balanced, and drawn to beauty. You bring harmony into relationships and make connection feel effortless.",
                traits = listOf("Charming", "Balanced", "Romantic"),
                startMonth = 9,
                startDay = 23,
                endMonth = 10,
                endDay = 22
            ),
            ZodiacSign(
                key = "SCORPIO",
                name = "Scorpio",
                symbol = "♏",
                logo = Res.drawable.scorpio,
                title = "Your zodiac sign",
                dateRange = "October 23 – November 21",
                description = "You're intense, loyal, and emotionally fearless. People are drawn to your mystery and depth.",
                traits = listOf("Loyal", "Magnetic", "Intense"),
                startMonth = 10,
                startDay = 23,
                endMonth = 11,
                endDay = 21
            ),
            ZodiacSign(
                key = "SAGITTARIUS",
                name = "Sagittarius",
                symbol = "♐",
                logo = Res.drawable.sagittarus,
                title = "Your zodiac sign",
                dateRange = "November 22 – December 21",
                description = "You're adventurous, honest, and full of life. You chase meaning, freedom, and unforgettable moments.",
                traits = listOf("Adventurous", "Honest", "Free"),
                startMonth = 11,
                startDay = 22,
                endMonth = 12,
                endDay = 21
            ),
            ZodiacSign(
                key = "CAPRICORN",
                name = "Capricorn",
                symbol = "♑",
                logo = Res.drawable.capricorn,
                title = "Your zodiac sign",
                dateRange = "December 22 – January 19",
                description = "You're ambitious, steady, and deeply dependable. You build love with patience, effort, and quiet devotion.",
                traits = listOf("Ambitious", "Loyal", "Disciplined"),
                startMonth = 12,
                startDay = 22,
                endMonth = 1,
                endDay = 19
            ),
            ZodiacSign(
                key = "AQUARIUS",
                name = "Aquarius",
                symbol = "♒",
                logo = Res.drawable.aquarus,
                title = "Your zodiac sign",
                dateRange = "January 20 – February 18",
                description = "You're original, independent, and beautifully unpredictable. Your mind sees possibilities others miss.",
                traits = listOf("Original", "Independent", "Visionary"),
                startMonth = 1,
                startDay = 20,
                endMonth = 2,
                endDay = 18
            ),
            ZodiacSign(
                key = "PISCES",
                name = "Pisces",
                symbol = "♓",
                logo = Res.drawable.pisces,
                title = "Your zodiac sign",
                dateRange = "February 19 – March 20",
                description = "You're dreamy, gentle, and emotionally wise. Your heart feels deeply and your presence brings comfort.",
                traits = listOf("Dreamy", "Gentle", "Empathetic"),
                startMonth = 2,
                startDay = 19,
                endMonth = 3,
                endDay = 20
            )
        )
    }

    fun getZodiacFromName(name: String?): ZodiacSign? {
        if (name.isNullOrBlank()) return null

        return getZodiacSignList().find {
            it.key.equals(name, ignoreCase = true) ||
                    it.name.equals(name, ignoreCase = true)
        }
    }

    fun getZodiacSignImage(name: String): DrawableResource? {
        return getZodiacFromName(name)?.logo
    }

    fun getZodiacFromDate(
        month: Int,
        day: Int
    ): ZodiacSign? {
        return getZodiacSignList().find { zodiac ->
            isDateInZodiacRange(
                month = month,
                day = day,
                startMonth = zodiac.startMonth,
                startDay = zodiac.startDay,
                endMonth = zodiac.endMonth,
                endDay = zodiac.endDay
            )
        }
    }

    fun getZodiacFromDob(dob: String?): ZodiacSign? {
        if (dob.isNullOrBlank()) return null

        return runCatching {
            val date = LocalDate.parse(dob)
            getZodiacFromDate(
                month = date.month.number,
                day = date.day
            )
        }.getOrNull()
    }

    private fun isDateInZodiacRange(
        month: Int,
        day: Int,
        startMonth: Int,
        startDay: Int,
        endMonth: Int,
        endDay: Int
    ): Boolean {
        val value = month * 100 + day
        val start = startMonth * 100 + startDay
        val end = endMonth * 100 + endDay

        return if (start <= end) {
            value in start..end
        } else {
            value !in (end + 1)..<start
        }
    }
}