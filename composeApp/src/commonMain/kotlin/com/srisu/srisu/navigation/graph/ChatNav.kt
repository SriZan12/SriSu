package com.srisu.srisu.navigation.graph

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.chat.presentation.chat.screen.ChatRoomScreen
import com.srisu.srisu.features.chat.presentation.chat.screen.ChatScreen
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.screen.FindYourPartnerScreen
import com.srisu.srisu.core.session.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class ChatNav : Route {

    @Serializable
    data object FindPartnerScreen : ChatNav()

    @Serializable
    data class ChatScreen(val chatRoomData: String?) : ChatNav()

    @Serializable
    data object ChatRoomScreen : ChatNav()


}

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    chatViewModel: ChatViewModel,
    session: Session?
) {
    composable<ChatNav.FindPartnerScreen> {
        FindYourPartnerScreen()
    }

    composable<ChatNav.ChatScreen> { backStackEntry ->
        val chatRoomData = backStackEntry.toRoute<ChatNav.ChatScreen>().chatRoomData
        ChatScreen(
            viewModel = chatViewModel,
            chatRoomData = chatRoomData,
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
            val chatRoomData = Json.encodeToString(chatRoom)
            navController.navigate(ChatNav.ChatScreen(chatRoomData))
        }

    }
}

