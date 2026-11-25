package com.srisu.srisu.features.settings

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable internal actual fun ApplyPlatformAppearance(appearance: AppAppearance, dark: Boolean) {
    val view=LocalView.current
    if(view.isInEditMode) return
    val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull() ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window,view).apply {
            isAppearanceLightStatusBars=!dark
            isAppearanceLightNavigationBars=!dark
        }
    }
}
