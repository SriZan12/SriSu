package com.srisu.srisu.features.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.srisu.srisu.features.home.dashboard.screen.*
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.navigation.navhost.*
import kotlinx.datetime.LocalDateTime

/** Synthetic reference states only; no account or production service is contacted. */
object HomeRenderFixtures {
    val me = HomePersonUi(1, "Srijan", null)
    val partner = HomePersonUi(2, "Aatithi", null)
    val spark = SparkSession("11111111-1111-4111-8111-111111111111", 10, 2,
        "22222222-2222-4222-8222-222222222222", "!synthetic:example.test", 1, "deep-7-v1",
        "What made you feel close to me this week?", "deep", "open", 1, submittedBy = listOf(2), createdAt = "2026-11-09T06:00:00Z")
    val daily = SparkDaily(spark, "2026-11-09", "Asia/Kathmandu", revealedCount = 59, partnerActivityAt = "2026-11-09T07:00:00Z", partnerActivityKind = "answered")
    val plan = Plan(7, 10, 1, "Sunrise hike to Nagarkot", "2099-11-20T00:00:00Z", "Asia/Kathmandu", response = "yes", state = "agreed", revision = 1)
    val couple = HomeCoupleUi(10, partner, 248,
        listOf(HomeMomentUi(me, true, 1, 0), HomeMomentUi(partner, false, 2, 2)), true, null,
        HomeSection.Ready(HomeChallengeUi("c1", "Cook something new together", HomeChallengeStatus.ONE_CONFIRMED,
            listOf(HomeChallengeMemberUi(me, false), HomeChallengeMemberUi(partner, true)))),
        HomeSection.Ready(HomePlanUi(7, plan.title, LocalDateTime(2099, 11, 20, 5, 45), HomePlanStatus.BOTH_GOING)), 0,
        title = "Lovebirds", account = 1, question = HomeSection.Ready(daily), questionFresh = true,
        statuses = CoupleStatuses(10, listOf(CoupleStatus(1, "Feeling cosy", revision = 1), CoupleStatus(2, "Thinking of you", revision = 1)), "2026-11-09T07:00:00Z"),
        statusesFresh = true, ideas = HomeSection.Ready(DateIdeas(10)), ideasFresh = true,
        featuredPlan = plan, planFresh = true, questionsDone = 59, plansDone = 27, chatAvailability = MatrixChatAvailability.Ready(), unread = false, statusState = HomeStatus.READY, sharingRequest = HomeSection.Ready(false))
    val sparkHome = HomeUiState(HomeStatus.READY, name = "Srijan", couple = couple)
    val message = sparkHome.copy(couple = couple.copy(conversation = HomeConversationUi("found the trail we talked about…", "12:40", 0, false, emptyList(), "message", Long.MAX_VALUE), unread = true))
    val quiet = sparkHome.copy(couple = couple.copy(question = HomeSection.Ready(daily.copy(session = spark.copy(submittedBy = emptyList()), partnerActivityAt = null, partnerActivityKind = null)),
        moments = couple.moments.map { it.copy(unseenCount = 0) },
        conversation = HomeConversationUi("night night 🌙", "22:20", 0, false, emptyList()),
        plan = HomeSection.Empty, featuredPlan = null,
        challenge = HomeSection.Ready((couple.challenge as HomeSection.Ready).value.copy(status = HomeChallengeStatus.COMPLETED))))
    val unpaired = HomeUiState(HomeStatus.READY, name = "Srijan")
    fun state(name: String): HomeUiState = when (name) {
        "message" -> message
        "quiet" -> quiet
        "unpaired" -> unpaired
        "sent" -> unpaired.copy(invitation = HomeInvitationUi("Aatithi", false))
        "received" -> unpaired.copy(invitation = HomeInvitationUi("Aatithi", true))
        "new" -> quiet.copy(couple = quiet.couple!!.copy(daysTogether = null, questionsDone = 0, plansDone = 0, statuses = CoupleStatuses(10, serverTime = "2026-11-09T00:00:00Z"), challenge = HomeSection.Empty, conversation = null))
        "failed" -> HomeUiState(HomeStatus.FAILED)
        "loading" -> HomeUiState()
        "sections-failed" -> sparkHome.copy(couple = couple.copy(question = HomeSection.Failed, plan = HomeSection.Failed, challenge = HomeSection.Failed, chatAvailability = MatrixChatAvailability.Error("Offline"), unread = null))
        else -> sparkHome
    }
}
@Composable internal fun HomeRenderFixture(name: String, actions: HomeActions = HomeActions(), interactions: HomeInteractions = HomeInteractions(), retry: () -> Unit = {}) {
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { AppBottomBar({}, BottomDestination.HOME, true) }) { padding ->
        HomeContent(HomeRenderFixtures.state(name), { null }, retry, actions, Modifier.padding(padding).consumeWindowInsets(padding), interactions)
    }
}
