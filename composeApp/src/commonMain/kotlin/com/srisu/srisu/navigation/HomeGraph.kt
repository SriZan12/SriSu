package com.srisu.srisu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.home.home.screen.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

}

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph() {

    composable<HomeNavigation.Home> { _ ->
        HomeScreen()
    }

}

