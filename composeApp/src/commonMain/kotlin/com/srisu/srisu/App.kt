package com.srisu.srisu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.navigation.AuthNavigation
import com.srisu.srisu.navigation.HomeNavigation
import com.srisu.srisu.navigation.Route
import com.srisu.srisu.navigation.authGraph
import com.srisu.srisu.navigation.homeGraph
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.Constants.SESSION_KEY
import com.srisu.srisu.theme.AppTheme
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.koinInject

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
        }
    }
}

private fun checkSession(session: SessionStorage): Session? {
    val sessionJson = session.getSession(sessionKey = SESSION_KEY)
    var sessionData: Session? = null

    try {
        sessionData = sessionJson?.let { Json.decodeFromString<Session>(it) }
    } catch (exception: Exception) {
        AppLogger.log("SESSION SERIALIZATION EXCEPTION")
    }

    return sessionData
}

@Composable
private fun NavHostController(session: Session?) {
    val navController = rememberNavController()
    val startDestination = startDestination(session = session)

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
        //Nav graphs
        authGraph(navController = navController)
        homeGraph(navController = navController)
    }
}

private fun startDestination(session: Session?): Route {
    return when {
        session?.isPhoneVerified == true && session.isProfileComplete == true -> HomeNavigation.Suggestions

        else -> AuthNavigation.Auth
    }
}