package com.srisu.srisu.app

import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.core.identity.PartnerNames
import com.srisu.srisu.core.identity.LocalPartnerNames
import com.srisu.srisu.core.identity.PartnerNameSnapshot
import org.koin.compose.koinInject

@Composable
fun App(darkTheme: Boolean = isSystemInDarkTheme()) {
    ApplicationDependencies {
        val notifications: com.srisu.srisu.features.notifications.NotificationRepository = koinInject()
        val sessions: SessionCoordinator = koinInject()
        val lifetime: ApplicationLifetime = koinInject()
        val planReminders: com.srisu.srisu.features.plans.data.PlanReminders = koinInject()
        val partnerNames: PartnerNames = koinInject()
        val nameSnapshot by partnerNames.state.collectAsState()
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
        val appearancePrefs: com.srisu.srisu.features.settings.AppearancePreferences = koinInject()
        val appearance by appearancePrefs.appearance.collectAsState(com.srisu.srisu.features.settings.AppAppearance.SYSTEM)
        val effectiveDark = when (appearance) {
            com.srisu.srisu.features.settings.AppAppearance.SYSTEM -> darkTheme
            com.srisu.srisu.features.settings.AppAppearance.LIGHT -> false
            com.srisu.srisu.features.settings.AppAppearance.DARK -> true
        }
        com.srisu.srisu.features.settings.ApplyPlatformAppearance(appearance, effectiveDark)
        SriSuTheme(darkTheme = effectiveDark) {
            key(session) {
                // The entire navigation/view-model store is owned by this account.
                val accountOwner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                DisposableEffect(accountOwner) { onDispose { accountOwner.viewModelStore.clear() } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides accountOwner,
                    LocalPartnerNames provides (nameSnapshot.takeIf { it.accountId == session.accountId } ?: PartnerNameSnapshot())) {
                    BaseNavigation(sessionStorage = sessions)
                }
            }
        }
    }
}
