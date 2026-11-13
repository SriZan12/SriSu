package com.srisu.srisu.features.challenges.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.challenges.presentation.ChallengeScreen
import com.srisu.srisu.features.moments.navigation.MomentComposerDestination
import kotlinx.serialization.Serializable

@Serializable data class ChallengesDestination(val id: String? = null, val fromChat: Boolean = false) : Route
fun NavGraphBuilder.challengeGraph(controller: NavController) {
    composable<ChallengesDestination> { entry ->
        val route = entry.toRoute<ChallengesDestination>()
        val nav = AppNavigator(controller, entry)
        if (route.id != null && !route.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else ChallengeScreen(route.id, nav::back,
            { if (route.fromChat) nav.back() else nav.replace(ChatNav.CoupleConversation(origin = "challenge")) },
            { nav.open(MomentComposerDestination(it)) })
    }
}
