package com.srisu.srisu.features.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.srisu.srisu.core.MemoryPreferences
import com.srisu.srisu.core.environment
import com.srisu.srisu.core.session
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.domain.StartupCoordinator
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.coupleprofile.data.CoupleProfileRepository
import com.srisu.srisu.features.notifications.*
import com.srisu.srisu.navigation.CoupleAccessCoordinator
import com.srisu.srisu.navigation.graph.*
import com.srisu.srisu.theme.SriSuTheme
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.*

/** Uses the production navigation graph and repositories with synthetic HTTP responses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w320dp-h640dp-xhdpi")
class SettingsNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun detailRoutesBackAppearanceAndCacheWorkAtDoubleTextSize() {
        val sessions=session();val lifetime=ApplicationLifetime();val storage=MemoryPreferences()
        val prefs=AppearancePreferences(storage)
        val client=HttpClientFactory.create(sessions,environment,MockEngine { request ->
            when {
                request.url.encodedPath.contains("notifications/v1/preferences") -> respond("""{"data":{"contract":"notifications-1","push":false,"chat":true,"activity":true,"revision":1}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
                request.url.encodedPath.contains("auth/setup-profile") -> respond("""{"data":{"user":{"id":1,"full_name":"Alex","username":"alex","is_phone_verified":true,"is_profile_complete":true},"progress":{"phone_verified":true,"profile_complete":true,"next_step":"complete","photo_skipped":true,"couple_id":null,"membership":"unlinked"}}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
                else -> respond("{}",HttpStatusCode.NotFound,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        val startup=StartupCoordinator(sessions,AuthRepository(AuthApiService(client,environment)),lifetime.scope,AuthDataStore(storage))
        val access=CoupleAccessCoordinator(sessions,startup,CoupleProfileRepository(client,environment,sessions),lifetime)
        val notifications=NotificationRepository(NotificationApi(client,environment),sessions,lifetime)
        var loaded=false
        val source=object:SettingsSource {
            override suspend fun load():NetworkAPIResult<SettingsContext?> {loaded=true;return NetworkAPIResult.Success(SettingsContext("settings-1",samplePartner))}
            override suspend fun disconnect(partner:SettingsPartner):NetworkAPIResult<SettingsContext?> = NetworkAPIResult.Success(SettingsContext("settings-1"))
        }
        val dependencies=module {
            single {sessions};single {access};single {prefs};single {notifications}
            factory {SettingsViewModel(source,sessions,connectionChanged=access::retry)}
        }
        lateinit var nav:androidx.navigation.NavHostController
        try {
            compose.setContent {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    KoinApplication(application={modules(dependencies)}) {
                        SriSuTheme {nav=rememberNavController();NavHost(nav,startDestination=ProfileNav.Settings) {profileGraph(nav)}}
                    }
                }
            }
            compose.waitUntil(10000) {ShadowLooper.idleMainLooper();loaded}
            fun open(label:String,page:String) {
                compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
                compose.onNodeWithText(label).performClick()
                compose.waitForIdle()
                compose.runOnIdle {assertEquals(page,nav.currentBackStackEntry!!.toRoute<ProfileNav.SettingsDetail>().page)}
            }
            fun back() {compose.onNodeWithContentDescription("Go back").performClick();compose.waitForIdle()}
            open("Account","account")
            compose.onNodeWithText("Edit profile").assertIsDisplayed();back()
            open("Language","language")
            compose.onNodeWithText("English").assertIsDisplayed();back()
            open("Shared preferences","shared")
            compose.waitUntil(10000) {ShadowLooper.idleMainLooper();compose.onAllNodesWithText("Connect your partner").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("settings-detail-list").performScrollToNode(hasText("Connect your partner"))
            compose.onNodeWithText("Connect your partner").assertIsDisplayed();back()
            open("Appearance","appearance")
            compose.onNodeWithText("Dark",substring=false).performClick()
            compose.waitUntil(10000) {ShadowLooper.idleMainLooper();runBlocking {prefs.appearance.first()}==AppAppearance.DARK}
            compose.onNodeWithText("Dark",substring=false).assertIsSelected()
            back();open("Appearance","appearance")
            compose.onNodeWithText("Dark",substring=false).assertIsSelected()
            compose.onNodeWithText("Use device setting").performClick()
            compose.waitUntil(10000) {ShadowLooper.idleMainLooper();runBlocking {prefs.appearance.first()}==AppAppearance.SYSTEM}
            back();open("Downloads","downloads")
            compose.onNodeWithTag("settings-detail-list").performScrollToNode(hasText("Clear image cache"))
            compose.onNodeWithText("Clear image cache").performClick()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Clear image cache").performClick()
            compose.onNodeWithText("Clear cache",substring=false).performClick()
            compose.waitUntil(10000) {ShadowLooper.idleMainLooper();compose.onAllNodesWithText("Image cache cleared.").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("settings-detail-list").performScrollToNode(hasText("Image cache cleared."))
            compose.onNodeWithText("Image cache cleared.").assertIsDisplayed()
            back()
            for((label,page) in listOf("Accessibility" to "accessibility","Support" to "support","Privacy & Security" to "privacy")) {
                open(label,page)
                compose.onNodeWithTag("settings-detail-list").performTouchInput {swipeUp()}
                back()
                compose.onNodeWithText(label).assertIsDisplayed()
            }
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Shared notifications",substring=false))
            compose.onNodeWithText("Shared notifications",substring=false).performClick()
            compose.waitUntil(10000) {ShadowLooper.idleMainLooper();notifications.state.value.preferences!=null}
            compose.onNodeWithTag("settings-detail-list").performScrollToNode(hasContentDescription("Invitations & shared activity"))
            compose.onNodeWithContentDescription("Invitations & shared activity").assertIsNotEnabled()
            compose.onNodeWithText("Share crash diagnostics").assertDoesNotExist()
            back()
        } finally {notifications.close();lifetime.close();client.close()}
    }
}
