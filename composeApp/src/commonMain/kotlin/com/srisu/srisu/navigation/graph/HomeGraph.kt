package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.features.home.dashboard.screen.HomeActions
import com.srisu.srisu.features.home.dashboard.screen.HomeScreen
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import kotlinx.serialization.Serializable

sealed interface HomeNavigation : Route {
    @Serializable data object Home : HomeNavigation
    @Serializable data object Explore : HomeNavigation
}
fun NavGraphBuilder.homeGraph(controller: NavHostController) {
    composable<HomeNavigation.Home> { entry ->
        val actions = remember(controller, entry) {
            val nav = AppNavigator(controller, entry)
            val chat = { nav.open(ChatNav.CoupleConversation(origin = "home")) }
            HomeActions(
                onNotifications = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(showInvitations = true)) },
                onCoupleProfile = { nav.open(CoupleProfileDestination()) },
                onViewMoments = { couple, creator -> nav.open(com.srisu.srisu.features.moments.navigation.MomentViewerDestination(coupleId = couple, creatorId = creator)) },
                onCreateMoment = { nav.open(com.srisu.srisu.features.moments.navigation.MomentComposerDestination(it)) },
                onChat = chat,
                onSpark = { nav.open(com.srisu.srisu.features.sparks.navigation.SparksDestination()) },
                // No Ask feature or endpoint exists yet; asking happens in the couple conversation.
                onAsk = chat,
                onPlanner = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination()) },
                onChallenge = { nav.open(com.srisu.srisu.features.challenges.navigation.ChallengesDestination(it)) },
                onPlan = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(it)) },
                onFindPartner = { nav.open(ChatNav.PartnerFlow) },
                onInvitations = { nav.open(ChatNav.PartnerRequests) },
            )
        }
        HomeScreen(actions)
    }
}
