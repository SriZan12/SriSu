package com.srisu.srisu.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.chat.chatroom.couple.findpartner.FindYourPartnerScreen
import kotlinx.serialization.Serializable

sealed class ChatNav : Route {

    @Serializable
    data object FindPartnerScreen : ChatNav()

    @Serializable
    data object ChatScreen : ChatNav()


}

fun NavGraphBuilder.chatGraph(
    navController: NavController
) {
    composable<ChatNav.FindPartnerScreen>() {
        FindYourPartnerScreen()
    }


}