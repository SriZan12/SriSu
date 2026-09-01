package com.srisu.srisu.navigation.graph

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.home.couple.presentation.screen.CoupleProfileScreen
import com.srisu.srisu.features.home.couple.presentation.state.CoupleProfileUiState
import com.srisu.srisu.features.home.couple.presentation.vm.CoupleProfileViewModel
import com.srisu.srisu.features.home.profile.presentation.screen.InterestScreen
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
    coupleProfileViewModel: CoupleProfileViewModel,
    chatViewModel: ChatViewModel,
) {

    composable<ProfileNav.EditProfile> { navBackStackEntry ->
        val savedStateHandle = navBackStackEntry.savedStateHandle
        val editedInterest = savedStateHandle.getStateFlow(EDITED_INTERESTS, "").value
        val editedInterestList =
            if (editedInterest.isNotEmpty()) Json.decodeFromString<List<User.UserInterest?>?>(
                editedInterest
            ) else null

//        EditProfileScreen(
//            onNavigateInterestScreen = { interests, currentInterestStrings ->
//
//                val data = ScreenData.InterestScreenData(
//                    list = interests,
//                    currentInterests = currentInterestStrings,
//                )
//                navController.navigate(
//                    ProfileNav.InterestScreen(
//                        data = Json.encodeToString(data)
//                    )
//                )
//
//            },
//            editedInterest = editedInterestList,
//        )

        val screenState by coupleProfileViewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(coupleProfileViewModel) {
            coupleProfileViewModel.loadProfile()
        }

        CoupleProfileScreen(
            uiState = screenState.profile ?: CoupleProfileUiState(),
            isLoading = screenState.isLoading,
            isMissing = screenState.isMissing ||
                (screenState.profile == null && !screenState.isLoading),
            errorTitle = screenState.errorTitle,
            errorMessage = screenState.errorMessage,
            onRetry = { coupleProfileViewModel.loadProfile(forceRefresh = true) },
            onDismissError = coupleProfileViewModel::clearError,
            onNavigateBack = navController::popBackStack,
            onSendMessage = {
                screenState.profile?.partnerId?.let(chatViewModel::openPartnerChat)
                navController.navigate(ChatNav.ChatScreen) {
                    launchSingleTop = true
                }
            },
            onPlanDate = {
                // UI-only extension point for the future date-planning flow.
            },
            onOpenSettings = {
                if (screenState.profile != null) {
                    coupleProfileViewModel.clearError()
                    navController.navigate(HomeNavigation.EditCoupleProfile)
                }
            },
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
