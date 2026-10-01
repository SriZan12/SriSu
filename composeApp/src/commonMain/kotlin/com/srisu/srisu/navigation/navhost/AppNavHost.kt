package com.srisu.srisu.navigation.navhost

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.features.chat.presentation.chat.vm.ChatViewModel
import com.srisu.srisu.features.chat.presentation.findpartner.vm.FindPartnerViewModel
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.features.home.suggestions.presentation.vm.SuggestionViewModel
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.authGraph
import com.srisu.srisu.navigation.graph.chatGraph
import com.srisu.srisu.navigation.graph.connectionGraph
import com.srisu.srisu.navigation.graph.homeGraph
import com.srisu.srisu.navigation.graph.profileGraph
import com.srisu.srisu.navigation.graph.suggestionsGraph
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable fun AppNavHost(
    navController: NavHostController,
    startDestination: Route,
    session: Session?,
    protectedAccess: Boolean = false,
    onLeaveAuthentication: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SharedTransitionLayout {
        val suggestionViewModel = if (protectedAccess) koinViewModel<SuggestionViewModel>() else null
        val authViewModel = koinViewModel<AuthViewModel>()
        val chatViewModel = if (protectedAccess) koinViewModel<ChatViewModel>() else null
        val findPartnerViewModel = if (protectedAccess) koinViewModel<FindPartnerViewModel>() else null

        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination,
            popEnterTransition = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = LinearEasing
                    )
                )
            },
            popExitTransition = {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = LinearEasing
                    )
                )
            }
        ) {
            if (!protectedAccess) {
                authGraph(navController = navController, authViewModel = authViewModel, onLeaveAuthentication = onLeaveAuthentication)
            } else {

            homeGraph(navController = navController, onFindPartner = { navController.navigate(com.srisu.srisu.navigation.graph.ChatNav.FindPartnerScreen) { launchSingleTop = true } })

            suggestionsGraph(
                navController = navController,
                suggestionViewModel = requireNotNull(suggestionViewModel),
                sharedTransitionScope = this@SharedTransitionLayout
            )

            connectionGraph(navController = navController)

            profileGraph(navController = navController)

            chatGraph(
                session = session,
                chatViewModel = requireNotNull(chatViewModel),
                findPartnerViewModel = requireNotNull(findPartnerViewModel),
                navController = navController
            )
            }
        }
    }
}