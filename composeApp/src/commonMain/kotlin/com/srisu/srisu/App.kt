package com.srisu.srisu

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.navigation.AuthNavigation
import com.srisu.srisu.navigation.HomeNavigation
import com.srisu.srisu.navigation.Route
import com.srisu.srisu.navigation.authGraph
import com.srisu.srisu.navigation.homeGraph
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.theme.AppTheme
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
@Preview
fun App(
    darkTheme: Boolean,
    dynamicColor: Boolean,
) {

    KoinMultiplatformApplication(
        config = createKoinConfiguration()
    ) {
        val session: SessionStorage = koinInject()

        AppTheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor
        ) {
            NavHostController(session = checkSession(session = session))
            InitCoilImageLoader()
        }
    }
}

private fun checkSession(session: SessionStorage): Session? {
    val sessionJson = session.getSession(sessionKey = SESSION_KEY)
    var sessionData: Session? = null

    try {
        sessionData = sessionJson?.let { Json.decodeFromString<Session>(it) }
    } catch (_: Exception) {
        AppLogger.log("SESSION SERIALIZATION EXCEPTION")
    }

    return sessionData
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NavHostController(session: Session?) {
    SharedTransitionLayout {
        val navController = rememberNavController()
        val startDestination = startDestination(session = session)
        val suggestionViewModel = koinViewModel<SuggestionViewModel>()

        NavHost(
            navController = navController,
            startDestination = startDestination,
            popEnterTransition = {
                fadeIn(
                    animationSpec = tween(
                        500, easing = LinearEasing
                    )
                )
            },
            popExitTransition = {
                fadeOut(
                    animationSpec = tween(
                        500, easing = LinearEasing
                    )
                )
            }
        ) {
            // Nav graphs
            authGraph(navController = navController)

            homeGraph(
                navController = navController,
                suggestionViewModel = suggestionViewModel,
                sharedTransitionScope = this@SharedTransitionLayout,

                )
        }
    }
}


private fun startDestination(session: Session?): Route {
    return when {
        session?.isPhoneVerified == true && session.isProfileComplete == true -> HomeNavigation.Connection
        else -> HomeNavigation.Connection
//        else -> HomeNavigation.EditProfile
    }
}

@Composable
private fun InitCoilImageLoader() {

    setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder() // platformContext needed for max size percent
                    .maxSizePercent(platformContext, 0.25)
                    .build()
            }
            .crossfade(true)
            .build()
    }

}