package com.srisu.srisu.navigation.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srisu.srisu.features.chat.presentation.findpartner.screen.YouAreConnectedScreen
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.chat.presentation.chat.screen.ChatRoomScreen
import com.srisu.srisu.features.chat.presentation.chat.screen.ChatScreen
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.screen.FindYourPartnerScreen
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.presentation.findpartner.screen.ReceivedLoveRequestScreen
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import kotlinx.serialization.Serializable
import com.srisu.srisu.features.chat.presentation.findpartner.screen.InviteSentScreen

sealed class ChatNav : Route {

    @Serializable
    data object FindPartnerScreen : ChatNav()

    @Serializable
    data object ChatScreen : ChatNav()

    @Serializable
    data object ChatRoomScreen : ChatNav()

    @Serializable
    data object RequestReceivedScreen : ChatNav()

    @Serializable
    data object InviteSent : ChatNav()

    @Serializable
    data object YoureConnected : ChatNav()

}

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    chatViewModel: ChatViewModel,
    findPartnerViewModel: FindPartnerViewModel,
    session: Session?
) {
    composable<ChatNav.FindPartnerScreen> {
        NavigateOnPartnerAccepted(findPartnerViewModel) {
            navController.navigate(ChatNav.YoureConnected) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
        FindYourPartnerScreen(
            findPartnerViewModel = findPartnerViewModel,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToInviteSent = { navController.navigate(ChatNav.InviteSent) { launchSingleTop = true } },
            onNavigateToProfile = { navController.navigate(ConnectionNav.Profile(userProfileData = it)) },
            onContinue = { navController.navigate(HomeNavigation.Home) {
                popUpTo<ChatNav.FindPartnerScreen> { inclusive = true }
                launchSingleTop = true
            } },
        )
    }

    composable<ChatNav.InviteSent> {
        NavigateOnPartnerAccepted(findPartnerViewModel) {
            navController.navigate(ChatNav.YoureConnected) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
        InviteSentScreen(findPartnerViewModel, onNavigateBack = { navController.popBackStack() })
    }

    composable<ChatNav.RequestReceivedScreen> {
        NavigateOnPartnerAccepted(findPartnerViewModel) {
            navController.navigate(ChatNav.YoureConnected) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
        ReceivedLoveRequestScreen(findPartnerViewModel, onNavigateBack = { navController.popBackStack() },
            onNavigateToProfile = { navController.navigate(ConnectionNav.Profile(userProfileData = it)) })
    }

    composable<ChatNav.YoureConnected> {
        val state by findPartnerViewModel.findPartnerUIState.collectAsStateWithLifecycle()
        LaunchedEffect(findPartnerViewModel) { findPartnerViewModel.onScreenEntered() }
        YouAreConnectedScreen(state) {
            navController.navigate(HomeNavigation.Home) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    composable<ChatNav.ChatScreen> { _ ->
        ChatScreen(
            viewModel = chatViewModel,
            session = session,
            onNavBack = {
                navController.popBackStack()
            }
        )
    }

    composable<ChatNav.ChatRoomScreen> {
        chatViewModel.updateSession(session = session)

        ChatRoomScreen(
            viewModel = chatViewModel
        ) { chatRoom ->
//            val chatRoomData = Json.encodeToString(chatRoom)
//            navController.navigate(ChatNav.ChatScreen(chatRoomData))
        }

    }
}


@Composable
private fun NavigateOnPartnerAccepted(viewModel: FindPartnerViewModel, onAccepted: () -> Unit) {
    val state by viewModel.findPartnerUIState.collectAsStateWithLifecycle()
    LaunchedEffect(state.acceptedPartnerName) {
        if (state.acceptedPartnerName != null) onAccepted()
    }
}
