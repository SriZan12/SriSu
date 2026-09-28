package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.ProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.screen.authscreen.PhoneNumberScreen
import com.srisu.srisu.features.auth.presentation.screen.authscreen.PhoneNumberVerificationScreen
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed class AuthNavigation : Route {

    @Serializable
    data object PhoneNumberScreen : AuthNavigation()

    @Serializable
    data object PhoneNumberVerificationScreen : AuthNavigation()

    @Serializable
    data object ProfileSetUp : AuthNavigation()
}

fun NavGraphBuilder.authGraph(navController: NavController, authViewModel: AuthViewModel, onLeaveAuthentication: () -> Unit = {}) {
    composable<AuthNavigation.PhoneNumberScreen> {
        val state by authViewModel.authUiState.collectAsState()
        LaunchedEffect(state.challengeId) {
            if (state.challengeId != null) navController.navigate(AuthNavigation.PhoneNumberVerificationScreen) { launchSingleTop = true }
        }
        PhoneNumberScreen(
            authViewModel = authViewModel,
            onBack = { authViewModel.abandonChallenge(); onLeaveAuthentication() },
            onNavToOTPScreen = {
                navController.navigate(AuthNavigation.PhoneNumberVerificationScreen)
            }
        )
    }

    composable<AuthNavigation.PhoneNumberVerificationScreen> {
        val state by authViewModel.authUiState.collectAsState()
        LaunchedEffect(state.challengeId) {
            if (state.challengeId == null) navController.popBackStack()
        }
        PhoneNumberVerificationScreen(
            navController = navController,
            authViewModel = authViewModel
        )
    }

    composable<AuthNavigation.ProfileSetUp> { _ ->
        ProfileSetupScreen(navController = navController, authViewModel = authViewModel)
    }

}
