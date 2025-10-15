package com.srisu.srisu.navigation

import SuggestionProfileScreen
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.core.data.response.auth.InterestResponse
import com.srisu.srisu.core.data.response.auth.User
import com.srisu.srisu.core.data.response.suggestion.UserSuggestionResponse
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.connection.screen.ConnectionScreen
import com.srisu.srisu.features.home.home.HomeScreen
import com.srisu.srisu.features.profile.screen.EditProfileScreen
import com.srisu.srisu.features.profile.screen.InterestScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import com.srisu.srisu.features.suggestions.screens.FilterSuggestionScreen
import com.srisu.srisu.features.suggestions.screens.SuggestionScreen
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.utils.Constants.HomeGraph.EDITED_INTERESTS
import com.srisu.srisu.utils.Constants.HomeGraph.FILTER_APPLIED
import com.srisu.srisu.utils.Constants.HomeGraph.FILTER_CLEARED
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    @Serializable
    data object EditProfile : HomeNavigation()

    @Serializable
    data class InterestScreen(
        val data: String
    ) : HomeNavigation()

    @Serializable
    data object Connection : HomeNavigation()
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    suggestionViewModel: SuggestionViewModel,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

    composable<HomeNavigation.Connection> {
        ConnectionScreen { userProfileData ->
            AppLogger.log("WHILE NAVIGATING TO PROFILE SCREEN = $userProfileData")
            navController.navigate(HomeNavigation.Profile(userProfileData = userProfileData))
        }
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
                val json = Json.encodeToString(suggestionProfileData)
                navController.navigate(HomeNavigation.SuggestionProfile(json))
            },
        )
    }

    composable<HomeNavigation.SuggestionProfile> { backStackEntry ->
        val userProfileData =
            backStackEntry.toRoute<HomeNavigation.SuggestionProfile>().suggestionProfileData
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
        val userProfileData =
            backStackEntry.toRoute<HomeNavigation.Profile>().userProfileData
        ProfileScreen(userProfileData =userProfileData)
    }

    composable<HomeNavigation.EditProfile> { navBackStackEntry ->
        val savedStateHandle = navBackStackEntry.savedStateHandle
        val editedInterest = savedStateHandle.getStateFlow(EDITED_INTERESTS, "").value
        val editedInterestList =
            if (editedInterest.isNotEmpty()) Json.decodeFromString<List<User.UserInterest?>?>(
                editedInterest
            ) else null

        EditProfileScreen(
            onNavigateInterestScreen = { interests, currentInterestStrings ->

                val data = ScreenData.InterestScreenData(
                    list = interests,
                    currentInterests = currentInterestStrings,
                )
                navController.navigate(
                    HomeNavigation.InterestScreen(
                        data = Json.encodeToString(data)
                    )
                )

            },
            editedInterest = editedInterestList,
        )
    }


    composable<HomeNavigation.InterestScreen> { backStackEntry ->
        val data = backStackEntry.toRoute<HomeNavigation.InterestScreen>().data
        val interestScreenData = Json.decodeFromString<ScreenData.InterestScreenData>(data)

        InterestScreen(
            interests = interestScreenData.list,
            currentInterests = interestScreenData.currentInterests,
            onInterestSelected = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        key = EDITED_INTERESTS,
                        value = Json.encodeToString(it)
                    )

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

object ScreenData {
    @Serializable
    data class InterestScreenData(
        val list: List<InterestResponse.Interest?>?,
        val currentInterests: List<User.UserInterest?>?,
    )
}

