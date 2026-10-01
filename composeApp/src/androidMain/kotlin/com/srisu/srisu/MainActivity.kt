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
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(com.srisu.srisu.navigation.PlatformEntry::openUrl)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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