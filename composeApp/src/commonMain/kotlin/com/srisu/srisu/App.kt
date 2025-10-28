package com.srisu.srisu

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.navigation.AuthNavigation
import com.srisu.srisu.navigation.HomeNavigation
import com.srisu.srisu.navigation.Route
import com.srisu.srisu.navigation.SuggestionsNav
import com.srisu.srisu.navigation.authGraph
import com.srisu.srisu.navigation.homeGraph
import com.srisu.srisu.navigation.suggestionsGraph
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.theme.AppTheme
import com.srisu.srisu.utils.Constants.Auth.FIRST_INSTALL_FLAG
import com.srisu.srisu.utils.Constants.Auth.SESSION_KEY
import kotlinx.serialization.encodeToString
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
            session.clearOnReinstall(key = FIRST_INSTALL_FLAG)
            NavHostController(session = checkSession(session = session))
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

    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val currentRoute = navController.currentDestination?.route ?: HomeNavigation.Home
            AppLogger.log("CURRENT ROUTE: $currentRoute")
            BottomNavigation(
                navController = navController,
                session = session
            )


        }
    ) {
        SharedTransitionLayout {
            val navController = navController
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
                authGraph(navController = navController)

                homeGraph(
                    navController = navController,
                    suggestionViewModel = suggestionViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                )

                suggestionsGraph(
                    navController = navController,
                    viewModel = suggestionViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }
        }

    }
}


private fun startDestination(session: Session?): Route {
    return when {
        session?.isPhoneVerified == true && session.isProfileComplete == true -> HomeNavigation.Home
        else -> AuthNavigation.Auth
//        else -> HomeNavigation.EditProfile
    }
}

enum class BottomNavDestination(
    val icon: ImageVector,
    val label: String
) {
    HOME(Icons.Filled.Home, "Home"),
    SUGGESTIONS(Icons.Filled.Favorite, "Suggestions"),
    CONNECTION(Icons.Filled.Group, "Matches"),
    PROFILE(Icons.Filled.Person, "Profile")
}

fun shouldShowBottomNav(route: Any?): Boolean {
    return route is HomeNavigation
}

fun getTabForRoute(route: HomeNavigation?): BottomNavDestination? {
    return when (route) {
        is HomeNavigation.Home -> BottomNavDestination.HOME
        is HomeNavigation.Suggestions -> BottomNavDestination.SUGGESTIONS
        is HomeNavigation.Connection -> BottomNavDestination.CONNECTION
        is HomeNavigation.Profile -> BottomNavDestination.PROFILE
        else -> null
    }
}

@Composable
private fun BottomNavigation(
    navController: NavHostController,
    session: Session?
) {
    NavigationBar(
        windowInsets = NavigationBarDefaults.windowInsets,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        BottomNavDestination.entries.forEach { destination ->
            val currentRoute = navController.currentBackStackEntry?.toRoute<HomeNavigation>()
            val selected = getTabForRoute(currentRoute) == destination
            NavigationBarItem(
                selected = selected,
                onClick = {
                    when (destination) {
                        BottomNavDestination.HOME -> navController.navigate(HomeNavigation.Home) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }

                        BottomNavDestination.SUGGESTIONS -> navController.navigate(HomeNavigation.Suggestions) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }

                        BottomNavDestination.CONNECTION -> navController.navigate(HomeNavigation.Connection) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }

                        BottomNavDestination.PROFILE -> {
                            val userProfileData = Json.encodeToString(session)
                            navController.navigate(
                                HomeNavigation.Profile(
                                    userProfileData
                                )
                            ) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                icon = {
                    GlowingIcon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        selected = selected
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
fun GlowingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val glowRadius by animateDpAsState(
        targetValue = if (selected) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "glowRadius"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow effect layer
        if (selected) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(36.dp) // Slightly larger for glow spread
                    .blur(glowRadius)
                    .alpha(glowAlpha),
                tint = MaterialTheme.colorScheme.primary // Use theme primary for glow color
            )
        } else {
            // Main icon
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
