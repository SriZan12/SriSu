package com.srisu.srisu.navigation.graph

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
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

fun NavGraphBuilder.authGraph(navController: NavController, authViewModel: AuthViewModel) {

    composable<AuthNavigation.PhoneNumberScreen> {
        PhoneNumberScreen(
            navController = navController,
            authViewModel = authViewModel
        )
    }

    composable<AuthNavigation.PhoneNumberVerificationScreen> {
        PhoneNumberVerificationScreen(
            navController = navController,
            authViewModel = authViewModel
        )
    }

    composable<AuthNavigation.ProfileSetUp> { _ ->
        ProfileSetupScreen(navController = navController)
    }

}