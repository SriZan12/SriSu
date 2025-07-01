package com.srisu.srisu.navigation

import SuggestionProfileScreen
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.srisu.srisu.features.home.HomeScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import com.srisu.srisu.features.suggestions.screens.FilterSuggestionScreen
import com.srisu.srisu.features.suggestions.screens.SuggestionScreen
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

    @Serializable
    data object Suggestions : HomeNavigation()

    @Serializable
    data class SuggestionProfile(val suggestionProfileData: String?) : HomeNavigation()

    @Serializable
    data class Profile(val userProfileData: String?) : HomeNavigation()

    @Serializable
    data object Filter : HomeNavigation()
}

fun NavGraphBuilder.homeGraph(
    navController: NavController,
    suggestionViewModel: SuggestionViewModel
) {
    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

    composable<HomeNavigation.Suggestions> {
        SuggestionScreen(
            suggestionViewModel = suggestionViewModel,
            navigateFilterScreen = { navController.navigate(HomeNavigation.Filter) },
            navigateProfileScreen = { suggestionProfileData ->
                navController.navigate(HomeNavigation.SuggestionProfile(suggestionProfileData = suggestionProfileData))
            }
        )
    }

    composable<HomeNavigation.SuggestionProfile> { backStackEntry ->
        val userProfileData = backStackEntry.arguments?.getString("suggestionProfileData")
        SuggestionProfileScreen(
            suggestionViewModel = suggestionViewModel,
            userProfileData = userProfileData,
        )
    }


    composable<HomeNavigation.Filter> { backStackEntry ->
        FilterSuggestionScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }



    composable<HomeNavigation.Profile> { backStackEntry ->
        val userProfileData = backStackEntry.arguments?.getString("userProfileData")
        ProfileScreen(userProfileData = userProfileData)
    }


}

