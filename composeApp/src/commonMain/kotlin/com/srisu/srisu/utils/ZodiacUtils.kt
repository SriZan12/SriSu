package com.srisu.srisu.utils

import org.jetbrains.compose.resources.DrawableResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.aquarius
import srisu.composeapp.generated.resources.aries
import srisu.composeapp.generated.resources.cancer
import srisu.composeapp.generated.resources.capricorn
import srisu.composeapp.generated.resources.gemini
import srisu.composeapp.generated.resources.leo
import srisu.composeapp.generated.resources.libra
import srisu.composeapp.generated.resources.pisces
import srisu.composeapp.generated.resources.sagittarus
import srisu.composeapp.generated.resources.scorpius
import srisu.composeapp.generated.resources.taurus
import srisu.composeapp.generated.resources.virgo

object ZodiacUtils {

    data class ZodiacSign(
        val sign: String,
        val logo: DrawableResource,
        val title: String,
        val zodiacDescription: String,
        val startMonth: Int,
        val startDay: Int,
        val endMonth: Int,
        val endDay: Int
    )

    fun getZodiacSignList(): List<ZodiacSign> {
        return listOf(
            ZodiacSign(
                sign = "ARIES",
                logo = Res.drawable.aries,
                title = "You're an Aries",
                zodiacDescription = "Aries are fearless leaders who thrive on challenges. They are known for their passion, determination, and confidence, often inspiring others to take action.",
                startMonth = 3,
                startDay = 21,
                endMonth = 4,
                endDay = 19
            ),
            ZodiacSign(
                sign = "TAURUS",
                logo = Res.drawable.taurus,
                title = "You're a Taurus",
                zodiacDescription = "Taurus is practical and grounded, known for being reliable and patient. They value stability and are often seen as the backbone of their relationships and work environments.",
                startMonth = 4,
                startDay = 20,
                endMonth = 5,
                endDay = 20
            ),
            ZodiacSign(
                sign = "GEMINI",
                logo = Res.drawable.gemini,
                title = "You're a Gemini",
                zodiacDescription = "Geminis are curious, adaptable, and great communicators. They are social butterflies who love variety and thrive in dynamic environments.",
                startMonth = 5,
                startDay = 21,
                endMonth = 6,
                endDay = 20
            ),
            ZodiacSign(
                sign = "CANCER",
                logo = Res.drawable.cancer,
                title = "You're a Cancer",
                zodiacDescription = "Cancer is deeply intuitive and caring, often putting the needs of others first. They are protective of their loved ones and create a nurturing environment.",
                startMonth = 6,
                startDay = 21,
                endMonth = 7,
                endDay = 22
            ),
            ZodiacSign(
                sign = "LEO",
                logo = Res.drawable.leo,
                title = "You're a Leo",
                zodiacDescription = "Leos are charismatic and natural-born leaders. They are confident, enthusiastic, and love being in the spotlight, inspiring others with their warmth and creativity.",
                startMonth = 7,
                startDay = 23,
                endMonth = 8,
                endDay = 22
            ),
            ZodiacSign(
                sign = "VIRGO",
                logo = Res.drawable.virgo,
                title = "You're a Virgo",
                zodiacDescription = "Virgos are analytical and detail-oriented. They are known for their practicality, strong work ethic, and desire to help others and achieve perfection.",
                startMonth = 8,
                startDay = 23,
                endMonth = 9,
                endDay = 22
            ),
            ZodiacSign(
                sign = "LIBRA",
                logo = Res.drawable.libra,
                title = "You're a Libra",
                zodiacDescription = "Libras are charming and diplomatic, seeking balance and harmony in all areas of life. They value relationships, beauty, and fairness.",
                startMonth = 9,
                startDay = 23,
                endMonth = 10,
                endDay = 22
            ),
            ZodiacSign(
                sign = "SCORPIUS",
                logo = Res.drawable.scorpius,
                title = "You're a Scorpio",
                zodiacDescription = "Scorpios are passionate and determined, with a deep emotional intensity. They are loyal and protective, but can also be mysterious and ambitious.",
                startMonth = 10,
                startDay = 23,
                endMonth = 11,
                endDay = 21
            ),
            ZodiacSign(
                sign = "SAGITTARIUS",
                logo = Res.drawable.sagittarus,
                title = "You're a Sagittarius",
                zodiacDescription = "Sagittarius is adventurous, optimistic, and loves freedom. They are truth-seekers, driven by their desire for knowledge, travel, and new experiences.",
                startMonth = 11,
                startDay = 22,
                endMonth = 12,
                endDay = 21
            ),
            ZodiacSign(
                sign = "CAPRICORN",
                logo = Res.drawable.capricorn,
                title = "You're a Capricorn",
                zodiacDescription = "Capricorns are disciplined and goal-oriented. They are ambitious, practical, and known for their perseverance and ability to overcome challenges.",
                startMonth = 12,
                startDay = 22,
                endMonth = 1,
                endDay = 19
            ),
            ZodiacSign(
                sign = "AQUARIUS",
                logo = Res.drawable.aquarius,
                title = "You're an Aquarius",
                zodiacDescription = "Aquarius is innovative, independent, and forward-thinking. They are visionaries who value individuality and often strive to bring positive change to the world.",
                startMonth = 1,
                startDay = 20,
                endMonth = 2,
                endDay = 18
            ),
            ZodiacSign(
                sign = "PISCES",
                logo = Res.drawable.pisces,
                title = "You're a Pisces",
                zodiacDescription = "Pisces are empathetic and artistic, known for their compassion and emotional depth. They are dreamers with a rich inner world and a desire to help others.",
                startMonth = 2,
                startDay = 19,
                endMonth = 3,
                endDay = 20
            )
        )

    }

    fun getZodiacSignImage(name: String): DrawableResource? {
        val zodiacSign = getZodiacSignList().find { it.sign == name }
        val zodiacSignLogo = zodiacSign?.logo
        return zodiacSignLogo

    }

}