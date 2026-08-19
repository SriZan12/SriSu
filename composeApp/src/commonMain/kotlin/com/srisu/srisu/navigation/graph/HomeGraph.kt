package com.srisu.srisu.navigation.graph

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.home.home.screen.HomeScreen
import com.srisu.srisu.features.home.coupleprofile.presentation.screen.CoupleProfileScreen
import com.srisu.srisu.features.home.coupleprofile.presentation.screen.EditCoupleProfileScreen
import com.srisu.srisu.features.home.coupleprofile.presentation.state.CoupleProfileUiState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class HomeNavigation : Route {
    @Serializable
    data object Home : HomeNavigation()

    @Serializable
    data object CoupleProfile : HomeNavigation()

    @Serializable
    data class EditCoupleProfile(val profileData: String) : HomeNavigation()

}

fun NavGraphBuilder.homeGraph(
    navController: NavController
) {

    composable<HomeNavigation.Home> { _ ->
        HomeScreen(
            onNavigateToChat = {
                navController.navigate(route = HomeNavigation.CoupleProfile)
            },
            onNavigateToFindYourPartner = {
                navController.navigate(route = ChatNav.FindPartnerScreen)
            }
        )
    }

    composable<HomeNavigation.CoupleProfile> { backStackEntry ->
        val savedProfile = backStackEntry.savedStateHandle
            .getStateFlow("couple_profile", "")
            .value
        val profile = savedProfile.takeIf(String::isNotBlank)
            ?.let { runCatching { Json.decodeFromString<CoupleProfileUiState>(it) }.getOrNull() }
            ?: CoupleProfileUiState()

        CoupleProfileScreen(
            uiState = profile,
            onNavigateBack = navController::popBackStack,
            onSendMessage = {
                navController.navigate(ChatNav.ChatScreen) {
                    launchSingleTop = true
                }
            },
            onPlanDate = {
                // UI-only extension point for the future date-planning flow.
            },
            onOpenSettings = {
                navController.navigate(
                    HomeNavigation.EditCoupleProfile(Json.encodeToString(profile))
                )
            },
        )
    }

    composable<HomeNavigation.EditCoupleProfile> { backStackEntry ->
        val profile = runCatching {
            Json.decodeFromString<CoupleProfileUiState>(
                backStackEntry.toRoute<HomeNavigation.EditCoupleProfile>().profileData
            )
        }.getOrDefault(CoupleProfileUiState())

        EditCoupleProfileScreen(
            initialProfile = profile,
            onNavigateBack = navController::popBackStack,
            onSave = { updatedProfile ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("couple_profile", Json.encodeToString(updatedProfile))
                navController.popBackStack()
            },
            onChangeCoverPhoto = {
                // UI extension point for the shared media picker.
            },
            onChangePartnerPhotos = {
                // UI extension point for partner photo management.
            },
        )
    }
}

