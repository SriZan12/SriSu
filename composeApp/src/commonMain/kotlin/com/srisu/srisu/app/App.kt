package com.srisu.srisu.app

import androidx.compose.runtime.Composable
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.core.session.SessionStorage
import com.srisu.srisu.theme.AppTheme
import com.srisu.srisu.utils.Constants.Auth.FIRST_INSTALL_FLAG
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI
import androidx.compose.runtime.LaunchedEffect

@OptIn(KoinExperimentalAPI::class)
@Composable
fun App(
    darkTheme: Boolean,
    dynamicColor: Boolean,
) {
    KoinMultiplatformApplication(
        config = createKoinConfiguration()
    ) {
        val sessionStorage: SessionStorage = koinInject()

        AppTheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor
        ) {
            LaunchedEffect(Unit) {
                sessionStorage.clearOnReinstall(key = FIRST_INSTALL_FLAG)
            }

            AppRoot(sessionStorage = sessionStorage)
        }
    }
}