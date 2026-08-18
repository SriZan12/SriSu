package com.srisu.srisu.navigation.graph

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.presentation.chat.screen.ChatScreen
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.screen.FindYourPartnerScreen
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.home.screen.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

    @Serializable
    data object Chat : HomeNavigation()

    @Serializable
    data object FindPartnerScreen : HomeNavigation()

}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    findPartnerViewModel: FindPartnerViewModel,
    chatViewModel: ChatViewModel,
    session: Session?
) {

    composable<HomeNavigation.Home> { _ ->
        HomeScreen(
            onNavigateToChat = {
                navController.navigate(route = HomeNavigation.Chat)
            },
            onNavigateToFindYourPartner = {
                navController.navigate(route = HomeNavigation.FindPartnerScreen)
            }
        )
    }

    composable<HomeNavigation.Chat> {
        ChatScreen(
            viewModel = chatViewModel,
            session = session,
            onNavBack = {
                navController.navigateUp()
            }
        )
    }

    composable<HomeNavigation.FindPartnerScreen> {
        FindYourPartnerScreen(
            findPartnerViewModel = findPartnerViewModel,
            onNavToRequestReceivedScreen = {}
        )
    }

}

