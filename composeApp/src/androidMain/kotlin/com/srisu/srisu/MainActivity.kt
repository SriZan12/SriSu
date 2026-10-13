package com.srisu.srisu

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.srisu.srisu.app.App

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        com.srisu.srisu.notifications.AndroidNotifications.instance?.refresh()
    }
    override fun onResume() {
        super.onResume()
        com.srisu.srisu.notifications.AndroidNotifications.instance?.refresh()
    }
    override fun onDestroy() {
        com.srisu.srisu.notifications.AndroidNotifications.instance?.requestRuntimePermission = null
        super.onDestroy()
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.srisu.srisu.notifications.AndroidNotifications.receive(intent)
        intent.dataString?.let(com.srisu.srisu.navigation.PlatformEntry::openUrl)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.srisu.srisu.notifications.AndroidNotifications.instance?.requestRuntimePermission = { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        if(savedInstanceState == null) com.srisu.srisu.notifications.AndroidNotifications.receive(intent)

        if (savedInstanceState == null) intent?.dataString?.let(com.srisu.srisu.navigation.PlatformEntry::openUrl)
        setContent {
            App(
                darkTheme = isSystemInDarkTheme(),

            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppAndroidPreview() {
    App(
        darkTheme = isSystemInDarkTheme(),

    )
}