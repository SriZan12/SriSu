package com.srisu.srisu.navigation.graph

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.features.home.entertainment.screen.HomeScreen
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import kotlinx.serialization.Serializable

sealed interface HomeNavigation : Route {
    @Serializable data object Home : HomeNavigation
    @Serializable data object Explore : HomeNavigation
}
fun NavGraphBuilder.homeGraph(controller: NavHostController) {
    composable<HomeNavigation.Home> { entry ->
        val nav = AppNavigator(controller, entry)
        HomeScreen(onFindPartner = { nav.open(ChatNav.PartnerFlow) },
            onExplore = { nav.switchTab(HomeNavigation.Explore) },
            onCoupleProfile = { nav.open(CoupleProfileDestination()) },
            onInvitations = { nav.open(ChatNav.PartnerRequests) },
            onChat = { nav.open(ChatNav.ChatRoomScreen) })
    }
}
