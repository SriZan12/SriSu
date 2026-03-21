package com.srisu.srisu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.home.connection.coupleconnection.screen.CoupleConnectionScreen
import com.srisu.srisu.features.home.connection.singleconnection.screen.SingleConnectionScreen
import com.srisu.srisu.features.home.connection.singleconnection.vm.SingleConnectionViewModel
import com.srisu.srisu.features.profile.screen.ProfileScreen
import com.srisu.srisu.features.profile.vm.ProfileViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

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
        val singleConnectionViewModel = koinViewModel<SingleConnectionViewModel>()
        SingleConnectionScreen(
            singleConnectionViewModel = singleConnectionViewModel,
            onNavigateToProfile = { userProfileData ->
                navController.navigate(ConnectionNav.Profile(userProfileData = userProfileData))
            }
        )
    }
    composable<ConnectionNav.Profile> { backStackEntry ->
        val profileViewModel: ProfileViewModel = koinViewModel<ProfileViewModel>()
        val userProfileData =
            backStackEntry.toRoute<ConnectionNav.Profile>().userProfileData
        profileViewModel.setUserProfileData(userProfileData = userProfileData)

        ProfileScreen(profileViewModel = profileViewModel)
    }

    composable<ConnectionNav.LoveRequestScreen>() {
        CoupleConnectionScreen(
            onNavigateToProfile = {
                navController.navigate(ConnectionNav.Profile(userProfileData = it))
            }
        )
    }

}