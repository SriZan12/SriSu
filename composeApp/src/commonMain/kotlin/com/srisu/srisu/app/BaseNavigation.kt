package com.srisu.srisu.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.auth.presentation.screen.introduction.*
import com.srisu.srisu.navigation.*
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.navigation.navhost.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import srisu.composeapp.generated.resources.*

/** Only application flow assembly lives here. Session and membership services own resolution. */
@Composable
fun BaseNavigation(sessionStorage: SessionStorage) {
    val startup: StartupCoordinator = koinInject()
    val access by startup.state.collectAsState()
    val sessions = sessionStorage as SessionCoordinator
    val inbox = PlatformEntry.inbox
    LaunchedEffect(sessions.stamp()) { inbox.reconcile(sessions.stamp().accountId, sessions.stamp().generation) }
    val available = (access as? StartupState.Available)?.takeIf { it.stamp == sessions.stamp() }
    if (available == null) {
        StartupPresentation((access as? StartupState.Recovery)?.message, startup::retry)
        return
    }
    when (available.destination) {
        AccessDestination.ONBOARDING -> { OnboardingScreen(startup::showSpace, startup::enterGuest, startup::beginAuthentication); return }
        AccessDestination.SPACE -> { SpaceScreen(startup::showWelcome, startup::beginAuthentication); return }
        AccessDestination.GUEST -> { GuestScreen(startup::beginAuthentication); return }
        else -> Unit
    }
    val protected = available.destination == AccessDestination.MAIN
    val membership: CoupleAccessCoordinator = koinInject()
    val memberState by membership.state.collectAsState()
    val member = (memberState as? CoupleAccess.Ready)?.takeIf { it.stamp == available.stamp }
    if (protected && member == null) {
        StartupPresentation((memberState as? CoupleAccess.Recovery)?.message, membership::retry)
        return
    }
    val scopeKey = "navigation-v2:${available.stamp}:$protected:${member?.coupleId}:${member?.members}"
    key(scopeKey) {
        // Changing access destroys both active and saved tab entry ViewModel stores.
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            // Old pre-migration JSON/dating routes cannot enter this versioned state scope.
            rememberSaveableStateHolder().SaveableStateProvider(scopeKey) {
                NavigationShell(available, member, startup::leaveAuthentication, membership::retry)
            }
        }
    }
}

@Composable
private fun NavigationShell(access: StartupState.Available, member: CoupleAccess.Ready?, onLeaveAuth: () -> Unit, onRetry: () -> Unit) {
    val controller = rememberNavController()
    val navigator = remember(controller) { AppNavigator(controller) }
    val protected = access.destination == AccessDestination.MAIN
    val pending by PlatformEntry.inbox.pending.collectAsState()
    LaunchedEffect(pending, protected, access.session?.id) {
        val account = access.session?.id
        if (protected && account != null) pending?.let { PlatformEntry.inbox.consume(it, account)?.let(navigator::open) }
    }
    val start: Route = when (access.destination) {
        AccessDestination.MAIN -> HomeNavigation.Home
        AccessDestination.PHONE -> AuthNavigation.PhoneNumberScreen
        else -> AuthNavigation.ProfileSetUp
    }
    val entry by controller.currentBackStackEntryAsState()
    val tab = BottomDestination.fromDestination(entry?.destination)
    Scaffold(
        modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (protected && member?.warning != null) TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.nav_membership_retry))
            }
        },
        bottomBar = { AppBottomBar({ navigator.switchTab(it.route) }, tab, tab != null) }
    ) { padding ->
        AppNavHost(controller, start, access.session, protected, onLeaveAuth,
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
    }
}
