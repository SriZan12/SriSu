package com.srisu.srisu.navigation

import SuggestionProfileScreen
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.HomeScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import com.srisu.srisu.features.suggestions.screens.FilterSuggestionScreen
import com.srisu.srisu.features.suggestions.screens.SuggestionScreen
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import kotlinx.serialization.Serializable

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

private const val FILTER_APPLIED = "filter_applied"
private const val FILTER_CLEARED = "filter_cleared"

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    suggestionViewModel: SuggestionViewModel,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

    composable<HomeNavigation.Suggestions> {

        val navBackStackEntry = remember { navController.currentBackStackEntry }
        val savedStateHandle = navBackStackEntry?.savedStateHandle

        val filterApplied = savedStateHandle?.getStateFlow(FILTER_APPLIED, false)?.value ?: false
        val filterCleared = savedStateHandle?.getStateFlow(FILTER_CLEARED, false)?.value ?: false


        SuggestionScreen(
            suggestionViewModel = suggestionViewModel,
            filterApplied = filterApplied,
            filterCleared = filterCleared,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this@composable,
            navigateFilterScreen = { navController.navigate(HomeNavigation.Filter) },
            navigateProfileScreen = { suggestionProfileData ->
                navController.navigate(HomeNavigation.SuggestionProfile(suggestionProfileData = suggestionProfileData))
            },
        )
    }

    composable<HomeNavigation.SuggestionProfile> { backStackEntry ->
        val userProfileData = backStackEntry.arguments?.getString("suggestionProfileData")
        clearFilterFlags(navController = navController)

        SuggestionProfileScreen(
            suggestionViewModel = suggestionViewModel,
            userProfileData = userProfileData,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this@composable,
        )

    }


    composable<HomeNavigation.Filter> { backStackEntry ->

        FilterSuggestionScreen(
            suggestionViewModel = suggestionViewModel,
            onNavigateBack = {
                clearFilterFlags(navController = navController)
                navController.popBackStack()
            },
            onClearFilter = {
                clearFilter(navController = navController)
                navController.popBackStack()
            },
            onFilterApplied = {
                applyFilter(navController = navController)
                navController.popBackStack()
            }
        )
    }



    composable<HomeNavigation.Profile> { backStackEntry ->
        val userProfileData = backStackEntry.arguments?.getString("userProfileData")
        ProfileScreen(userProfileData = userProfileData)
    }


}


fun clearFilterFlags(navController: NavController) {
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_CLEARED, false)

    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_APPLIED, false)
}

fun clearFilter(navController: NavController) {
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_CLEARED, true)
}

fun applyFilter(navController: NavController) {
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_APPLIED, true)
}
