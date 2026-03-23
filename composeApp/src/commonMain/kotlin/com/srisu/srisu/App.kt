package com.srisu.srisu

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColor
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@OptIn(KoinExperimentalAPI::class)
@Composable
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
    } catch (illegalArgumentException: IllegalArgumentException) {
        AppLogger.log("Exception = ${illegalArgumentException.message}")
    } catch (serializationException: SerializationException) {
        AppLogger.log("Exception = ${serializationException.message}")
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
        ConnectionNav.LoveRequestScreen::class,
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

            BottomNavigation(
                navController = navController,
                show = shouldShowBottom
            )

        }
    ) { _ ->
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

                homeGraph()

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
                    session = session,
                    navController = navController
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

    EXPLORE(Icons.Filled.Search, "Explore"), // For browsing opposite profiles

    CONNECTIONS(Icons.Filled.Favorite, "Crushes"), // Sent/received crush requests

    MATCHES(Icons.Default.FavoriteBorder, "Matches"),

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
            tonalElevation = 0.dp,
        ) {
            BottomNavDestination.entries.forEach { destination ->
                // Use hasRoute per destination (no toRoute or full sealed deserialization)
                val selected = when (destination) {
                    BottomNavDestination.HOME -> currentDestination?.hasRoute<HomeNavigation.Home>() == true
                    BottomNavDestination.EXPLORE -> currentDestination?.hasRoute<SuggestionsNav.Suggestions>() == true
                    BottomNavDestination.CONNECTIONS -> currentDestination?.hasRoute<ConnectionNav.Connection>() == true
                    BottomNavDestination.MATCHES -> currentDestination?.hasRoute<ConnectionNav.LoveRequestScreen>() == true
                    BottomNavDestination.PROFILE -> currentDestination?.hasRoute<ProfileNav.EditProfile>() == true
                }

                NavigationBarItem(
                    selected = selected,
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                    onClick = {
                        when (destination) {
                            BottomNavDestination.HOME -> navController.navigate(HomeNavigation.Home) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }

                            BottomNavDestination.EXPLORE -> navController.navigate(
                                SuggestionsNav.Suggestions
                            ) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }

                            BottomNavDestination.CONNECTIONS -> navController.navigate(ConnectionNav.Connection) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }

                            BottomNavDestination.MATCHES -> navController.navigate(ConnectionNav.LoveRequestScreen) {
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
//                        MorphBackgroundIcon(
                        GlassIcon(
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

    // Glow animation (only meaningful when selected)
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = ""
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {

        // 🔥 Glow layer (behind icon)
        if (selected) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = glowAlpha
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .blur(16.dp) // THIS is the key for glow ✨
            )
        }

        // Icon
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PulseRingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = tween(250),
        label = "icon_scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            )
        }

        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

@Composable
fun GradientRingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_transition")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(250),
        label = "icon_scale"
    )

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        rotationZ = rotation
                    }
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary,
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

@Composable
fun ShimmerIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_transition")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val iconColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        clip = true
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                start = Offset(shimmerOffset, 0f),
                                end = Offset(shimmerOffset + 40f, size.height)
                            )
                        )
                    }
            )
        }
    }
}

@Composable
fun MorphBackgroundIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val bgScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 0.9f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "bg_scale"
    )

    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.18f else 0f,
        animationSpec = tween(350),
        label = "bg_alpha"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(300),
        label = "icon_scale"
    )

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = bgScale
                    scaleY = bgScale
                    alpha = bgAlpha
                }
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )

        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

@Composable
fun ShadowIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (selected) 10.dp else 0.dp,
        animationSpec = tween(300),
        label = "shadow_elevation"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(250),
        label = "icon_scale"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(
                elevation = elevation,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

@Composable
fun BreathingColorIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_transition")

    val breathingColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary,
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_color"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.07f else 1f,
        animationSpec = tween(250),
        label = "icon_scale"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = if (selected) breathingColor else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
    )
}

@Composable
fun GlassIcon(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.16f else 0f,
        animationSpec = tween(300),
        label = "bg_alpha"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 0.28f else 0f,
        animationSpec = tween(300),
        label = "border_alpha"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(250),
        label = "icon_scale"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = bgAlpha)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}