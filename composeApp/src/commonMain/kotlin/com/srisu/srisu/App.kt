package com.srisu.srisu

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.navigation.AuthNavigation
import com.srisu.srisu.navigation.ChatNav
import com.srisu.srisu.navigation.ConnectionNav
import com.srisu.srisu.navigation.HomeNavigation
import com.srisu.srisu.navigation.ProfileNav
import com.srisu.srisu.navigation.Route
import com.srisu.srisu.navigation.SuggestionsNav
import com.srisu.srisu.navigation.authGraph
import com.srisu.srisu.navigation.chatGraph
import com.srisu.srisu.navigation.connectionGraph
import com.srisu.srisu.navigation.homeGraph
import com.srisu.srisu.navigation.profileGraph
import com.srisu.srisu.navigation.suggestionsGraph
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.theme.AppTheme
import com.srisu.srisu.utils.Constants.Auth.FIRST_INSTALL_FLAG
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
    val startDestination = startDestination(session = session)

    val bottomNavTabClasses = listOf(
        HomeNavigation.Home::class,
        SuggestionsNav.Suggestions::class,
        ConnectionNav.Connection::class,
        ProfileNav.EditProfile::class
    )


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Use hasRoute for visibility check (handles params/nesting)
            val shouldShowBottom = bottomNavTabClasses.any { tabClass ->
                currentDestination?.hasRoute(tabClass) == true
            }

            AppLogger.log("CURRENT ROUTE: ${currentDestination?.route}")
            AppLogger.log("SHOULD SHOW BOTTOM: $shouldShowBottom")

            BottomNavigation(
                navController = navController,
                show = shouldShowBottom
            )

        }
    ) { innerPadding ->
        SharedTransitionLayout {
            val navController = navController
            val suggestionViewModel = koinViewModel<SuggestionViewModel>()

            NavHost(
                modifier = Modifier,
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
                )

                suggestionsGraph(
                    navController = navController,
                    suggestionViewModel = suggestionViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout
                )

                connectionGraph(
                    navController = navController
                )

                profileGraph(
                    navController = navController
                )

                chatGraph(
                    navController = navController
                )
            }
        }

    }
}


private fun startDestination(session: Session?): Route {
    return when {
        session?.isPhoneVerified == true && session.isProfileComplete == true -> ChatNav.FindPartnerScreen
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
    CONNECTION(Icons.Filled.Groups, "Matches"),
    PROFILE(Icons.Filled.Person, "Profile")
}

@Composable
private fun BottomNavigation(
    navController: NavHostController,
    show: Boolean
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    if (show) {
        NavigationBar(
            modifier = Modifier,
            windowInsets = NavigationBarDefaults.windowInsets,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp
        ) {
            BottomNavDestination.entries.forEach { destination ->
                // Use hasRoute per destination (no toRoute or full sealed deserialization)
                val selected = when (destination) {
                    BottomNavDestination.HOME -> currentDestination?.hasRoute<HomeNavigation.Home>() == true
                    BottomNavDestination.SUGGESTIONS -> currentDestination?.hasRoute<SuggestionsNav.Suggestions>() == true
                    BottomNavDestination.CONNECTION -> currentDestination?.hasRoute<ConnectionNav.Connection>() == true
                    BottomNavDestination.PROFILE -> currentDestination?.hasRoute<ProfileNav.EditProfile>() == true
                }

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

                            BottomNavDestination.SUGGESTIONS -> navController.navigate(
                                SuggestionsNav.Suggestions
                            ) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }

                            BottomNavDestination.CONNECTION -> navController.navigate(ConnectionNav.Connection) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }

                            BottomNavDestination.PROFILE -> {
                                navController.navigate(
                                    ProfileNav.EditProfile
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
}

@Composable
fun GlowingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.2f else 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (selected) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(36.dp)
                    .blur(8.dp)
                    .alpha(glowAlpha),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

