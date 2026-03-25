package com.srisu.srisu.navigation.graph

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.suggestions.presentation.screen.FilterSuggestionScreen
import com.srisu.srisu.features.home.suggestions.presentation.screen.SuggestionProfileScreen
import com.srisu.srisu.features.home.suggestions.presentation.screen.SuggestionScreen
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel
import com.srisu.srisu.utils.Constants.HomeGraph.FILTER_APPLIED
import com.srisu.srisu.utils.Constants.HomeGraph.FILTER_CLEARED
import kotlinx.serialization.Serializable

@Serializable
sealed class SuggestionsNav : Route {

    @Serializable
    data object Suggestions : SuggestionsNav()

    @Serializable
    data object SuggestionProfile : SuggestionsNav()

    @Serializable
    data object Filter : SuggestionsNav()
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.suggestionsGraph(
    navController: NavController,
    suggestionViewModel: SuggestionViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    composable<SuggestionsNav.Suggestions> {

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
            navigateFilterScreen = { navController.navigate(SuggestionsNav.Filter) },
            navigateProfileScreen = { suggestionProfileData ->
                suggestionViewModel.setSuggestionProfileData(suggestionProfileData)
                navController.navigate(SuggestionsNav.SuggestionProfile)
            },
        )
    }

    composable<SuggestionsNav.SuggestionProfile> {
        clearFilterFlags(navController = navController)

        SuggestionProfileScreen(
            suggestionViewModel = suggestionViewModel,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this@composable,
        )

    }


    composable<SuggestionsNav.Filter> {

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
