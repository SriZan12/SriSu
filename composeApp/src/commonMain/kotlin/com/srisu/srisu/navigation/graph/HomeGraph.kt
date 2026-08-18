package com.srisu.srisu.navigation.graph

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.home.screen.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

}

fun NavGraphBuilder.homeGraph(
    navController: NavController
) {

    composable<HomeNavigation.Home> { _ ->
        HomeScreen(
            onNavigateToChat = {
                navController.navigate(route = ChatNav.ChatScreen)
            },
            onNavigateToFindYourPartner = {
                navController.navigate(route = ChatNav.FindPartnerScreen)
            }
        )
    }
}

