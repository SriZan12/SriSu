package com.srisu.srisu.navigation.navhost

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.coupleProfileGraph

/** Assembly only; each feature registers and owns its entry/flow scopes. */
@Composable fun AppNavHost(
    navController: NavHostController,
    startDestination: Route,
    session: Session?,
    protectedAccess: Boolean = false,
    onLeaveAuthentication: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(navController, startDestination = if (protectedAccess) startDestination else AuthNavigation.Flow, modifier = modifier) {
        if (protectedAccess) {
            homeGraph(navController)
            coupleProfileGraph(navController)
            profileGraph(navController)
            chatGraph(navController, session)
        } else authGraph(navController, startDestination, onLeaveAuthentication)
    }
}
