package com.srisu.srisu.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.srisu.srisu.features.chat.findpartner.FindYourPartnerScreen
import kotlinx.serialization.Serializable

sealed class ChatNav: Route {

    @Serializable
    data object FindPartnerScreen: ChatNav()

    @Serializable
    data object ChatScreen: ChatNav()


}
fun NavGraphBuilder.chatGraph(
    navController: NavController
){
    composable<ChatNav.FindPartnerScreen>(){
        FindYourPartnerScreen()
    }
}