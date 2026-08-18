package com.srisu.srisu.navigation.graph

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

sealed class ChatNav : Route {

    @Serializable
    data object FindPartnerScreen : ChatNav()

    @Serializable
    data object ChatScreen : ChatNav()

    @Serializable
    data object ChatRoomScreen : ChatNav()

    @Serializable
    data object RequestReceivedScreen : ChatNav()

}

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    chatViewModel: ChatViewModel,
    findPartnerViewModel: FindPartnerViewModel,
    session: Session?
) {
    composable<ChatNav.FindPartnerScreen> {
        FindYourPartnerScreen(
            findPartnerViewModel = findPartnerViewModel
        ) {
            navController.navigate(ChatNav.RequestReceivedScreen)
        }
    }

    composable<ChatNav.RequestReceivedScreen> {
        ReceivedLoveRequestScreen(
            findPartnerViewModel = findPartnerViewModel,
            onNavigateToProfile = {
                navController.navigate(ConnectionNav.Profile(userProfileData = it))
            },
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavToChatScreen = {
                navController.navigate(ChatNav.ChatScreen)
            }
        )
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

