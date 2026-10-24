package com.srisu.srisu.navigation.navhost

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.srisu.srisu.core.session.Session
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.features.coupleprofile.navigation.coupleProfileGraph
import com.srisu.srisu.features.moments.navigation.momentGraph
import com.srisu.srisu.features.sparks.navigation.sparkGraph
import com.srisu.srisu.features.challenges.navigation.challengeGraph

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
            momentGraph(navController)
            sparkGraph(navController)
            challengeGraph(navController)
            coupleProfileGraph(navController)
            profileGraph(navController)
            chatGraph(navController)
        } else authGraph(navController, startDestination, onLeaveAuthentication)
    }
}
