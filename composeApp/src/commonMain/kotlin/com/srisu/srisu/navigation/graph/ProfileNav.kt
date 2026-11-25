package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.features.auth.data.remote.response.User
import com.srisu.srisu.features.home.profile.presentation.screen.EditProfileScreen
import com.srisu.srisu.features.home.profile.presentation.screen.InterestScreen
import com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

sealed interface ProfileNav : Route {
    @Serializable data object Settings : ProfileNav
    @Serializable data class SettingsDetail(val page: String) : ProfileNav
    @Serializable data object SharedNotifications : ProfileNav
    @Serializable data object NotificationSettings : ProfileNav
    @Serializable data object EditProfile : ProfileNav
    @Serializable data object EditProfileDetails : ProfileNav
    @Serializable data class InterestScreen(val requester: String, val requestId: String) : ProfileNav
}
private const val REQUEST = "interest.request.v2"
private const val RESULT = "interest.result.v2"

@OptIn(ExperimentalUuidApi::class)
fun NavGraphBuilder.profileGraph(controller: NavController) {
    composable<ProfileNav.Settings> { entry ->
        val nav = AppNavigator(controller, entry)
        com.srisu.srisu.features.settings.SettingsScreen(nav::back, nav::open)
    }
    composable<ProfileNav.SettingsDetail> { entry ->
        val nav = AppNavigator(controller, entry)
        com.srisu.srisu.features.settings.SettingsDetailScreen(entry.toRoute<ProfileNav.SettingsDetail>().page, nav::back, nav::open)
    }
    composable<ProfileNav.SharedNotifications> { entry ->
        com.srisu.srisu.features.notifications.NotificationSettings(sharedOnly=true, onBack={AppNavigator(controller,entry).back()})
    }
    composable<ProfileNav.NotificationSettings> { entry ->
        com.srisu.srisu.features.notifications.NotificationSettings { AppNavigator(controller,entry).back() }
    }
    composable<ProfileNav.EditProfile> { entry -> ProfileEditorDestination(controller, entry) }
    composable<ProfileNav.EditProfileDetails> { entry ->
        ProfileEditorDestination(controller, entry, AppNavigator(controller, entry)::back)
    }
    composable<ProfileNav.InterestScreen> { entry ->
        val route = entry.toRoute<ProfileNav.InterestScreen>()
        val origin = controller.previousBackStackEntry?.takeIf { it.id == route.requester && it.savedStateHandle.get<String>(REQUEST) == route.requestId }
        val nav = AppNavigator(controller, entry)
        if (origin == null) { com.srisu.srisu.navigation.NavigationUnavailable(nav::back) }
        else {
            val vm = koinViewModel<EditProfileViewModel>(viewModelStoreOwner = origin)
            val state by vm.editProfileUIState.collectAsState()
            val catalogue by vm.catalogueState.collectAsState()
            InterestScreen(interests = state.interestList, currentInterests = state.currentInterests,
                catalogue = catalogue, onRetry = vm::refreshCatalogue,
                onCancel = {
                    if (nav.isCurrent() && origin.savedStateHandle.get<String>(REQUEST) == route.requestId) {
                        origin.savedStateHandle.remove<String>(REQUEST); nav.back()
                    }
                },
                onInterestSelected = { interests ->
                    if (nav.isCurrent() && origin.savedStateHandle.get<String>(REQUEST) == route.requestId) {
                        origin.savedStateHandle[RESULT] = Json.encodeToString(InterestPickerResult(route.requestId,
                            interests.orEmpty().filterNotNull().filter { it.removed != true }.mapNotNull { it.interest }))
                        nav.back()
                    }
                })
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ProfileEditorDestination(controller: NavController, entry: androidx.navigation.NavBackStackEntry,
    onBack: (() -> Unit)? = null) {
        val vm = koinViewModel<EditProfileViewModel>()
        val result by entry.savedStateHandle.getStateFlow<String?>(RESULT, null).collectAsState()
        val initialized by vm.isInitialized.collectAsState()
        val profileState by vm.editProfileUIState.collectAsState()
        LaunchedEffect(result, initialized, profileState.interestList) {
            if (!initialized || profileState.interestList == null) return@LaunchedEffect
            result?.let { encoded ->
                val selection = decodeInterestResult(entry.savedStateHandle[REQUEST], encoded)
                if (selection == null) {
                    entry.savedStateHandle[RESULT] = null
                    return@LaunchedEffect
                }
                val ids = selection.ids
                val state = vm.editProfileUIState.value
                val old = state.currentInterests.orEmpty().filterNotNull()
                val updated = old.map { if (it.interest == null) it else it.copy(removed = it.interest !in ids) } +
                    state.interestList.orEmpty().filterNotNull().filter { it.id in ids && old.none { o -> o.interest == it.id } }
                        .map { User.UserInterest(interest = it.id, name = it.name, removed = false) }
                vm.updateCurrentInterests(updated)
                entry.savedStateHandle[RESULT] = null
                entry.savedStateHandle.remove<String>(REQUEST)
            }
        }
        EditProfileScreen(editProfileViewModel = vm, onBack = onBack,
            onExplore = if(onBack==null) ({AppNavigator(controller,entry).open(HomeNavigation.Explore)}) else null,
            onNotifications = if(onBack==null) ({AppNavigator(controller,entry).open(ProfileNav.Settings)}) else null,
            onSystemBack = AppNavigator(controller, entry)::back, onNavigateInterestScreen = { _, _ ->
            val navigator = AppNavigator(controller, entry)
            if (navigator.isCurrent()) {
                val id = Uuid.random().toString()
                entry.savedStateHandle[REQUEST] = id
                navigator.open(ProfileNav.InterestScreen(entry.id, id))
            }
        })
}
