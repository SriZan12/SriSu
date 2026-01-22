package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.chat.chatroom.screen.ChatRoomScreen
import com.srisu.srisu.features.chat.chatroom.screen.ChatScreen
import com.srisu.srisu.features.chat.couple.findpartner.FindYourPartnerScreen
import com.srisu.srisu.session.Session
import kotlinx.serialization.Serializable

sealed class ChatNav : Route {

    @Serializable
    data object FindPartnerScreen : ChatNav()

    @Serializable
    data object ChatScreen : ChatNav()

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

    composable<ChatNav.ChatScreen> {
        ChatScreen(
            session = session,
            navController = navController
        )
    }

    composable<ChatNav.ChatRoomScreen> {
        ChatRoomScreen(
            session = session,
        )
    }


}