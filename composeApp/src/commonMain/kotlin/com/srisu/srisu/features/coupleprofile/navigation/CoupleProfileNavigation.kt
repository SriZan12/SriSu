package com.srisu.srisu.features.coupleprofile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.features.coupleprofile.presentation.CoupleProfileScreen
import kotlinx.serialization.Serializable

@Serializable enum class CoupleSection { PROFILE, PREVIEW, STORY, STORY_EDIT, SONG, INTERESTS, COVER, DATE, SHARING, PLANS, NEW_PLAN, PLAN, ANSWER }
@Serializable data class CoupleProfileDestination(val coupleId: Long? = null, val section: CoupleSection = CoupleSection.PROFILE, val planId: Long? = null) : Route

fun NavGraphBuilder.coupleProfileGraph(controller: NavController) {
    composable<CoupleProfileDestination> { entry ->
        val route = entry.toRoute<CoupleProfileDestination>()
        val nav = AppNavigator(controller, entry)
        if ((route.coupleId != null && route.coupleId <= 0) || (route.planId != null && route.planId <= 0)) {
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
            return@composable
        }
        CoupleProfileScreen(route.coupleId, onBack = nav::back, initialPage = route.section.name, planId = route.planId,
            onOpenCouple = { nav.open(CoupleProfileDestination(it)) })
    }
    composable<HomeNavigation.Explore> { entry ->
        val nav = AppNavigator(controller, entry)
        CoupleProfileScreen(null, onBack = nav::back, initialPage = "DISCOVER",
            onOpenCouple = { nav.open(CoupleProfileDestination(it)) })
    }
}
