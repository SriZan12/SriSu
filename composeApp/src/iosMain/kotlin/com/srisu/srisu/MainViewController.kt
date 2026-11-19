package com.srisu.srisu

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import com.srisu.srisu.app.App

fun MainViewController() = ComposeUIViewController(configure = {
    // Screens own keyboard insets and scrolling. UIKit's additional whole-scene
    // pan can move the fixed header into the status bar at accessibility sizes.
    onFocusBehavior = OnFocusBehavior.DoNothing
}) { App() }
