package com.srisu.srisu.features.sparks.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable data class SparkQuestion(val id: String, val deck: String, val text: String)
@Serializable data class SparkDeck(val id: String, val name: String, val subtitle: String, val questions: List<SparkQuestion>)
@Serializable data class SparkMember(val id: Long, val name: String, val photo: String? = null)
@Serializable data class SparkHome(val decks: List<SparkDeck> = emptyList(), val couple: Long? = null,
    val members: List<SparkMember> = emptyList(), val active: SparkSession? = null,
    val recent: List<SparkSession> = emptyList(), val unavailable: String? = null)
@Serializable data class SparkAnswer(val author: Long, @SerialName("sender_matrix_id") val sender: String,
    val envelope: JsonObject)
@Serializable data class SparkSession(val id: String, val couple: Long, val creator: Long,
    @SerialName("room_id") val roomId: String, @SerialName("matrix_room_id") val matrixRoomId: String,
    @SerialName("membership_epoch") val epoch: Long, @SerialName("question_id") val questionId: String,
    val question: String, val deck: String, val state: String, val revision: Long,
    @SerialName("submitted_by") val submittedBy: List<Long> = emptyList(),
    val answers: List<SparkAnswer> = emptyList(), @SerialName("card_event_id") val cardEventId: String = "") {
    fun submitted(account: Long?) = account in submittedBy
    val open get() = state == "open"
    val revealed get() = state == "revealed"
}

/** Inside the Megolm ciphertext; binds the answer to its immutable question and audience. */
@Serializable internal data class SparkPlaintext(val version: Int = 1, val spark: String, val couple: Long,
    val epoch: Long, val questionId: String, val question: String, val sender: String, val answer: String) {
    fun validated(session: SparkSession, expectedSender: String): String {
        require(version == 1 && spark == session.id && couple == session.couple && epoch == session.epoch &&
            questionId == session.questionId && question == session.question && sender == expectedSender)
        require(answer.isNotBlank() && answer.length <= SPARK_ANSWER_LIMIT)
        return answer
    }
}
const val SPARK_ANSWER_LIMIT = 2000

@Serializable data class SparkReference(val id: String, val couple: Long) {
    val url get() = "https://srisu.app/sparks/$couple/$id"
    companion object {
        private val pattern = Regex("^https://srisu\\.app/sparks/([1-9][0-9]*)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")
        fun parse(value: String?): SparkReference? {
            val match = pattern.matchEntire(value ?: return null) ?: return null
            val couple = match.groupValues[1].toLongOrNull() ?: return null
            return SparkReference(match.groupValues[2], couple)
        }
    }
}

/** Stored only in the existing protected, account-scoped platform session storage. */
@Serializable data class SparkDraft(val text: String = "", val envelope: JsonObject? = null)
@Serializable data class SparkStartIntent(val id: String, val couple: Long, val question: String)

@Serializable data class SparkHistoryPage(val ids: List<String> = emptyList(), val next: String? = null)
