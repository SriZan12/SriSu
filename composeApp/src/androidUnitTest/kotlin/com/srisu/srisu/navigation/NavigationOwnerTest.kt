package com.srisu.srisu.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.navigation.NavDestination.Companion.hasRoute
import com.srisu.srisu.navigation.graph.*
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
                composable<ChatNav.ChatScreen> { entry ->
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
        compose.runOnIdle { AppNavigator(controller).open(ChatNav.ChatScreen("00000000-0000-4000-8000-000000000001")) }
        compose.waitForIdle()
        lateinit var probe: Probe
        compose.runOnIdle { probe = probes.getValue(controller.currentBackStackEntry!!.id); AppNavigator(controller).back() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(probe.cleared) }
    }
    @Test fun librarySavedStateRestoresMinimalArguments() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = HomeNavigation.Home) {
                composable<HomeNavigation.Home> { }
                composable<ChatNav.ChatScreen> { }
            }
        }
        val route = ChatNav.ChatScreen("00000000-0000-4000-8000-000000000001")
        compose.runOnIdle { AppNavigator(controller).open(route) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(route, controller.currentBackStackEntry!!.toRoute<ChatNav.ChatScreen>()) }
    }
    @Test fun duplicateAndLateCallbacksCannotStackOrNavigate() {
        host()
        lateinit var origin: AppNavigator
        val room = ChatNav.ChatScreen("00000000-0000-4000-8000-000000000001")
        compose.runOnIdle {
            origin = AppNavigator(controller, controller.currentBackStackEntry)
            origin.open(room)
            origin.open(room)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(room, controller.currentBackStackEntry!!.toRoute<ChatNav.ChatScreen>())
            origin.open(ProfileNav.EditProfile)
            assertTrue(controller.currentDestination!!.hasRoute<ChatNav.ChatScreen>())
            AppNavigator(controller).back()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<HomeNavigation.Home>()) }
    }
    @Test fun distinctConversationsHaveDistinctEntryIdentityAndBackRestoresArguments() {
        host()
        var first = ""
        val a = ChatNav.ChatScreen("00000000-0000-4000-8000-000000000001")
        val b = ChatNav.ChatScreen("00000000-0000-4000-8000-000000000002")
        compose.runOnIdle { AppNavigator(controller).open(a); first = controller.currentBackStackEntry!!.id }
        compose.waitForIdle()
        compose.runOnIdle {
            AppNavigator(controller).open(b)
            assertNotEquals(first, controller.currentBackStackEntry!!.id)
            assertEquals(b, controller.currentBackStackEntry!!.toRoute<ChatNav.ChatScreen>())
            AppNavigator(controller).back()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(a, controller.currentBackStackEntry!!.toRoute<ChatNav.ChatScreen>()) }
    }
    @Test fun tabReselectionDoesNotDuplicateRootAndSecurityRootClearsHistory() {
        host()
        compose.runOnIdle {
            val nav = AppNavigator(controller)
            nav.switchTab(HomeNavigation.Explore)
            nav.switchTab(HomeNavigation.Explore)
            nav.open(ChatNav.ChatScreen("00000000-0000-4000-8000-000000000001"))
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
