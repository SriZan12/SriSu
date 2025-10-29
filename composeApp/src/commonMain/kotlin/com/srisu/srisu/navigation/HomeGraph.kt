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
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.home.connection.screen.ConnectionScreen
import com.srisu.srisu.features.home.home.screen.HomeScreen
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

@Serializable
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



    composable<HomeNavigation.Profile> { backStackEntry ->
        val userProfileData =
            backStackEntry.toRoute<HomeNavigation.Profile>().userProfileData
        ProfileScreen(userProfileData = userProfileData)
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




object ScreenData {
    @Serializable
    data class InterestScreenData(
        val list: List<InterestResponse.Interest?>?,
        val currentInterests: List<User.UserInterest?>?,
    )
}

