package com.srisu.srisu.features.moments.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.moments.presentation.*
import kotlinx.serialization.Serializable

@Serializable data class MomentComposerDestination(val coupleId: Long): Route
@Serializable data class MomentViewerDestination(val momentId: Long?=null,val coupleId: Long?=null,val creatorId: Long?=null): Route
@Serializable data class MomentNoteDestination(val noteId: Long): Route

fun NavGraphBuilder.momentGraph(controller: NavController) {
    composable<MomentNoteDestination> { entry ->
        val route=entry.toRoute<MomentNoteDestination>(); val nav=AppNavigator(controller,entry)
        if(route.noteId<=0) com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else MomentNoteScreen(route.noteId,nav::back)
    }
    composable<MomentComposerDestination> { entry ->
        val route=entry.toRoute<MomentComposerDestination>();val nav=AppNavigator(controller,entry)
        if(route.coupleId<=0) com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else MomentComposerScreen(route.coupleId,nav::back,{nav.replace(MomentViewerDestination(it,route.coupleId))})
    }
    composable<MomentViewerDestination> {entry->
        val route=entry.toRoute<MomentViewerDestination>();val nav=AppNavigator(controller,entry)
        if((route.momentId==null && route.coupleId==null) || listOfNotNull(route.momentId,route.coupleId,route.creatorId).any {it<=0}) com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else MomentViewerScreen(route.momentId,route.coupleId,route.creatorId,nav::back,
            {ref->nav.open(ChatNav.CoupleConversation(origin="moment",momentId=ref.id,momentCoupleId=ref.couple))})
    }
}
