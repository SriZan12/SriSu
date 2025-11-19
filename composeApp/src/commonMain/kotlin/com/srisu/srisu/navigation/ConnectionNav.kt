package com.srisu.srisu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.home.connection.coupleconnection.screen.CoupleConnectionScreen
import com.srisu.srisu.features.home.connection.singleconnection.screen.SingleConnectionScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import kotlinx.serialization.Serializable

sealed class ConnectionNav : Route {
    @Serializable
    data object Connection : ConnectionNav()

    @Serializable
    data class Profile(val userProfileData: String?) : ConnectionNav()
    @Serializable
    data object LoveRequestScreen : ChatNav()
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.connectionGraph(
    navController: NavController,
) {

    composable<ConnectionNav.Connection> {
        SingleConnectionScreen(
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

    composable<ConnectionNav.LoveRequestScreen>() {
        CoupleConnectionScreen(
            onNavigateToProfile = {
                navController.navigate(ConnectionNav.Profile(userProfileData = it))
            }
        )
    }

}