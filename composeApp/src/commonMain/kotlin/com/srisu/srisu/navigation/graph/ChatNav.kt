package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import com.srisu.srisu.features.coupleprofile.navigation.CoupleSection
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.presentation.chat.screen.*
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.v2.presentation.SecureChatScreen
import com.srisu.srisu.features.chat.v2.presentation.SecureChatViewModel
import com.srisu.srisu.features.chat.v2.presentation.ChatV2RolloutPolicy
import com.srisu.srisu.features.chat.v2.presentation.ChatEntryTarget
import com.srisu.srisu.features.chat.presentation.findpartner.screen.*
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.home.profile.presentation.vm.ProfileViewModel
import com.srisu.srisu.features.home.profile.presentation.screen.ProfileScreen
import com.srisu.srisu.features.home.connection.presentation.coupleconnection.vm.CoupleConnectionViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable

sealed interface ChatNav : Route {
    @Serializable data object PartnerFlow : ChatNav
    @Serializable data object PartnerRequests : ChatNav
    @Serializable data object FindPartnerScreen : ChatNav
    @Serializable data class PartnerPreview(val userId: Long) : ChatNav
    @Serializable data class ChatScreen(val roomId: String) : ChatNav
    @Serializable data object ChatRoomScreen : ChatNav
    @Serializable data object RequestReceivedScreen : ChatNav
    @Serializable data object InviteSent : ChatNav
    @Serializable data object YoureConnected : ChatNav
}
fun NavGraphBuilder.chatGraph(controller: NavController, session: Session?) {
    navigation<ChatNav.PartnerFlow>(startDestination = ChatNav.FindPartnerScreen) {
        composable<ChatNav.PartnerRequests> { entry ->
            val vm = partnerModel(controller, entry)
            val requestsVm = koinViewModel<CoupleConnectionViewModel>()
            val requestsState by requestsVm.coupleConnectionUiState.collectAsState()
            val nav = AppNavigator(controller, entry)
            LaunchedEffect(Unit) { vm.onScreenEntered() }
            LaunchedEffect(requestsState.acceptedConnection) {
                requestsState.acceptedConnection?.let { accepted ->
                    vm.recordConfirmedConnection(accepted)
                    requestsVm.consumeAcceptedConnection(accepted.requestId)
                }
            }
            NavigateOnPartnerAccepted(vm) { nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow) }
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
            NavigateOnPartnerAccepted(vm) { nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow) }
            FindYourPartnerScreen(vm, onNavigateBack = nav::back,
                onNavigateToInviteSent = { nav.open(ChatNav.InviteSent) },
                onNavigateToProfile = { id -> id?.let { nav.open(ChatNav.PartnerPreview(it)) } },
                onContinue = { nav.root(HomeNavigation.Home) })
        }
        composable<ChatNav.InviteSent> { entry ->
            val vm = partnerModel(controller, entry)
            val nav = AppNavigator(controller, entry)
            NavigateOnPartnerAccepted(vm) { nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow) }
            InviteSentScreen(vm, onNavigateBack = nav::back)
        }
        composable<ChatNav.RequestReceivedScreen> { entry ->
            val vm = partnerModel(controller, entry)
            val nav = AppNavigator(controller, entry)
            NavigateOnPartnerAccepted(vm) { nav.finishFlow(ChatNav.YoureConnected, ChatNav.PartnerFlow) }
            ReceivedLoveRequestScreen(vm, onNavigateBack = nav::back,
                onNavigateToProfile = { id -> id?.let { nav.open(ChatNav.PartnerPreview(it)) } })
        }
        composable<ChatNav.PartnerPreview> { entry ->
            val user = partnerModel(controller, entry).previewUser(entry.toRoute<ChatNav.PartnerPreview>().userId)
            val nav = AppNavigator(controller, entry)
            if (user == null) { com.srisu.srisu.navigation.NavigationUnavailable(nav::back) }
            else {
                val vm = koinViewModel<ProfileViewModel>()
                LaunchedEffect(user.id) { vm.setUserProfileData(user) }
                ProfileScreen(vm)
            }
        }
        composable<ChatNav.YoureConnected> { entry ->
            val vm = partnerModel(controller, entry)
            val state by vm.findPartnerUIState.collectAsState()
            val nav = AppNavigator(controller, entry)
            val rollout = koinInject<ChatV2RolloutPolicy>()
            YouAreConnectedScreen(
                state = state,
                onViewOurSpace = { nav.root(HomeNavigation.Home) },
                onOpenConversation = if (rollout.exposesSecureAcceptanceAction)
                    ({ roomId -> nav.open(ChatNav.ChatScreen(roomId)) }) else null,
            )
        }
    }
    composable<ChatNav.ChatScreen> { entry ->
        val nav = AppNavigator(controller, entry)
        val roomId = entry.toRoute<ChatNav.ChatScreen>().roomId
        if (com.srisu.srisu.navigation.parseExternalEntry("srisu://chats/$roomId") == null) {
            com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
            return@composable
        }
        val rollout = koinInject<ChatV2RolloutPolicy>()
        if (rollout.entryTarget == ChatEntryTarget.SECURE_V2) {
            val vm = koinViewModel<SecureChatViewModel>()
            LaunchedEffect(roomId) { vm.openRoom(roomId) }
            DisposableEffect(entry) { onDispose { vm.closeRoom() } }
            SecureChatScreen(viewModel = vm, onBack = nav::back)
        } else {
            // This is an explicit pre-v2 compatibility policy, never a runtime
            // fallback after secure capabilities, crypto, or synchronization fail.
            val vm = koinViewModel<ChatViewModel>()
            LaunchedEffect(roomId) { vm.updateSession(session); vm.openRoom(roomId) }
            DisposableEffect(entry) { onDispose { vm.clearActiveChatRoom() } }
            CompositionLocalProvider(com.srisu.srisu.features.coupleprofile.presentation.LocalProfileNavigation provides { action ->
                nav.open(CoupleProfileDestination(action.coupleId, if (action.kind == "story") CoupleSection.STORY_EDIT else CoupleSection.PLAN, action.planId))
            }) { ChatScreen(viewModel = vm, session = session, onNavBack = nav::back) }
        }
    }
    composable<ChatNav.ChatRoomScreen> { entry ->
        val vm = koinViewModel<ChatViewModel>()
        LaunchedEffect(session?.id) { vm.updateSession(session) }
        ChatRoomScreen(vm) { room -> room.id?.let { AppNavigator(controller, entry).open(ChatNav.ChatScreen(it)) } }
    }
}
@Composable private fun partnerModel(controller: NavController, entry: NavBackStackEntry): FindPartnerViewModel {
    val parent = remember(entry) { controller.getBackStackEntry(ChatNav.PartnerFlow) }
    return koinViewModel(viewModelStoreOwner = parent)
}
@Composable private fun NavigateOnPartnerAccepted(vm: FindPartnerViewModel, onAccepted: () -> Unit) {
    val state by vm.findPartnerUIState.collectAsState()
    LaunchedEffect(state.acceptedPartnerName) { if (state.acceptedPartnerName != null) onAccepted() }
}
