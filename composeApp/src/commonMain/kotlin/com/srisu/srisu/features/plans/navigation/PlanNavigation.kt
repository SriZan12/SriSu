package com.srisu.srisu.features.plans.navigation
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.srisu.srisu.navigation.AppNavigator
import com.srisu.srisu.navigation.graph.Route
import com.srisu.srisu.navigation.graph.ChatNav
import com.srisu.srisu.features.plans.presentation.PlanScreen
import com.srisu.srisu.features.moments.navigation.MomentComposerDestination
import kotlinx.serialization.Serializable
@Serializable data class PlansDestination(val id:Long?=null,val fromChat:Boolean=false,val showInvitations:Boolean=false,val create:Boolean=false,val ideaId:String?=null,val edit:Boolean=false):Route
@Serializable data class DateIdeaDestination(val id: String): Route
fun NavGraphBuilder.planGraph(controller:NavController) {
    composable<DateIdeaDestination> { entry ->
        val route=entry.toRoute<DateIdeaDestination>(); val nav=AppNavigator(controller,entry)
        if(!com.srisu.srisu.features.notifications.validNotificationId(route.id)) com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else com.srisu.srisu.features.plans.presentation.DateIdeaScreen(route.id,nav::back,
            { nav.open(com.srisu.srisu.navigation.graph.HomeNavigation.Home) },{ nav.open(PlansDestination(ideaId=it)) })
    }
    composable<PlansDestination> {entry->
        val route=entry.toRoute<PlansDestination>();val nav=AppNavigator(controller,entry)
        if((route.id!=null && route.id<=0) || (route.ideaId!=null && !route.ideaId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))) com.srisu.srisu.navigation.NavigationUnavailable(nav::back)
        else PlanScreen(route.id,nav::back,{nav.conversation(route.fromChat, ChatNav.CoupleConversation(origin="plan"))},{nav.open(MomentComposerDestination(it))},initialInvitations=route.showInvitations,initialCreate=route.create,initialIdea=route.ideaId,initialEdit=route.edit)
    }
}
