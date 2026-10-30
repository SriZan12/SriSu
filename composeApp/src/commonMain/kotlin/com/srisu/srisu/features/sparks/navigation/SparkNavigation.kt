package com.srisu.srisu.features.sparks.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.sparks.presentation.SparkScreen
import kotlinx.serialization.Serializable

@Serializable data class SparksDestination(val id: String? = null, val fromChat: Boolean = false) : Route
fun NavGraphBuilder.sparkGraph(controller: NavController) {
    composable<SparksDestination> { entry ->
        val route = entry.toRoute<SparksDestination>()
        val nav = AppNavigator(controller, entry)
        if (route.id != null && !route.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else SparkScreen(route.id, nav::back, { if (route.fromChat) nav.back() else nav.replace(ChatNav.CoupleConversation(origin = "spark")) })
    }
}
