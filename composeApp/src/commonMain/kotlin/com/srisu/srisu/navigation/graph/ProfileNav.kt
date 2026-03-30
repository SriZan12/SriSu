package com.srisu.srisu.navigation.graph

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.screen.EditProfileScreen
import com.srisu.srisu.features.home.profile.presentation.screen.InterestScreen
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.utils.Constants.HomeGraph.EDITED_INTERESTS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class ProfileNav : Route {

    @Serializable
    data object EditProfile : ProfileNav()

    @Serializable
    data class InterestScreen(
        val data: String
    ) : ConnectionNav()

}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.profileGraph(
    navController: NavController,
) {

    composable<ProfileNav.EditProfile> { navBackStackEntry ->
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
                    ProfileNav.InterestScreen(
                        data = Json.encodeToString(data)
                    )
                )

            },
            editedInterest = editedInterestList,
        )
    }


    composable<ProfileNav.InterestScreen> { backStackEntry ->
        val data = backStackEntry.toRoute<ProfileNav.InterestScreen>().data
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