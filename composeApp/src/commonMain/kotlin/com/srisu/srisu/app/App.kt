package com.srisu.srisu.app

import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.theme.SriSuTheme
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun App(darkTheme: Boolean = isSystemInDarkTheme()) {
    KoinMultiplatformApplication(config = createKoinConfiguration()) {
        val sessions: SessionCoordinator = koinInject()
        val lifetime: ApplicationLifetime = koinInject()
        val session by sessions.state.collectAsState()
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, lifetime) {
            lifetime.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            val observer = LifecycleEventObserver { _, _ ->
                lifetime.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); lifetime.setForeground(false) }
        }
        SriSuTheme(darkTheme = darkTheme) {
            key(session.accountId) {
                // The entire navigation/view-model store is owned by this account.
                val accountOwner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                DisposableEffect(accountOwner) { onDispose { accountOwner.viewModelStore.clear() } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides accountOwner) {
                    AppRoot(sessionStorage = sessions)
                }
            }
        }
    }
}
