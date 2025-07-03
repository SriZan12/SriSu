package com.srisu.srisu.navigation

import SuggestionProfileScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.srisu.srisu.core.logger.AppLogger
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

private const val FILTER_APPLIED = "filter_applied"
private const val FILTER_CLEARED = "filter_cleared"

fun NavGraphBuilder.homeGraph(
    navController: NavController,
    suggestionViewModel: SuggestionViewModel
) {
    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

    composable<HomeNavigation.Suggestions> {

        val navBackStackEntry = remember { navController.currentBackStackEntry }
        val savedStateHandle = navBackStackEntry?.savedStateHandle

        val filterApplied = savedStateHandle?.getStateFlow(FILTER_APPLIED, false)?.value ?: false
        val filterCleared = savedStateHandle?.getStateFlow(FILTER_CLEARED, false)?.value ?: false

        AppLogger.log("FILTER CLEARED = $filterCleared")

        SuggestionScreen(
            suggestionViewModel = suggestionViewModel,
            filterApplied = filterApplied,
            filterCleared = filterCleared,
            navigateFilterScreen = { navController.navigate(HomeNavigation.Filter) },
            navigateProfileScreen = { suggestionProfileData ->
                navController.navigate(HomeNavigation.SuggestionProfile(suggestionProfileData = suggestionProfileData))
            },
        )
    }

    composable<HomeNavigation.SuggestionProfile> { backStackEntry ->
        val userProfileData = backStackEntry.arguments?.getString("suggestionProfileData")
        clearFilterFlags(navController = navController)

        AnimatedContent(
            targetState = true
        ){
            SuggestionProfileScreen(
                suggestionViewModel = suggestionViewModel,
                userProfileData = userProfileData,
            )
        }

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
