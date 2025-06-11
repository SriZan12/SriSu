package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.auth.screens.BaseAuthScreen
import kotlinx.serialization.Serializable

sealed class AuthNavigation: Route {
    @Serializable
    data object Auth : AuthNavigation()
}

fun NavGraphBuilder.authGraph(navController: NavController) {
    composable<AuthNavigation.Auth> { _ ->
        BaseAuthScreen(navController = navController)
    }

}