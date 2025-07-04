package com.srisu.srisu

import SuggestionProfileScreen
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.srisu.srisu.core.logger.AppLogger
import com.srisu.srisu.di.createKoinConfiguration
import com.srisu.srisu.features.home.HomeScreen
import com.srisu.srisu.features.profile.screen.ProfileScreen
import com.srisu.srisu.features.suggestions.screens.FilterSuggestionScreen
import com.srisu.srisu.features.suggestions.screens.SuggestionScreen
import com.srisu.srisu.features.suggestions.vm.SuggestionViewModel
import com.srisu.srisu.navigation.AuthNavigation
import com.srisu.srisu.navigation.HomeNavigation
import com.srisu.srisu.navigation.Route
import com.srisu.srisu.navigation.applyFilter
import com.srisu.srisu.navigation.authGraph
import com.srisu.srisu.navigation.clearFilter
import com.srisu.srisu.navigation.clearFilterFlags
import com.srisu.srisu.navigation.homeGraph
import com.srisu.srisu.session.Session
import com.srisu.srisu.session.SessionStorage
import com.srisu.srisu.utils.Constants.SESSION_KEY
import com.srisu.srisu.theme.AppTheme
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinMultiplatformApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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

private const val FILTER_APPLIED = "filter_applied"
private const val FILTER_CLEARED = "filter_cleared"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NavHostController(session: Session?) {


    SharedTransitionScope {
        val navController = rememberNavController()
        val startDestination = startDestination(session = session)
        val suggestionViewModel = koinViewModel<SuggestionViewModel>()
        val sharedTransitionScope: SharedTransitionScope = this@SharedTransitionScope

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
//            homeGraph(
//                navController = navController,
//                suggestionViewModel = suggestionViewModel,
//                sharedTransitionScope = this@SharedTransitionScope,
//            )

            composable<HomeNavigation.Home> { _ ->
                HomeScreen()
            }

            composable<HomeNavigation.Suggestions> {

                val navBackStackEntry = remember { navController.currentBackStackEntry }
                val savedStateHandle = navBackStackEntry?.savedStateHandle

                val filterApplied = savedStateHandle?.getStateFlow(FILTER_APPLIED, false)?.value ?: false
                val filterCleared = savedStateHandle?.getStateFlow(FILTER_CLEARED, false)?.value ?: false


                SuggestionScreen(
                    suggestionViewModel = suggestionViewModel,
                    filterApplied = filterApplied,
                    filterCleared = filterCleared,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = this@composable,
                    navigateFilterScreen = { navController.navigate(HomeNavigation.Filter) },
                    navigateProfileScreen = { suggestionProfileData ->
                        navController.navigate(HomeNavigation.SuggestionProfile(suggestionProfileData = suggestionProfileData))
                    },
                )
            }

            composable<HomeNavigation.SuggestionProfile> { backStackEntry ->
                val userProfileData = backStackEntry.arguments?.getString("suggestionProfileData")
                clearFilterFlags(navController = navController)

                SuggestionProfileScreen(
                    suggestionViewModel = suggestionViewModel,
                    userProfileData = userProfileData,
                )

            }


            composable<HomeNavigation.Filter> { backStackEntry ->

                FilterSuggestionScreen(
                    suggestionViewModel = suggestionViewModel,
                    onNavigateBack = {
                        clearFilterFlags(navController = navController)
                        navController.popBackStack()
                    },
                    onClearFilter = {
                        clearFilter(navController = navController)
                        navController.popBackStack()
                    },
                    onFilterApplied = {
                        applyFilter(navController = navController)
                        navController.popBackStack()
                    }
                )
            }



            composable<HomeNavigation.Profile> { backStackEntry ->
                val userProfileData = backStackEntry.arguments?.getString("userProfileData")
                ProfileScreen(userProfileData = userProfileData)
            }
        }
    }
}

private fun startDestination(session: Session?): Route {
    return when {
        session?.isPhoneVerified == true && session.isProfileComplete == true -> HomeNavigation.Suggestions

        else -> AuthNavigation.Auth
    }
}