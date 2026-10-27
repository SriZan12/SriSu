package com.srisu.srisu.navigation.graph

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.features.home.entertainment.screen.HomeScreen
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import kotlinx.serialization.Serializable

sealed interface HomeNavigation : Route {
    @Serializable data object Home : HomeNavigation
    @Serializable data object Explore : HomeNavigation
}
fun NavGraphBuilder.homeGraph(controller: NavHostController) {
    composable<HomeNavigation.Home> { entry ->
        val nav = AppNavigator(controller, entry)
        HomeScreen(onFindPartner = { nav.open(ChatNav.PartnerFlow) },
            onExplore = { nav.switchTab(HomeNavigation.Explore) },
            onCoupleProfile = { nav.open(CoupleProfileDestination()) },
            onPlans = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(it)) },
            onPlanInvitations = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(showInvitations = true)) },
            onChallenges = { nav.open(com.srisu.srisu.features.challenges.navigation.ChallengesDestination(it)) },
            onSparks = { nav.open(com.srisu.srisu.features.sparks.navigation.SparksDestination(it)) },
            onInvitations = { nav.open(ChatNav.PartnerRequests) },
            onChat = { nav.open(ChatNav.CoupleConversation(origin = "home")) },
            onCreateMoment = { nav.open(com.srisu.srisu.features.moments.navigation.MomentComposerDestination(it)) },
            onViewMoments = { couple,creator -> nav.open(com.srisu.srisu.features.moments.navigation.MomentViewerDestination(coupleId=couple,creatorId=creator)) })
    }
}
