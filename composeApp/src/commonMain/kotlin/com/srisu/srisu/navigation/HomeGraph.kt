package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.HomeScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import com.srisu.srisu.features.suggestions.screens.SuggestionScreen
import kotlinx.serialization.Serializable

sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

    @Serializable
    data object Suggestions : HomeNavigation()

    @Serializable
    data class Profile(val userProfileData: String?) : HomeNavigation()
}

fun NavGraphBuilder.homeGraph(navController: NavController) {
    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

    composable<HomeNavigation.Suggestions> {
        SuggestionScreen { userProfileData ->
            navController.navigate(HomeNavigation.Profile(userProfileData = userProfileData))
        }
    }

    composable<HomeNavigation.Profile> { backStackEntry ->
        val userProfileData = backStackEntry.arguments?.getString("userProfileData")
        ProfileScreen(userProfileData = userProfileData)
    }

}