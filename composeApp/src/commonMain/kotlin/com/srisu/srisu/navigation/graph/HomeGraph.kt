package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.home.screen.HomeScreen
import com.srisu.srisu.features.home.couple.presentation.screen.CoupleProfileScreen
import com.srisu.srisu.features.home.couple.presentation.screen.EditCoupleProfileScreen
import com.srisu.srisu.features.home.couple.presentation.state.CoupleProfileUiState
import com.srisu.srisu.features.home.couple.presentation.vm.CoupleProfileViewModel
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

    @Serializable
    data object CoupleProfile : HomeNavigation()

    @Serializable
    data object EditCoupleProfile : HomeNavigation()

}

fun NavGraphBuilder.homeGraph(
    navController: NavController,
    coupleProfileViewModel: CoupleProfileViewModel,
    chatViewModel: ChatViewModel,
) {

    composable<HomeNavigation.Home> { _ ->
        HomeScreen(
            onNavigateToChat = {
                navController.navigate(route = HomeNavigation.CoupleProfile)
            },
            onNavigateToFindYourPartner = {
                navController.navigate(route = ChatNav.FindPartnerScreen)
            }
        )
    }

    composable<HomeNavigation.CoupleProfile> {
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

    composable<HomeNavigation.EditCoupleProfile> {
        val screenState by coupleProfileViewModel.state.collectAsStateWithLifecycle()
        val profile = screenState.profile ?: CoupleProfileUiState()

        EditCoupleProfileScreen(
            initialProfile = profile,
            isSaving = screenState.isSaving,
            errorTitle = screenState.errorTitle,
            errorMessage = screenState.errorMessage,
            onDismissError = coupleProfileViewModel::clearError,
            onNavigateBack = navController::popBackStack,
            onSave = { updatedProfile, coverPhotoPath ->
                coupleProfileViewModel.saveProfile(
                    profile = updatedProfile,
                    coverPhotoPath = coverPhotoPath,
                    onSuccess = navController::popBackStack,
                )
            },
        )
    }
}
