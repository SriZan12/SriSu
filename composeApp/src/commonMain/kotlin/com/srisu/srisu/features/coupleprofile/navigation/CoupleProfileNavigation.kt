package com.srisu.srisu.features.coupleprofile.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.features.coupleprofile.presentation.CoupleProfileScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

@Serializable enum class CoupleSection { PROFILE, PREVIEW, STORY, STORY_EDIT, SONG, INTERESTS, COVER, DATE, SHARING, PLANS, NEW_PLAN, PLAN, ANSWER }
@Serializable data class CoupleProfileDestination(val coupleId: Long? = null, val section: CoupleSection = CoupleSection.PROFILE, val planId: Long? = null) : Route

private val CoupleSectionNavType = object : NavType<CoupleSection>(isNullableAllowed = false) {
    override val name: String = "CoupleSection"

    override fun put(bundle: SavedState, key: String, value: CoupleSection) {
        bundle.write { putString(key, value.name) }
    }

    override fun get(bundle: SavedState, key: String): CoupleSection? = bundle.read {
        if (contains(key) && !isNull(key)) parseValue(getString(key)) else null
    }

    override fun parseValue(value: String): CoupleSection =
        CoupleSection.entries.firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Unknown couple profile section: $value")

    override fun serializeAsValue(value: CoupleSection): String = value.name
}

internal val CoupleProfileTypeMap = mapOf(typeOf<CoupleSection>() to CoupleSectionNavType)

internal fun NavGraphBuilder.coupleProfileDestination(
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<CoupleProfileDestination>(typeMap = CoupleProfileTypeMap, content = content)
}

fun NavGraphBuilder.coupleProfileGraph(controller: NavController) {
    coupleProfileDestination { entry ->
        val route = entry.toRoute<CoupleProfileDestination>()
        val nav = AppNavigator(controller, entry)
        if ((route.coupleId != null && route.coupleId <= 0) || (route.planId != null && route.planId <= 0)) {
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
            return@coupleProfileDestination
        }
        CoupleProfileScreen(route.coupleId, onBack = nav::back, initialPage = route.section.name, planId = route.planId,
            onOpenCouple = { nav.open(CoupleProfileDestination(it)) },
            onMessage = { nav.open(ChatNav.CoupleConversation(origin = "couple_profile")) })
    }
    composable<HomeNavigation.Explore> { entry ->
        val nav = AppNavigator(controller, entry)
        com.srisu.srisu.features.moments.presentation.MomentDiscoveryScreen(
            onBack = nav::back,
            onOpen = { id,couple -> nav.open(com.srisu.srisu.features.moments.navigation.MomentViewerDestination(id,couple)) },
            onProfile = { nav.open(CoupleProfileDestination(it)) })
    }
}
