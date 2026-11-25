package com.srisu.srisu.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.interop.LocalUIViewController
import platform.UIKit.UIUserInterfaceStyle

@Composable internal actual fun ApplyPlatformAppearance(appearance: AppAppearance, dark: Boolean) {
    val controller=LocalUIViewController.current
    SideEffect {
        controller.overrideUserInterfaceStyle=when(appearance) {
            AppAppearance.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            AppAppearance.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            AppAppearance.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        controller.setNeedsStatusBarAppearanceUpdate()
    }
}
