package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.Lifecycle
import com.srisu.srisu.navigation.graph.Route

/** The library owns the stack. Entry-scoped callbacks cannot navigate after disposal. */
class AppNavigator(private val controller: NavController, private val origin: NavBackStackEntry? = null) {
    fun isCurrent() = permitted()
    private fun permitted() = origin == null ||
        (controller.currentBackStackEntry == origin && origin.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    fun open(route: Route) {
        if (!permitted()) return
        val identity = route.toString()
        if (controller.currentBackStackEntry?.savedStateHandle?.get<String>("navigation.intent.v2") == identity) return
        controller.navigate(route)
        controller.currentBackStackEntry?.savedStateHandle?.set("navigation.intent.v2", identity)
    }
    fun back() { if (permitted() && controller.previousBackStackEntry != null) controller.popBackStack() }
    fun up() = back()
    fun replace(route: Route) { if (permitted()) controller.navigate(route) {
        controller.currentBackStackEntry?.destination?.id?.let { popUpTo(it) { inclusive = true } }
        launchSingleTop = true
    } }
    fun root(route: Route) { if (permitted()) controller.navigate(route) {
        popUpTo(controller.graph.id) { inclusive = false }
        launchSingleTop = true
    } }
    fun finishFlow(route: Route, flow: Route) { if (permitted()) controller.navigate(route) {
        popUpTo(flow) { inclusive = false }
        launchSingleTop = true
    } }
    fun switchTab(route: Route) { if (permitted()) controller.navigate(route) {
        popUpTo(controller.graph.startDestinationId) { saveState = true }
        restoreState = true
        launchSingleTop = true
    } }
}
