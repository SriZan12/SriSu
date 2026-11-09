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
    @Serializable data object Questions : HomeNavigation
}
fun NavGraphBuilder.homeGraph(controller: NavHostController) {
    composable<HomeNavigation.Questions> { entry ->
        val nav = AppNavigator(controller, entry)
        com.srisu.srisu.features.sparks.presentation.SparkScreen(null, { nav.switchTab(HomeNavigation.Home) },
            { nav.open(ChatNav.CoupleConversation(origin = "questions")) })
    }
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
                onAsk = { nav.open(ChatNav.CoupleConversation(origin = "home", draftPreset = "ask")) },
                onPlanner = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(create = true)) },
                onChallenge = { nav.open(com.srisu.srisu.features.challenges.navigation.ChallengesDestination(it)) },
                onPlan = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(it)) },
                onFindPartner = { nav.open(ChatNav.PartnerFlow) },
                onInvitations = { nav.open(ChatNav.PartnerRequests) },
                onQuestion = { nav.open(com.srisu.srisu.features.sparks.navigation.SparksDestination(it)) },
                onChatMessage = { nav.open(ChatNav.CoupleConversation(origin = "home", messageId = it)) },
                onDraft = { nav.open(ChatNav.CoupleConversation(origin = "home", draftPreset = it)) },
                onIdeaPlan = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(ideaId = it)) },
                onEditPlan = { nav.open(com.srisu.srisu.features.plans.navigation.PlansDestination(id = it, edit = true)) },
                onExplore = { nav.open(HomeNavigation.Explore) },
                onSharing = { nav.open(CoupleProfileDestination(section = com.srisu.srisu.features.coupleprofile.navigation.CoupleSection.SHARING)) },
            )
        }
        HomeScreen(actions)
    }
}
