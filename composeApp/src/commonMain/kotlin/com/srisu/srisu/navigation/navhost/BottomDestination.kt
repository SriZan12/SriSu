package com.srisu.srisu.navigation.navhost

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.srisu.srisu.navigation.graph.ConnectionNav
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.navigation.graph.ProfileNav
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.SuggestionsNav

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
    EXPLORE(
        icon = Icons.Filled.Search,
        label = "Explore",
        route = SuggestionsNav.Suggestions
    ),
    CONNECTIONS(
        icon = Icons.Filled.Favorite,
        label = "Crushes",
        route = ConnectionNav.Connection
    ),
    MATCHES(
        icon = Icons.Default.FavoriteBorder,
        label = "Matches",
        route = ConnectionNav.LoveRequestScreen
    ),
    PROFILE(
        icon = Icons.Filled.Person,
        label = "Profile",
        route = ProfileNav.EditProfile
    );

    companion object {
        fun fromDestination(destination: NavDestination?): BottomDestination? {
            return when {
                destination?.hasRoute<HomeNavigation.Home>() == true -> HOME
                destination?.hasRoute<SuggestionsNav.Suggestions>() == true -> EXPLORE
                destination?.hasRoute<ConnectionNav.Connection>() == true -> CONNECTIONS
                destination?.hasRoute<ConnectionNav.LoveRequestScreen>() == true -> MATCHES
                destination?.hasRoute<ProfileNav.EditProfile>() == true -> PROFILE
                else -> null
            }
        }
    }
}