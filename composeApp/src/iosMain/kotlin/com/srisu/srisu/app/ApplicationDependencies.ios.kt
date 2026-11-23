package com.srisu.srisu.app

import androidx.compose.runtime.Composable
import com.srisu.srisu.di.createKoinConfiguration
import org.koin.compose.KoinMultiplatformApplication
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
actual fun ApplicationDependencies(content: @Composable () -> Unit) {
    KoinMultiplatformApplication(config = createKoinConfiguration(), content = content)
}
