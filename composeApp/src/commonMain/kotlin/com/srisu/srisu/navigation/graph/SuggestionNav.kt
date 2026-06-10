package com.srisu.srisu.navigation.graph

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    sharedTransitionScope: SharedTransitionScope,
) {

    composable<SuggestionsNav.Suggestions> { backStackEntry ->
        val filterApplied by backStackEntry.savedStateHandle
            .getStateFlow(FILTER_APPLIED, false)
            .collectAsStateWithLifecycle()

        val filterCleared by backStackEntry.savedStateHandle
            .getStateFlow(FILTER_CLEARED, false)
            .collectAsStateWithLifecycle()

        SuggestionScreen(
            suggestionViewModel = suggestionViewModel,
            filterApplied = filterApplied,
            filterCleared = filterCleared,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this@composable,
            navigateFilterScreen = {
                navController.navigate(SuggestionsNav.Filter)
            },
            navigateProfileScreen = { suggestionProfileData ->
                suggestionViewModel.getSuggestionProfile(offlineProfileData = suggestionProfileData)
                navController.navigate(SuggestionsNav.SuggestionProfile)
            },
        )
    }

    composable<SuggestionsNav.SuggestionProfile> {
        resetSuggestionFilterFlags(navController)

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
                resetSuggestionFilterFlags(navController)
                navController.popBackStack()
            },
            onClearFilter = {
                setSuggestionFilterCleared(navController)
                navController.popBackStack()
            },
            onFilterApplied = {
                setSuggestionFilterApplied(navController)
                navController.popBackStack()
            },
        )
    }
}

private fun resetSuggestionFilterFlags(navController: NavController) {
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_CLEARED, false)

    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_APPLIED, false)
}

private fun setSuggestionFilterCleared(navController: NavController) {
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_CLEARED, true)

    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_APPLIED, false)
}

private fun setSuggestionFilterApplied(navController: NavController) {
    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_APPLIED, true)

    navController.previousBackStackEntry
        ?.savedStateHandle
        ?.set(FILTER_CLEARED, false)
}