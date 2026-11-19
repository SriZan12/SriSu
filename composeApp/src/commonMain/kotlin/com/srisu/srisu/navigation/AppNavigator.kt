package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.lifecycle.Lifecycle
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ChatNav

/** The library owns the stack. Entry-scoped callbacks cannot navigate after disposal. */
class AppNavigator(private val controller: NavController, private val origin: NavBackStackEntry? = null) {
    fun isCurrent() = permitted()
    private fun permitted() = origin == null ||
        (controller.currentBackStackEntry == origin && origin.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    fun open(route: Route) {
        if (!permitted()) return
        val identity = route.toString()
        if (controller.currentBackStackEntry?.savedStateHandle?.get<String>("navigation.intent.v2") == identity) return
        val currentChat = controller.currentBackStackEntry?.takeIf {
            it.destination.hasRoute<ChatNav.CoupleConversation>()
        }?.toRoute<ChatNav.CoupleConversation>()
        controller.navigate(route) {
            // A notification for this conversation updates its target message instead
            // of stacking another copy. Different conversations retain normal Back.
            launchSingleTop = route is ChatNav.CoupleConversation && currentChat != null &&
                route.roomId == currentChat.roomId
        }
        controller.currentBackStackEntry?.savedStateHandle?.set("navigation.intent.v2", identity)
    }
    fun back() { if (permitted() && controller.previousBackStackEntry != null) controller.popBackStack() }
    fun up() = back()
    /** A feature opened from chat can have child routes. Return to the conversation,
     * rather than popping just one child or creating a second chat instance. */
    fun conversation(fromChat: Boolean, route: ChatNav.CoupleConversation) {
        if (!permitted()) return
        if (fromChat && controller.popBackStack<ChatNav.CoupleConversation>(inclusive = false)) return
        open(route)
    }
    fun replace(route: Route) { if (permitted()) controller.navigate(route) {
        controller.currentBackStackEntry?.destination?.id?.let { popUpTo(it) { inclusive = true } }
        launchSingleTop = true
    } }
    fun root(route: Route) { if (permitted()) controller.navigate(route) {
        popUpTo(controller.graph.id) { inclusive = false }
        launchSingleTop = true
    } }
    fun finishFlow(route: Route, flow: Route): Boolean {
        if (!permitted()) return false
        controller.navigate(route) {
            popUpTo(flow) { inclusive = false }
            launchSingleTop = true
        }
        return true
    }
    fun leaveFlow(route: Route, flow: Route) { if (permitted()) controller.navigate(route) {
        popUpTo(flow) { inclusive = true }
        launchSingleTop = true
    } }
    fun switchTab(route: Route) { if (permitted()) controller.navigate(route) {
        popUpTo(controller.graph.startDestinationId) { saveState = true }
        restoreState = true
        launchSingleTop = true
    } }
}
