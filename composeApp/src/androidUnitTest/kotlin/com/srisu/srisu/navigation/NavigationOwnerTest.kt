package com.srisu.srisu.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.navigation.NavDestination.Companion.hasRoute
import com.srisu.srisu.features.coupleprofile.navigation.CoupleProfileDestination
import com.srisu.srisu.features.coupleprofile.navigation.CoupleSection
import com.srisu.srisu.features.coupleprofile.navigation.coupleProfileDestination
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.navigation.navhost.BottomDestination
import com.srisu.srisu.navigation.navhost.selectBottomDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationOwnerTest {
    class Probe : androidx.lifecycle.ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }
    private val probes = mutableMapOf<String, Probe>()
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var controller: NavHostController
    private fun host() {
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = HomeNavigation.Home) {
                composable<HomeNavigation.Home> { }
                composable<HomeNavigation.Explore> { }
                composable<ChatNav.CoupleConversation> { entry ->
                    val probe = androidx.lifecycle.viewmodel.compose.viewModel<Probe>(initializer = { Probe() })
                    SideEffect { probes[entry.id] = probe }
                }
                composable<ProfileNav.EditProfile> { }
            }
        }
        compose.waitForIdle()
    }
    @Test fun poppedEntriesReleaseTheirViewModels() {
        host()
        compose.runOnIdle { AppNavigator(controller).open(ChatNav.CoupleConversation("00000000-0000-4000-8000-000000000001")) }
        compose.waitForIdle()
        lateinit var probe: Probe
        compose.runOnIdle { probe = probes.getValue(controller.currentBackStackEntry!!.id); AppNavigator(controller).back() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(probe.cleared) }
    }
    @Test fun bottomChatAndDiscoveryReturnToTheirOriginWithoutCoveringTheComposer() {
        host()
        compose.runOnIdle {AppNavigator(controller).selectBottomDestination(BottomDestination.PROFILE)}
        compose.waitForIdle()
        val profile=controller.currentBackStackEntry!!.id
        compose.runOnIdle {AppNavigator(controller).selectBottomDestination(BottomDestination.CHAT)}
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(controller.currentDestination!!.hasRoute<ChatNav.CoupleConversation>())
            assertNull(BottomDestination.fromDestination(controller.currentDestination))
            AppNavigator(controller).back()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(profile,controller.currentBackStackEntry!!.id)
            AppNavigator(controller).selectBottomDestination(BottomDestination.HOME)
        }
        compose.waitForIdle()
        compose.runOnIdle {AppNavigator(controller).selectBottomDestination(BottomDestination.EXPLORE)}
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(BottomDestination.EXPLORE, BottomDestination.fromDestination(controller.currentDestination))
            AppNavigator(controller).back()
        }
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(BottomDestination.HOME,BottomDestination.fromDestination(controller.currentDestination))}
    }
    @Test fun librarySavedStateRestoresMinimalArguments() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = HomeNavigation.Home) {
                composable<HomeNavigation.Home> { }
                composable<ChatNav.CoupleConversation> { }
            }
        }
        val route = ChatNav.CoupleConversation("00000000-0000-4000-8000-000000000001")
        compose.runOnIdle { AppNavigator(controller).open(route) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(route, controller.currentBackStackEntry!!.toRoute<ChatNav.CoupleConversation>()) }
    }
    @Test fun coupleProfileEnumRouteRegistersAndRestoresArguments() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = HomeNavigation.Home) {
                composable<HomeNavigation.Home> { }
                coupleProfileDestination { }
            }
        }
        val route = CoupleProfileDestination(42, CoupleSection.PLAN, 9)
        compose.runOnIdle { AppNavigator(controller).open(route) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(route, controller.currentBackStackEntry!!.toRoute<CoupleProfileDestination>())
        }
    }
    @Test fun duplicateAndLateCallbacksCannotStackOrNavigate() {
        host()
        lateinit var origin: AppNavigator
        val room = ChatNav.CoupleConversation("00000000-0000-4000-8000-000000000001")
        compose.runOnIdle {
            origin = AppNavigator(controller, controller.currentBackStackEntry)
            origin.open(room)
            origin.open(room)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(room, controller.currentBackStackEntry!!.toRoute<ChatNav.CoupleConversation>())
            assertFalse(origin.finishFlow(ProfileNav.EditProfile, ChatNav.PartnerFlow))
            origin.open(ProfileNav.EditProfile)
            assertTrue(controller.currentDestination!!.hasRoute<ChatNav.CoupleConversation>())
            AppNavigator(controller).back()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<HomeNavigation.Home>()) }
    }
    @Test fun leavingPartnerFlowForConversationDoesNotRestoreAcceptanceOnBack() {
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = HomeNavigation.Home) {
                composable<HomeNavigation.Home> { }
                navigation<ChatNav.PartnerFlow>(startDestination = ChatNav.YoureConnected) {
                    composable<ChatNav.YoureConnected> { }
                }
                composable<ChatNav.CoupleConversation> { }
            }
        }
        compose.waitForIdle()
        val room = ChatNav.CoupleConversation(
            roomId = "00000000-0000-4000-8000-000000000001",
            origin = "acceptance",
        )
        compose.runOnIdle { AppNavigator(controller).open(ChatNav.PartnerFlow) }
        compose.waitForIdle()
        compose.runOnIdle {
            val acceptanceEntry = controller.currentBackStackEntry!!
            assertTrue(controller.currentDestination!!.hasRoute<ChatNav.YoureConnected>())
            AppNavigator(controller, acceptanceEntry).leaveFlow(room, ChatNav.PartnerFlow)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(room, controller.currentBackStackEntry!!.toRoute<ChatNav.CoupleConversation>())
            controller.popBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<HomeNavigation.Home>()) }
    }
    @Test fun distinctConversationsHaveDistinctEntryIdentityAndBackRestoresArguments() {
        host()
        var first = ""
        val a = ChatNav.CoupleConversation("00000000-0000-4000-8000-000000000001")
        val b = ChatNav.CoupleConversation("00000000-0000-4000-8000-000000000002")
        compose.runOnIdle { AppNavigator(controller).open(a); first = controller.currentBackStackEntry!!.id }
        compose.waitForIdle()
        compose.runOnIdle {
            AppNavigator(controller).open(b)
            assertNotEquals(first, controller.currentBackStackEntry!!.id)
            assertEquals(b, controller.currentBackStackEntry!!.toRoute<ChatNav.CoupleConversation>())
            AppNavigator(controller).back()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(a, controller.currentBackStackEntry!!.toRoute<ChatNav.CoupleConversation>()) }
    }
    @Test fun tabReselectionDoesNotDuplicateRootAndSecurityRootClearsHistory() {
        host()
        compose.runOnIdle {
            val nav = AppNavigator(controller)
            nav.switchTab(HomeNavigation.Explore)
            nav.switchTab(HomeNavigation.Explore)
            nav.open(ChatNav.CoupleConversation("00000000-0000-4000-8000-000000000001"))
            nav.root(HomeNavigation.Home)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(controller.previousBackStackEntry) }
    }
    @Test fun switchingTabsRestoresTheExistingEntryAndItsSavedState() {
        host()
        var profileEntry = ""
        compose.runOnIdle { AppNavigator(controller).switchTab(ProfileNav.EditProfile) }
        compose.waitForIdle()
        compose.runOnIdle {
            profileEntry = controller.currentBackStackEntry!!.id
            controller.currentBackStackEntry!!.savedStateHandle["synthetic-selection"] = 7
            AppNavigator(controller).switchTab(HomeNavigation.Home)
        }
        compose.waitForIdle()
        compose.runOnIdle { AppNavigator(controller).switchTab(ProfileNav.EditProfile) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(profileEntry, controller.currentBackStackEntry!!.id)
            assertEquals(7, controller.currentBackStackEntry!!.savedStateHandle.get<Int>("synthetic-selection"))
        }
    }
}
