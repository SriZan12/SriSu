package com.srisu.srisu.features.sparks

import androidx.compose.runtime.Composable
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.features.sparks.presentation.*
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

internal object SparkFixtures {
    private const val CATALOGUE = """[{"id": "deep", "name": "Deep", "subtitle": "A little closer", "questions": [{"id": "deep-1-v1", "deck": "deep", "text": "What do you admire about me that you’ve never told me?"}, {"id": "deep-2-v1", "deck": "deep", "text": "When do you feel most understood by me?"}, {"id": "deep-3-v1", "deck": "deep", "text": "What helps you feel loved on a difficult day?"}, {"id": "deep-4-v1", "deck": "deep", "text": "What is something you would like us to understand better about each other?"}, {"id": "deep-5-v1", "deck": "deep", "text": "What small thing do I do that means more than I realise?"}, {"id": "deep-6-v1", "deck": "deep", "text": "What does feeling safe together mean to you?"}]}, {"id": "memories", "name": "Memories", "subtitle": "Back to us", "questions": [{"id": "memories-1-v1", "deck": "memories", "text": "Which ordinary day together would you happily live again?"}, {"id": "memories-2-v1", "deck": "memories", "text": "What do you remember most clearly about our first conversation?"}, {"id": "memories-3-v1", "deck": "memories", "text": "When did you first feel that we made a good team?"}, {"id": "memories-4-v1", "deck": "memories", "text": "What is a little moment with me that you still smile about?"}, {"id": "memories-5-v1", "deck": "memories", "text": "Which place holds a special memory of us?"}, {"id": "memories-6-v1", "deck": "memories", "text": "What song takes you back to a moment we shared?"}]}, {"id": "playful", "name": "Playful", "subtitle": "Make room for laughter", "questions": [{"id": "playful-1-v1", "deck": "playful", "text": "If we had a whole day with no responsibilities, what would we do?"}, {"id": "playful-2-v1", "deck": "playful", "text": "What would our completely impractical dream date look like?"}, {"id": "playful-3-v1", "deck": "playful", "text": "If we opened a tiny shop together, what would we sell?"}, {"id": "playful-4-v1", "deck": "playful", "text": "What is our funniest shared habit?"}, {"id": "playful-5-v1", "deck": "playful", "text": "Which fictional world would you visit with me for a day?"}, {"id": "playful-6-v1", "deck": "playful", "text": "What would you put on a surprise picnic menu for us?"}]}, {"id": "future", "name": "Future", "subtitle": "Things to look forward to", "questions": [{"id": "future-1-v1", "deck": "future", "text": "What is one small tradition you would love us to start?"}, {"id": "future-2-v1", "deck": "future", "text": "What would a lovely ordinary weekend together look like a year from now?"}, {"id": "future-3-v1", "deck": "future", "text": "What is something new you would like us to learn together?"}, {"id": "future-4-v1", "deck": "future", "text": "Which place would you love to explore with me?"}, {"id": "future-5-v1", "deck": "future", "text": "What is one way we can make more time for each other?"}, {"id": "future-6-v1", "deck": "future", "text": "What shared dream would you like to take one small step towards?"}]}]"""
    val session = SparkSession("11111111-1111-4111-8111-111111111111", 3, 2,
        "22222222-2222-4222-8222-222222222222", "!spark:test.invalid", 1,
        "deep-1-v1", "What do you admire about me that you’ve never told me?", "deep", "open", 1, cardEventId = "\$card")
    val home = SparkHome(Json.decodeFromString<List<SparkDeck>>(CATALOGUE), 3,
        listOf(SparkMember(1, "Alex"), SparkMember(2, "Sam")))
    val envelope = buildJsonObject { put("algorithm", "m.megolm.v1.aes-sha2"); put("ciphertext", "synthetic-ciphertext"); put("session_id", "synthetic-session") }
    val one = SparkAnswer(1, "@one:test.invalid", envelope)
    val two = SparkAnswer(2, "@two:test.invalid", envelope)
}
@Composable internal fun SparkRenderFixture(name: String) {
    if (name.startsWith("recovery")) {
        SparkRecoveryContent(SparkRecoveryUi(status = if ("ready" in name) com.srisu.srisu.features.chat.matrix.SparkRecoveryStatus.Ready else com.srisu.srisu.features.chat.matrix.SparkRecoveryStatus.Locked,
            error = if ("error" in name) "Recovery could not finish. Check your connection and recovery key, then retry." else null),
            true, {}, {}, {}, {}, {}, {}, {}, {}, {})
        return
    }
    val session = SparkFixtures.session
    val base = SparkState(home = SparkFixtures.home, account = 1, loading = false, fresh = true, secure = true)
    val state = when {
        name.startsWith("answer") -> base.copy(session = session.copy(submittedBy = listOf(2)), draft = SparkDraft("The way you make room for people. You listen with your whole heart, even on the busiest days."))
        name.startsWith("waiting") -> base.copy(session = session.copy(submittedBy = listOf(1)), answers = mapOf(1L to "You make the little things feel important."))
        name.startsWith("reveal") -> base.copy(session = session.copy(state = "revealed", submittedBy = listOf(1, 2)), answers = mapOf(
            1L to "The way you make room for people. You listen with your whole heart, even on the busiest days.",
            2L to "Your quiet courage. You try again even when something is hard, and you always make me feel like we’re a team."))
        name == "keys" -> base.copy(session = session.copy(state = "revealed", submittedBy = listOf(1, 2)), keyError = true)
        name == "failed" -> base.copy(session = session, draft = SparkDraft("I love how we find something to laugh about.", SparkFixtures.envelope), error = "Your submission could not be confirmed. Your draft is kept; retry to check it.")
        name == "skipped" -> base.copy(session = session.copy(state = "skipped"))
        name == "unpaired" -> base.copy(home = SparkFixtures.home.copy(couple = null))
        else -> base
    }
    SparkContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}
internal open class FakeSparks : SparkSource {
    override val changes = MutableStateFlow(0L)
    override fun changed() { changes.value++ }
    var session = SparkFixtures.session
    var failAnswer = false
    var received = mutableListOf<JsonObject>()
    override suspend fun home() = NetworkAPIResult.Success<SparkHome?>(SparkFixtures.home)
    override suspend fun get(id: String) = NetworkAPIResult.Success<SparkSession?>(session)
    override suspend fun start(intent: SparkStartIntent) = NetworkAPIResult.Success<SparkSession?>(session)
    override suspend fun answer(id: String, envelope: JsonObject): NetworkAPIResult<SparkSession?> {
        received += envelope
        if (failAnswer) return com.srisu.srisu.features.moments.MomentFixtures.failed()
        session = session.copy(submittedBy = listOf(1), answers = listOf(SparkFixtures.one), revision = 2)
        return NetworkAPIResult.Success(session)
    }
    override suspend fun skip(id: String): NetworkAPIResult<SparkSession?> { session = session.copy(state = "skipped", revision = 2); return NetworkAPIResult.Success(session) }
    override suspend fun card(id: String, eventId: String) = NetworkAPIResult.Success<SparkSession?>(session.copy(cardEventId = eventId))
}
