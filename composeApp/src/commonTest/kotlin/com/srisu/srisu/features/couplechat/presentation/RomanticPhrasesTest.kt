package com.srisu.srisu.features.couplechat.presentation

import kotlin.test.*

class RomanticPhrasesTest {
    @Test fun everyCategorizedPhraseAndKeywordHasItsIntendedIntensity() {
        for((intensity, phrases) in RomanticPhrases.phrases) for(phrase in phrases)
            assertEquals(intensity,RomanticPhrases.match(phrase),phrase)
        for((intensity, words) in RomanticPhrases.standaloneKeywords) for(word in words)
            assertEquals(intensity,RomanticPhrases.match(word),word)
    }
    @Test fun casePunctuationAndCasualSpellingsNormalizeWithoutSubstringMatches() {
        for(text in listOf("I LOVE YOU!", "luv u", "I loooove yooou", "ily", "ilu", "ily2", "love-you"))
            assertEquals(RomanticIntensity.AFFECTIONATE,RomanticPhrases.match(text),text)
        for(text in listOf("imY", "missss u", "Thinking OF ya", "can’t wait to see you"))
            assertEquals(RomanticIntensity.SWEET,RomanticPhrases.match(text),text)
        for(text in listOf("ilysm", "i luv u sooo much", "be mine 4ever"))
            assertEquals(RomanticIntensity.DEEP,RomanticPhrases.match(text),text)
    }
    @Test fun strongestMatchWinsAndEmojiAreInputOnly() {
        assertEquals(RomanticIntensity.DEEP,RomanticPhrases.match("Miss you! I love you so much 😘"))
        for(emoji in RomanticPhrases.emojiTriggers+"❤️")
            assertEquals(RomanticIntensity.AFFECTIONATE,RomanticPhrases.match(emoji),emoji)
        assertEquals(RomanticIntensity.DEEP,RomanticPhrases.match("❤️‍🔥"))
        assertEquals(RomanticIntensity.DEEP,RomanticPhrases.match("❤️❤️❤️"))
    }
    @Test fun unrelatedWordsLinksAndExplicitNegationsStayQuiet() {
        for(text in listOf("", "love your photo", "the delivery takes forever", "miss your train", "I don't love you",
            "I do not love you", "I never miss you", "don't marry me", "not really in love", "https://test.invalid/love-you"))
            assertNull(RomanticPhrases.match(text),text)
    }
}
