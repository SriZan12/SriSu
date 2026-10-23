package com.srisu.srisu.navigation.navhost

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Forum
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.navigation.graph.ProfileNav
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.AppNavigator

/** Chat is a full-screen destination; Back returns to the originating root. */
fun AppNavigator.selectBottomDestination(destination: BottomDestination) {
    if(destination==BottomDestination.CHAT) open(destination.route) else switchTab(destination.route)
}

enum class BottomDestination(
    val icon: ImageVector,
    val label: String,
    val route: Route
) {
    HOME(
        icon = Icons.Filled.Home,
        label = "Home",
        route = HomeNavigation.Home
    ),
    CHAT(
        icon = Icons.Filled.Forum,
        label = "Chat",
        route = ChatNav.CoupleConversation(origin = "bottom_bar")
    ),
    PROFILE(
        icon = Icons.Filled.PersonAdd,
        label = "Profile",
        route = ProfileNav.EditProfile
    );

    companion object {
        fun fromDestination(destination: NavDestination?): BottomDestination? {
            return when {
                destination?.hasRoute<HomeNavigation.Home>() == true -> HOME
                destination?.hasRoute<ProfileNav.EditProfile>() == true -> PROFILE
                else -> null
            }
        }
    }
}
