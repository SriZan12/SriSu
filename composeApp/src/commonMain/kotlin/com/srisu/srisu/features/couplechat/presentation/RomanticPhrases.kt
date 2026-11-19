package com.srisu.srisu.features.couplechat.presentation

/** Ordered from quietest to strongest. A message chooses only its strongest match. */
internal enum class RomanticIntensity { SWEET, AFFECTIONATE, DEEP }

/** Edit these lists to extend matching; no animation or transport code belongs here. */
internal object RomanticPhrases {
    val phrases = mapOf(
        RomanticIntensity.SWEET to listOf(
            "miss you", "missing you", "missyou", "thinking of you", "thinking about you", "thought of you",
            "wish you were here", "cant wait to see you", "cannot wait to see you", "you make me smile",
            "sending hugs", "hugs and kisses", "sweet dreams", "goodnight sweetheart",
        ),
        RomanticIntensity.AFFECTIONATE to listOf(
            "i love you", "love you", "love you too", "iloveyou", "my love", "my heart", "be mine",
            "adore you", "you mean so much to me", "you are my favorite person", "youre my favorite person",
            "you are my favourite person", "youre my favourite person", "you have my heart", "kiss you",
            "love of my life", "my soulmate", "my darling", "my sweetheart",
        ),
        RomanticIntensity.DEEP to listOf(
            "love you so much", "love you very much", "love you more than anything", "love you endlessly",
            "love you forever", "be mine forever", "marry me", "will you marry me", "forever yours",
            "yours forever", "together forever", "always and forever", "you are my everything",
            "youre my everything", "cant live without you", "cannot live without you", "spend my life with you",
        ),
    )
    // Broad words only match by themselves, so "the download takes forever" stays quiet.
    val standaloneKeywords = mapOf(
        RomanticIntensity.SWEET to listOf("xoxo", "hugs", "kisses"),
        RomanticIntensity.AFFECTIONATE to listOf("forever", "sweetheart", "darling", "soulmate"),
    )
    val emojiTriggers = listOf("❤", "😍", "😘", "🥰", "💕", "💖", "💗", "💞", "💘")
    private val aliases = mapOf("u" to "you", "ya" to "you", "ur" to "your", "r" to "are",
        "ily" to "i love you", "ilu" to "i love you", "ilysm" to "i love you so much",
        "ily2" to "i love you too", "ilyt" to "i love you too", "imy" to "i miss you", "4ever" to "forever")
    private val love = Regex("(?:lo+v+e+|lu+v+)")
    private val miss = Regex("mi+s{2,}")
    private val you = Regex("y+o+u+")
    private val so = Regex("so+")
    private val negation = Regex("(?:^| )(?:dont|do not|not|never|no longer|stop) (?:really |actually |even |ever )?(?:love|miss|adore|marry|kiss|want|be mine)(?: |$)")
    private val urls = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val clauses = Regex("[.!?;\\n]+")

    private fun normalize(text: String): String = text.lowercase().replace("’", "'").replace("'", "")
        .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }
        .joinToString(" ") { token -> aliases[token] ?: when {
            love.matches(token) -> "love"
            miss.matches(token) -> "miss"
            you.matches(token) -> "you"
            so.matches(token) -> "so"
            else -> token
        } }

    fun match(text: String): RomanticIntensity? {
        var strongest: RomanticIntensity? = null
        // Message length is already bounded by chat, but keep this utility bounded too.
        for (clause in urls.replace(text.take(4000), "").replace("\uFE0F", "").split(clauses)) {
            val words = normalize(clause)
            if (negation.containsMatchIn(words)) continue
            val padded = " $words "
            var match = RomanticIntensity.entries.reversed().firstOrNull { intensity ->
                phrases[intensity].orEmpty().any { " $it " in padded } || words in standaloneKeywords[intensity].orEmpty()
            }
            val emojiCount = emojiTriggers.sumOf { trigger -> clause.windowed(trigger.length).count { it == trigger } }
            val emojiMatch = when {
                "❤\u200D🔥" in clause || emojiCount >= 3 -> RomanticIntensity.DEEP
                emojiCount > 0 -> RomanticIntensity.AFFECTIONATE
                else -> null
            }
            if (emojiMatch != null && (match == null || emojiMatch > match)) match = emojiMatch
            if (match != null && (strongest == null || match > strongest)) strongest = match
        }
        return strongest
    }
}

internal data class RomanticEffect(val accountId: Long, val roomId: String, val messageId: String, val intensity: RomanticIntensity) {
    val seed: Int get() = messageId.hashCode()
}
