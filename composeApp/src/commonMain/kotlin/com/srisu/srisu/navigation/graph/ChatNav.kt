package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.srisu.srisu.features.chat.matrix.MatrixChatEntryScreen
import com.srisu.srisu.features.chat.matrix.MatrixChatEntryViewModel
import com.srisu.srisu.features.chat.matrix.normalizeDjangoRoomIdOrNull
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatScreen
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.screen.FindYourPartnerScreen
import com.srisu.srisu.features.chat.presentation.findpartner.screen.InviteSentScreen
import com.srisu.srisu.features.chat.presentation.findpartner.screen.ReceivedLoveRequestScreen
import com.srisu.srisu.features.chat.presentation.findpartner.screen.YouAreConnectedScreen
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import com.srisu.srisu.features.home.profile.presentation.screen.ProfileScreen
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import com.srisu.srisu.navigation.AppNavigator
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

sealed interface ChatNav : Route {
    /**
     * Stable app route for the active couple conversation. [roomId] is the
     * Django ChatRoom UUID, never a Matrix room id. A null id resolves the
     * authenticated user's active relationship before opening the room.
     */
    @Serializable
    data class CoupleConversation(
        val roomId: String? = null,
        val messageId: String? = null,
        val origin: String = "chat",
    ) : ChatNav

    @Serializable data object PartnerFlow : ChatNav
    @Serializable data object PartnerRequests : ChatNav
    @Serializable data object FindPartnerScreen : ChatNav
    @Serializable data class PartnerPreview(val userId: Long) : ChatNav
    @Serializable data object RequestReceivedScreen : ChatNav
    @Serializable data object InviteSent : ChatNav
    @Serializable data object YoureConnected : ChatNav
}

fun NavGraphBuilder.chatGraph(controller: NavController) {
    navigation<ChatNav.PartnerFlow>(startDestination = ChatNav.FindPartnerScreen) {
        composable<ChatNav.PartnerRequests> { entry ->
            val vm = partnerModel(controller, entry)
            val requestsVm = koinViewModel<CoupleConnectionViewModel>()
            val requestsState by requestsVm.coupleConnectionUiState.collectAsStateWithLifecycle()
            val nav = AppNavigator(controller, entry)
            LaunchedEffect(Unit) { vm.onScreenEntered() }
            LaunchedEffect(requestsState.acceptedConnection) {
                requestsState.acceptedConnection?.let { accepted ->
                    vm.recordConfirmedConnection(accepted)
                    requestsVm.consumeAcceptedConnection(accepted.requestId)
                }
            }
            NavigateOnPartnerAccepted(vm) {
                nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow)
            }
            com.srisu.srisu.features.home.connection.presentation.coupleconnection.screen.CoupleConnectionScreen(
                coupleConnectionViewModel = requestsVm,
                onNavigateBack = nav::back,
            ) { user ->
                user?.let { vm.rememberPreview(it) }?.let { nav.open(ChatNav.PartnerPreview(it)) }
            }
        }
        composable<ChatNav.FindPartnerScreen> { entry ->
            val vm = partnerModel(controller, entry)
            val nav = AppNavigator(controller, entry)
            NavigateOnPartnerAccepted(vm) {
                nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow)
            }
            FindYourPartnerScreen(
                findPartnerViewModel = vm,
                onNavigateBack = nav::back,
                onNavigateToInviteSent = { nav.open(ChatNav.InviteSent) },
                onNavigateToProfile = { id -> id?.let { nav.open(ChatNav.PartnerPreview(it)) } },
                onContinue = { nav.root(HomeNavigation.Home) },
            )
        }
        composable<ChatNav.InviteSent> { entry ->
            val vm = partnerModel(controller, entry)
            val nav = AppNavigator(controller, entry)
            NavigateOnPartnerAccepted(vm) {
                nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow)
            }
            InviteSentScreen(vm, onNavigateBack = nav::back)
        }
        composable<ChatNav.RequestReceivedScreen> { entry ->
            val vm = partnerModel(controller, entry)
            val nav = AppNavigator(controller, entry)
            NavigateOnPartnerAccepted(vm) {
                nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow)
            }
            ReceivedLoveRequestScreen(
                findPartnerViewModel = vm,
                onNavigateBack = nav::back,
                onNavigateToProfile = { id -> id?.let { nav.open(ChatNav.PartnerPreview(it)) } },
            )
        }
        composable<ChatNav.PartnerPreview> { entry ->
            val user = partnerModel(controller, entry)
                .previewUser(entry.toRoute<ChatNav.PartnerPreview>().userId)
            val nav = AppNavigator(controller, entry)
            if (user == null) {
                com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
            } else {
                val vm = koinViewModel<ProfileViewModel>()
                LaunchedEffect(user.id) { vm.setUserProfileData(user) }
                ProfileScreen(vm)
            }
        }
        composable<ChatNav.YoureConnected> { entry ->
            val vm = partnerModel(controller, entry)
            val state by vm.findPartnerUIState.collectAsStateWithLifecycle()
            val nav = AppNavigator(controller, entry)
            YouAreConnectedScreen(
                state = state,
                onViewOurSpace = { nav.root(HomeNavigation.Home) },
                onOpenConversation = { roomId ->
                    nav.leaveFlow(
                        ChatNav.CoupleConversation(
                            roomId = roomId,
                            origin = "acceptance",
                        ),
                        ChatNav.PartnerFlow,
                    )
                },
            )
        }
    }

    composable<ChatNav.CoupleConversation> { entry ->
        val route = entry.toRoute<ChatNav.CoupleConversation>()
        val nav = AppNavigator(controller, entry)
        val roomId = route.roomId
        if (roomId == null) {
            MatrixChatEntryScreen(
                viewModel = koinViewModel<MatrixChatEntryViewModel>(),
                onOpenRoom = { resolvedRoomId ->
                    nav.replace(route.copy(roomId = resolvedRoomId))
                },
            )
        } else if (normalizeDjangoRoomIdOrNull(roomId) == null) {
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        } else {
            val vm = koinViewModel<SecureChatViewModel>()
            LaunchedEffect(roomId) { vm.openRoom(roomId) }
            DisposableEffect(entry, roomId) {
                onDispose { vm.closeRoom() }
            }
            SecureChatScreen(viewModel = vm, onBack = nav::back)
        }
    }
}

@Composable
private fun partnerModel(
    controller: NavController,
    entry: NavBackStackEntry,
): FindPartnerViewModel {
    val parent = remember(entry) { controller.getBackStackEntry(ChatNav.PartnerFlow) }
    return koinViewModel(viewModelStoreOwner = parent)
}

@Composable
private fun NavigateOnPartnerAccepted(
    vm: FindPartnerViewModel,
    onAccepted: () -> Boolean,
) {
    val state by vm.findPartnerUIState.collectAsStateWithLifecycle()
    LaunchedEffect(state.acceptedNavigationPending) {
        consumeAcceptedNavigation(
            pending = state.acceptedNavigationPending,
            navigate = onAccepted,
            consume = vm::onAcceptedNavigated,
        )
    }
}

internal fun consumeAcceptedNavigation(
    pending: Boolean,
    navigate: () -> Boolean,
    consume: () -> Unit,
) {
    if (pending && navigate()) consume()
}
