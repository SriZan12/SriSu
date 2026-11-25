package com.srisu.srisu.features.sparks.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.sparks.presentation.SparkScreen
import com.srisu.srisu.features.sparks.presentation.QuestionPage
import kotlinx.serialization.Serializable

@Serializable data class SparksDestination(val id: String? = null, val fromChat: Boolean = false,
    val page: String = QuestionPage.HOME.name, val selection: String? = null) : Route
fun NavGraphBuilder.sparkGraph(controller: NavController) {
    composable<SparksDestination> { entry ->
        val route = entry.toRoute<SparksDestination>()
        val page = QuestionPage.entries.firstOrNull { it.name == route.page }
        val nav = AppNavigator(controller, entry)
        if (page == null || (route.id != null && !route.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))))
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else SparkScreen(route.id, nav::back, { nav.conversation(route.fromChat, ChatNav.CoupleConversation(origin = "spark")) },
            page = page, selection = route.selection,
            onNavigate = { next, selection -> nav.open(SparksDestination(fromChat = route.fromChat, page = next.name, selection = selection)) },
            onSession = { nav.open(SparksDestination(it, fromChat = route.fromChat)) },
            onConnect = { nav.open(ChatNav.PartnerFlow) }, onInvitations = { nav.open(ChatNav.PartnerRequests) },
            onChatSession = { session -> nav.conversation(route.fromChat, ChatNav.CoupleConversation(origin = "spark", messageId = session?.cardEventId?.takeIf(String::isNotBlank))) })
    }
}
