package com.srisu.srisu.features.home.dashboard.state

import androidx.compose.runtime.Immutable
import com.srisu.srisu.features.challenges.data.ChallengeHome
import com.srisu.srisu.features.challenges.data.ChallengeSession
import com.srisu.srisu.features.moments.data.MomentHome
import com.srisu.srisu.features.plans.data.Plan
import com.srisu.srisu.features.plans.data.PlanHome
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Immutable, display-ready Home state. Copy is resolved from string resources in the UI. */
@Immutable
data class HomeUiState(
    val status: HomeStatus = HomeStatus.LOADING,
    val daypart: HomeDaypart = HomeDaypart.MORNING,
    val name: String = "",
    val couple: HomeCoupleUi? = null,
    val refreshing: Boolean = false,
    val stale: Boolean = false,
)

enum class HomeStatus { LOADING, READY, FAILED }
enum class HomeDaypart { MORNING, AFTERNOON, EVENING }

@Immutable
data class HomeCoupleUi(
    val id: Long,
    val partner: HomePersonUi,
    val daysTogether: Int?,
    val moments: List<HomeMomentUi>,
    val momentsFresh: Boolean,
    /** No backend/Matrix summary feeds this yet; previews use fixture data. */
    val conversation: HomeConversationUi?,
    val challenge: HomeSection<HomeChallengeUi>,
    val plan: HomeSection<HomePlanUi>,
    val pendingInvitations: Int,
)

@Immutable
data class HomePersonUi(val id: Long, val name: String, val photoUrl: String?)

@Immutable
data class HomeMomentUi(val person: HomePersonUi, val own: Boolean, val activeCount: Int, val unseenCount: Int) {
    val hasUnseen get() = unseenCount > 0
}

@Immutable
data class HomeConversationUi(
    val text: String,
    val sentAt: String,
    val unread: Int,
    val partnerOnline: Boolean,
    val photoUrls: List<String>,
)

@Immutable
sealed interface HomeSection<out T> {
    data object Loading : HomeSection<Nothing>
    data object Failed : HomeSection<Nothing>
    data object Empty : HomeSection<Nothing>
    data class Ready<T>(val value: T) : HomeSection<T>
}

@Immutable
data class HomeChallengeUi(val id: String, val title: String, val status: HomeChallengeStatus,
    val members: List<HomeChallengeMemberUi>)

@Immutable
data class HomeChallengeMemberUi(val person: HomePersonUi, val confirmed: Boolean)

enum class HomeChallengeStatus { INVITED, ACTIVE, ONE_CONFIRMED, PAUSED, COMPLETED, ENDED }

@Immutable
data class HomePlanUi(val id: Long, val title: String, val startsAt: LocalDateTime?, val status: HomePlanStatus)

enum class HomePlanStatus { BOTH_GOING, AWAITING_REPLY, CHANGE_SUGGESTED, UPCOMING }

/** Raw source snapshot owned by [com.srisu.srisu.features.home.dashboard.vm.HomeViewModel]. */
data class HomeData(
    val name: String = "",
    val moments: MomentHome? = null,
    val momentsLoading: Boolean = true,
    val momentsFailed: Boolean = false,
    val momentsFresh: Boolean = false,
    val challenge: ChallengeHome? = null,
    val challengeLoading: Boolean = true,
    val challengeFailed: Boolean = false,
    val plan: PlanHome? = null,
    val planLoading: Boolean = true,
    val planFailed: Boolean = false,
)

@OptIn(kotlin.time.ExperimentalTime::class)
fun HomeData.toUiState(now: Instant, zone: TimeZone): HomeUiState {
    val local = now.toLocalDateTime(zone)
    val home = moments
    val status = when {
        home != null -> HomeStatus.READY
        momentsFailed -> HomeStatus.FAILED
        else -> HomeStatus.LOADING
    }
    return HomeUiState(status = status, daypart = daypart(local.hour), name = home?.user?.name ?: name,
        couple = home?.let { coupleUi(it, local.date, zone) }, refreshing = momentsLoading && home != null,
        stale = momentsFailed && home != null)
}

internal fun daypart(hour: Int) = when (hour) {
    in 5..11 -> HomeDaypart.MORNING
    in 12..16 -> HomeDaypart.AFTERNOON
    else -> HomeDaypart.EVENING
}

private fun HomeData.coupleUi(home: MomentHome, today: LocalDate, zone: TimeZone): HomeCoupleUi? {
    val couple = home.couple ?: return null
    val me = home.user.id
    val people = couple.members.associate { it.id to HomePersonUi(it.id, it.name, it.photoUrl) }
    val partner = couple.members.firstOrNull { it.id != me }?.let { people.getValue(it.id) } ?: HomePersonUi(0, "", null)
    val days = couple.anniversary?.let { runCatching { LocalDate.parse(it).daysUntil(today).coerceAtLeast(0) + 1 }.getOrNull() }
    return HomeCoupleUi(
        id = couple.id,
        partner = partner,
        daysTogether = days,
        moments = couple.members.sortedBy { if (it.id == me) 0 else 1 }
            .map { HomeMomentUi(people.getValue(it.id), it.id == me, it.activeCount, it.unseenCount) },
        momentsFresh = momentsFresh,
        conversation = null,
        challenge = section(challenge?.current, challengeLoading, challengeFailed) { challengeUi(it, people) },
        plan = section(plan?.nextPlan, planLoading, planFailed) { planUi(it, zone) },
        pendingInvitations = plan?.invitations ?: 0,
    )
}

private fun <S, T> section(value: S?, loading: Boolean, failed: Boolean, map: (S) -> T): HomeSection<T> = when {
    value != null -> HomeSection.Ready(map(value))
    loading -> HomeSection.Loading
    failed -> HomeSection.Failed
    else -> HomeSection.Empty
}

private fun HomeData.challengeUi(session: ChallengeSession, people: Map<Long, HomePersonUi>) = HomeChallengeUi(
    id = session.id,
    title = session.activity.title,
    status = when (session.state) {
        "invited" -> HomeChallengeStatus.INVITED
        "active" -> if (session.confirmedBy.isEmpty()) HomeChallengeStatus.ACTIVE else HomeChallengeStatus.ONE_CONFIRMED
        "paused" -> HomeChallengeStatus.PAUSED
        "completed" -> HomeChallengeStatus.COMPLETED
        else -> HomeChallengeStatus.ENDED
    },
    members = challenge?.members.orEmpty().take(2).map {
        HomeChallengeMemberUi(people[it.id] ?: HomePersonUi(it.id, it.name, it.photo), it.id in session.confirmedBy)
    },
)

@OptIn(kotlin.time.ExperimentalTime::class)
private fun planUi(plan: Plan, zone: TimeZone) = HomePlanUi(
    id = plan.id,
    title = plan.title,
    startsAt = runCatching { Instant.parse(plan.startsAt).toLocalDateTime(zone) }.getOrNull(),
    status = when (plan.state) {
        "agreed" -> HomePlanStatus.BOTH_GOING
        "invited" -> HomePlanStatus.AWAITING_REPLY
        "change_proposed" -> HomePlanStatus.CHANGE_SUGGESTED
        else -> HomePlanStatus.UPCOMING
    },
)
