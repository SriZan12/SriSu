package com.srisu.srisu.navigation.graph

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.entertainment.screen.HomeScreen
import com.srisu.srisu.navigation.graph.Route
import kotlinx.serialization.Serializable

@Serializable
sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

    @Serializable data class CoupleProfile(val coupleId: Long? = null, val page: String? = null, val planId: Long? = null) : HomeNavigation()

}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(onFindPartner: () -> Unit, navController: androidx.navigation.NavHostController) {

    composable<HomeNavigation.CoupleProfile> { entry ->
        val route = entry.toRoute<HomeNavigation.CoupleProfile>()
        com.srisu.srisu.features.coupleprofile.presentation.CoupleProfileScreen(route.coupleId, onBack = { navController.popBackStack() }, initialPage = route.page, planId = route.planId, onOpenCouple = { id -> navController.navigate(HomeNavigation.CoupleProfile(id)) })
    }

    composable<HomeNavigation.Home> { _ ->
        HomeScreen(onFindPartner = onFindPartner, onExplore = { navController.navigate(HomeNavigation.CoupleProfile(page = "DISCOVER")) }, onCoupleProfile = { navController.navigate(HomeNavigation.CoupleProfile()) })
    }

}

