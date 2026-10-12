package com.srisu.srisu.features.coupleprofile.navigation

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

/** Navigation 2.9.1 does not provide enum NavTypes on non-Android targets. */
private object CoupleSectionNavType : NavType<CoupleSection>(isNullableAllowed = false) {
    override val name: String = "couple_section"

    override fun put(bundle: SavedState, key: String, value: CoupleSection) {
        bundle.write { putString(key, value.name) }
    }

    override fun get(bundle: SavedState, key: String): CoupleSection? =
        bundle.read { getStringOrNull(key)?.let(CoupleSection::valueOf) }

    override fun parseValue(value: String): CoupleSection = CoupleSection.valueOf(value)

    override fun serializeAsValue(value: CoupleSection): String = value.name
}

fun NavGraphBuilder.coupleProfileGraph(controller: NavController) {
    composable<CoupleProfileDestination>(
        typeMap = mapOf(typeOf<CoupleSection>() to CoupleSectionNavType)
    ) { entry ->
        val route = entry.toRoute<CoupleProfileDestination>()
        val nav = AppNavigator(controller, entry)
        if ((route.coupleId != null && route.coupleId <= 0) || (route.planId != null && route.planId <= 0)) {
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
            return@composable
        }
        CoupleProfileScreen(route.coupleId, onBack = nav::back, initialPage = route.section.name, planId = route.planId,
            onOpenCouple = { nav.open(CoupleProfileDestination(it)) },
            onMessage = { nav.open(ChatNav.CoupleConversation(origin = "couple_profile")) })
    }
    composable<HomeNavigation.Explore> { entry ->
        val nav = AppNavigator(controller, entry)
        CoupleProfileScreen(null, onBack = nav::back, initialPage = "DISCOVER",
            onOpenCouple = { nav.open(CoupleProfileDestination(it)) },
            onMessage = { nav.open(ChatNav.CoupleConversation(origin = "couple_profile")) })
    }
}
