package com.srisu.srisu.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.features.auth.domain.AccessDestination
import com.srisu.srisu.features.auth.presentation.screen.introduction.OnboardingScreen
import com.srisu.srisu.features.auth.presentation.screen.introduction.SpaceScreen
import com.srisu.srisu.features.auth.presentation.screen.introduction.GuestScreen
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
    val startup: com.srisu.srisu.features.auth.domain.StartupCoordinator = org.koin.compose.koinInject()
    val access by startup.state.collectAsState()
    val sessions = sessionStorage as com.srisu.srisu.core.session.SessionCoordinator
    val available = access as? com.srisu.srisu.features.auth.domain.StartupState.Available
    if (available == null || available.stamp != sessions.stamp()) {
        StartupPresentation((access as? com.srisu.srisu.features.auth.domain.StartupState.Recovery)?.message, startup::retry)
        return
    }
    when (available.destination) {
        AccessDestination.ONBOARDING -> {
            OnboardingScreen(startup::showSpace, startup::enterGuest, startup::beginAuthentication)
            return
        }
        AccessDestination.SPACE -> {
            SpaceScreen(startup::showWelcome, startup::beginAuthentication)
            return
        }
        AccessDestination.GUEST -> {
            GuestScreen(startup::beginAuthentication)
            return
        }
        else -> Unit
    }
    val protected = available.destination == com.srisu.srisu.features.auth.domain.AccessDestination.MAIN
    androidx.compose.runtime.key(protected) {
    val navController = rememberNavController()
    val session = available.session
    val startDestination: Route = when (available.destination) {
        com.srisu.srisu.features.auth.domain.AccessDestination.MAIN -> HomeNavigation.Home
        com.srisu.srisu.features.auth.domain.AccessDestination.PHONE -> AuthNavigation.PhoneNumberScreen
        else -> AuthNavigation.ProfileSetUp
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
            protectedAccess = protected,
            onLeaveAuthentication = startup::leaveAuthentication,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}
}
