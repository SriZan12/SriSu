package com.srisu.srisu.features.home

import com.srisu.srisu.features.challenges.data.*
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.plans.data.*
import kotlinx.datetime.TimeZone
import kotlin.test.*
import kotlin.time.Instant

@OptIn(kotlin.time.ExperimentalTime::class)
class HomeUiStateTest {
    private val now = Instant.parse("2026-10-27T18:30:00Z")
    private val zone = TimeZone.UTC
    private val me = MomentPerson(1, "Salil", activeCount = 1)
    private val partner = MomentPerson(2, "Aayush", "https://example.test/a.jpg", activeCount = 2, unseenCount = 2)
    private val moments = MomentHome(me, MomentCouple(10, "2026-02-22", listOf(partner, me)), "2026-10-27T18:30:00Z")
    private val activity = ChallengeActivity("a", "c", "Cook something new together", "", 45, "home", "low", emptyList(), "")

    @Test fun initialStateIsLoading() {
        val state = HomeData().toUiState(now, zone)
        assertEquals(HomeStatus.LOADING, state.status)
        assertNull(state.couple)
    }

    @Test fun failureWithoutDataIsFailed() {
        assertEquals(HomeStatus.FAILED, HomeData(momentsLoading = false, momentsFailed = true).toUiState(now, zone).status)
    }

    @Test fun failureWithDataKeepsContentAndMarksStale() {
        val state = HomeData(moments = moments, momentsLoading = false, momentsFailed = true).toUiState(now, zone)
        assertEquals(HomeStatus.READY, state.status)
        assertTrue(state.stale)
    }

    @Test fun unlinkedAccountHasNoCouple() {
        val state = HomeData(moments = moments.copy(couple = null), momentsLoading = false).toUiState(now, zone)
        assertEquals(HomeStatus.READY, state.status)
        assertNull(state.couple)
    }

    @Test fun mapsCoupleSections() {
        val session = ChallengeSession("c1", 10, 1, activity, "active", 1, confirmedBy = listOf(1), createdAt = "2026-10-20T00:00:00Z")
        val plan = Plan(7, 10, 1, "Sunrise hike", "2026-09-20T05:30:00Z", response = "yes", state = "agreed", revision = 1)
        val state = HomeData(moments = moments, momentsLoading = false, momentsFresh = true,
            challenge = ChallengeHome(members = listOf(ChallengeMember(1, "Salil"), ChallengeMember(2, "Aayush")), current = session),
            challengeLoading = false, plan = PlanHome(nextPlan = plan, invitations = 2), planLoading = false).toUiState(now, zone)
        val couple = assertNotNull(state.couple)
        assertEquals(HomeDaypart.EVENING, state.daypart)
        assertEquals("Aayush", couple.partner.name)
        assertEquals(248, couple.daysTogether)
        assertEquals(listOf(true, false), couple.moments.map { it.own })
        assertNull(couple.conversation)
        val challenge = assertIs<HomeSection.Ready<HomeChallengeUi>>(couple.challenge).value
        assertEquals(HomeChallengeStatus.ONE_CONFIRMED, challenge.status)
        assertEquals(listOf(true, false), challenge.members.map { it.confirmed })
        assertEquals("https://example.test/a.jpg", challenge.members[1].person.photoUrl)
        val upcoming = assertIs<HomeSection.Ready<HomePlanUi>>(couple.plan).value
        assertEquals(HomePlanStatus.BOTH_GOING, upcoming.status)
        assertEquals(5, upcoming.startsAt?.hour)
        assertEquals(2, couple.pendingInvitations)
    }

    @Test fun sectionsReportLoadingFailedAndEmpty() {
        val loading = HomeData(moments = moments, momentsLoading = false).toUiState(now, zone).couple!!
        assertEquals(HomeSection.Loading, loading.challenge)
        val settled = HomeData(moments = moments, momentsLoading = false, challengeLoading = false, challengeFailed = true,
            planLoading = false, plan = PlanHome()).toUiState(now, zone).couple!!
        assertEquals(HomeSection.Failed, settled.challenge)
        assertEquals(HomeSection.Empty, settled.plan)
    }

    @Test fun daypartBoundaries() {
        assertEquals(HomeDaypart.EVENING, daypart(4))
        assertEquals(HomeDaypart.MORNING, daypart(5))
        assertEquals(HomeDaypart.AFTERNOON, daypart(12))
        assertEquals(HomeDaypart.EVENING, daypart(17))
    }
}
