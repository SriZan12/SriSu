package com.srisu.srisu.navigation

import SuggestionProfileScreen
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.suggestions.screens.FilterSuggestionScreen
import com.srisu.srisu.features.suggestions.screens.SuggestionScreen
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.utils.Constants.HomeGraph.FILTER_APPLIED
import com.srisu.srisu.utils.Constants.HomeGraph.FILTER_CLEARED
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class SuggestionsNav : Route {

    @Serializable
    data object Suggestions : SuggestionsNav()

    @Serializable
    data class SuggestionProfile(val suggestionProfileData: String?) : SuggestionsNav()

    @Serializable
    data object Filter : SuggestionsNav()
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.suggestionsGraph(
    navController: NavController,
    viewModel: SuggestionViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    composable<SuggestionsNav.Suggestions> {

        val navBackStackEntry = remember { navController.currentBackStackEntry }
        val savedStateHandle = navBackStackEntry?.savedStateHandle

        val filterApplied = savedStateHandle?.getStateFlow(FILTER_APPLIED, false)?.value ?: false
        val filterCleared = savedStateHandle?.getStateFlow(FILTER_CLEARED, false)?.value ?: false

        SuggestionScreen(
            suggestionViewModel = viewModel,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this@composable,
            navigateFilterScreen = { navController.navigate(SuggestionsNav.Filter) },
            navigateProfileScreen = { suggestionProfileData ->
                val json = Json.encodeToString(suggestionProfileData)
                navController.navigate(SuggestionsNav.SuggestionProfile(json))
            },
            filterApplied = filterApplied,
            filterCleared = filterCleared
        )
    }

    composable<SuggestionsNav.SuggestionProfile> { backStackEntry ->
        val data = backStackEntry.toRoute<SuggestionsNav.SuggestionProfile>().suggestionProfileData
        SuggestionProfileScreen(
            suggestionViewModel = viewModel,
            userProfileData = data,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this@composable,
        )
    }

    composable<SuggestionsNav.Filter> {
        FilterSuggestionScreen(
            suggestionViewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onClearFilter = { navController.popBackStack() },
            onFilterApplied = { navController.popBackStack() }
        )
    }
}
