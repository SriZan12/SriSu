package com.srisu.srisu.features.couplechat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Private typed cards. Source references confer no domain permission. */
@Serializable sealed interface ChatCard {
    @Serializable @SerialName("ask") data class Ask(val question: String, val options: List<String> = emptyList()) : ChatCard
    @Serializable @SerialName("spark") data class Spark(val deck: String, val question: String) : ChatCard
    @Serializable @SerialName("plan") data class Plan(val coupleId: Long, val planId: Long) : ChatCard
    @Serializable @SerialName("moment") data class Moment(val momentId: Long, val expiresAt: String) : ChatCard
    @Serializable @SerialName("challenge") data class Challenge(val sourceId: String) : ChatCard
}
@Serializable data class CardReply(val answer: String = "", val vote: Int? = null)
@Serializable data class SparkStatus(val id: String, val submitted: Boolean,
    @SerialName("partner_submitted") val partnerSubmitted: Boolean, val revealed: Boolean,
    val answers: List<ChatReceivedOperation> = emptyList())
@Serializable data class LocalSparkState(val submitted: Boolean = false, val partnerSubmitted: Boolean = false,
    val revealed: Boolean = false, val answers: Map<Long,String> = emptyMap(), val error: String? = null)
@Serializable internal data class PendingSparkAnswer(val operationId: String, val answer: String)
@Serializable data class SharedMoment(val id: Long,val couple: Long,val title: String? = null,val caption: String,
    @SerialName("expires_at") val expiresAt: String,val photos: List<SharedMomentPhoto> = emptyList())
@Serializable data class SharedMomentPhoto(val id: Long,val image: String)
@Serializable data class SharedMoments(val results: List<SharedMoment>)

object SparkDecks {
    val questions = linkedMapOf(
        "Everyday us" to listOf("What small thing made you smile today?", "What would make tomorrow feel a little easier?", "What is one thing you appreciated about us this week?"),
        "Dream together" to listOf("Where would you like us to spend a quiet weekend?", "What is something new you would love to try together?", "What does a happy ordinary day together look like to you?"),
        "A little closer" to listOf("When do you feel most understood by me?", "What is a favorite memory of us?", "What is one kind thing we can do for each other this week?")
    )
}
