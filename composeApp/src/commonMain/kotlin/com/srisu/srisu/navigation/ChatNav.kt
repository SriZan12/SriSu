package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.features.chat.chatroom.screen.ChatRoomScreen
import com.srisu.srisu.features.chat.chatroom.screen.ChatScreen
import com.srisu.srisu.features.chat.couple.findpartner.FindYourPartnerScreen
import com.srisu.srisu.session.Session
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
    session: Session?
) {
    composable<ChatNav.FindPartnerScreen> {
        FindYourPartnerScreen()
    }

    composable<ChatNav.ChatScreen> { backStackEntry ->
        val chatRoomData = backStackEntry.toRoute<ChatNav.ChatScreen>().chatRoomData

        ChatScreen(
            session = session,
            chatRoomData = chatRoomData,
            onNavBack = {
                navController.popBackStack()
            }
        )
    }

    composable<ChatNav.ChatRoomScreen> {
        ChatRoomScreen(
            session = session,
        ) { chatRoom ->
            val chatRoomData = Json.encodeToString(chatRoom)
            navController.navigate(ChatNav.ChatScreen(chatRoomData))
        }

    }
}

