package com.srisu.srisu.navigation

import SuggestionProfileScreen
import androidx.compose.runtime.Composable
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

@Composable
fun koinSuggestionViewModelScoped(navController: NavController): SuggestionViewModel {
    val parentEntry = remember { navController.getBackStackEntry("home") }
    return koinViewModel(viewModelStoreOwner = parentEntry)
}


fun NavGraphBuilder.homeGraph(navController: NavController) {
    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

    composable<HomeNavigation.Suggestions> {
        val viewModel = koinSuggestionViewModelScoped(navController)
        SuggestionScreen(
            suggestionViewModel = viewModel,
            navigateFilterScreen = { navController.navigate(HomeNavigation.Filter) },
            navigateProfileScreen = { suggestionProfileData ->
                navController.navigate(HomeNavigation.SuggestionProfile(suggestionProfileData = suggestionProfileData))
            }
        )
    }

    composable<HomeNavigation.SuggestionProfile> { backStackEntry ->
        val viewModel = koinSuggestionViewModelScoped(navController)
        val userProfileData = backStackEntry.arguments?.getString("userProfileData")
        SuggestionProfileScreen(
            userProfileData = userProfileData,
            suggestionViewModel = viewModel
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