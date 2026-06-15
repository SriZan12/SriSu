package com.srisu.srisu.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.navigation.graph.AuthNavigation
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.navigation.graph.HomeNavigation
import com.srisu.srisu.navigation.navhost.AppBottomBar
import com.srisu.srisu.navigation.navhost.AppNavHost
import com.srisu.srisu.navigation.navhost.BottomDestination
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.serialization.json.Json

@Composable
 fun AppRoot(
    sessionStorage: SessionStorage
) {
    val navController = rememberNavController()

    val session = remember(sessionStorage) {
        readSessionSafely(sessionStorage)
    }

    val startDestination = remember(session) {
        resolveStartDestination(session)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentBottomDestination = remember(currentDestination) {
        BottomDestination.fromDestination(currentDestination)
    }

    val shouldShowBottomBar = currentBottomDestination != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(
                navController = navController,
                currentDestination = currentBottomDestination,
                visible = shouldShowBottomBar
            )
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            session = session,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}

private fun readSessionSafely(sessionStorage: SessionStorage): Session? {
    return try {
        sessionStorage
            .getSession(sessionKey = SESSION_KEY)
            ?.let { Json.decodeFromString<Session>(it) }
    } catch (exception: Exception) {
        AppLogger.log("Failed to decode session: ${exception.message}")
        null
    }
}

private fun resolveStartDestination(session: Session?): Route {
    return when {
        session?.isPhoneVerified == true && session.isProfileComplete == true -> {
            ChatNav.ChatScreen
        }

        else -> AuthNavigation.Auth
    }
}