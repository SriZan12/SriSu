package com.srisu.srisu.features.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.srisu.srisu.navigation.graph.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Actual screen state, including destinations selected inside a feature's navigation. */
internal object NotificationVisibility {
    private val screens = linkedMapOf<Any, Route?>()
    private val mutable = MutableStateFlow<Route?>(null)
    val current = mutable.asStateFlow()

    fun show(owner: Any, route: Route?) {
        screens.remove(owner)
        screens[owner] = route
        mutable.value = route
    }

    fun hide(owner: Any) {
        screens.remove(owner)
        mutable.value = screens.values.lastOrNull()
    }
}

@Composable
fun ReportNotificationScreen(route: Route?) {
    val owner = remember { Any() }
    DisposableEffect(route) {
        NotificationVisibility.show(owner, route)
        onDispose { NotificationVisibility.hide(owner) }
    }
}
