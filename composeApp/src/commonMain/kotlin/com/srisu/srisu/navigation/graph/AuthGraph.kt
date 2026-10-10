package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.ProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.screen.authscreen.PhoneNumberScreen
import com.srisu.srisu.features.auth.presentation.screen.authscreen.PhoneNumberVerificationScreen
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.serialization.Serializable

sealed interface AuthNavigation : Route {
    @Serializable data object Flow : AuthNavigation
    @Serializable data object PhoneNumberScreen : AuthNavigation
    @Serializable data object PhoneNumberVerificationScreen : AuthNavigation
    @Serializable data object ProfileSetUp : AuthNavigation
}

fun NavGraphBuilder.authGraph(controller: NavController, start: Route, onLeave: () -> Unit) {
    navigation<AuthNavigation.Flow>(startDestination = start) {
        composable<AuthNavigation.PhoneNumberScreen> { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AuthNavigation.Flow) }
            val vm = koinViewModel<AuthViewModel>(viewModelStoreOwner = parent)
            val state by vm.authUiState.collectAsState()
            val navigator = remember(entry) { AppNavigator(controller, entry) }
            LaunchedEffect(state.challengeId) {
                if (state.challengeId != null) navigator.open(AuthNavigation.PhoneNumberVerificationScreen)
            }
            PhoneNumberScreen(vm, onBack = { vm.abandonChallenge(); onLeave() })
        }
        composable<AuthNavigation.PhoneNumberVerificationScreen> { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AuthNavigation.Flow) }
            val vm = koinViewModel<AuthViewModel>(viewModelStoreOwner = parent)
            val navigator = remember(entry) { AppNavigator(controller, entry) }
            val state by vm.authUiState.collectAsState()
            LaunchedEffect(state.challengeId) { if (state.challengeId == null) navigator.back() }
            // Abandoning the challenge is the one state-driven back operation.
            PhoneNumberVerificationScreen(onBack = vm::abandonChallenge, authViewModel = vm)
        }
        composable<AuthNavigation.ProfileSetUp> { entry ->
            val parent = remember(entry) { controller.getBackStackEntry(AuthNavigation.Flow) }
            ProfileSetupScreen(authViewModel = koinViewModel(viewModelStoreOwner = parent))
        }
    }
}
