package com.srisu.srisu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.home.connection.screen.ConnectionScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import kotlinx.serialization.Serializable

sealed class ConnectionNav : Route {

    @Serializable
    data object Connection : ConnectionNav()

    @Serializable
    data class Profile(val userProfileData: String?) : ConnectionNav()
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.connectionGraph(
    navController: NavController,
) {

    composable<ConnectionNav.Connection> {
        ConnectionScreen(
            onNavigateToProfile = { userProfileData ->
                navController.navigate(ConnectionNav.Profile(userProfileData = userProfileData))
            }
        )
    }
    composable<ConnectionNav.Profile> { backStackEntry ->
        val userProfileData =
            backStackEntry.toRoute<ConnectionNav.Profile>().userProfileData
        ProfileScreen(userProfileData = userProfileData)
    }

}