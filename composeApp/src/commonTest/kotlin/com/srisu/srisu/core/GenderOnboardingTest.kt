package com.srisu.srisu.core

import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.auth.presentation.components.CustomProfileSetupScreen
import com.srisu.srisu.features.auth.presentation.screen.profilesetup.Gender
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GenderOnboardingTest {
    private class Flow(scope: TestScope, var step: String, var gender: String?) {
        val sessions = session()
        val writes = mutableListOf<JsonObject>()
        var responseStatus = HttpStatusCode.OK
        var release: CompletableDeferred<Unit>? = null
        private val headers = headersOf(HttpHeaders.ContentType, "application/json")
        private fun profile() = """{"data":{"user":{"id":1,"full_name":"Test User","username":"test","gender":${gender?.let { "\"$it\"" } ?: "null"},"is_phone_verified":true,"is_profile_complete":${step == "complete"}},"progress":{"phone_verified":true,"profile_complete":${step == "complete"},"next_step":"$step","photo_skipped":${step == "complete"},"couple_id":null,"membership":"unlinked"}}}"""
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(scope.testScheduler)
            addHandler { request ->
                // Local logout can issue a separate best-effort revocation request.
                if (request.url.encodedPath.endsWith("logout/")) return@addHandler respond("{}", headers = headers)
                assertEquals("/api/auth/setup-profile/", request.url.encodedPath)
                if (request.method == HttpMethod.Patch) {
                    val body = ApiJson.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    writes += body
                    release?.let { withContext(NonCancellable) { it.await() } }
                    if (responseStatus != HttpStatusCode.OK) return@addHandler respond(
                        """{"error":{"code":"profile_save_failed","message":"Please retry.","fields":{}}}""",
                        responseStatus, headers)
                    body["gender"]?.let { gender = it.jsonPrimitive.content }
                    if (body.containsKey("full_name")) step = "photo"
                    if (body["skip_photo"]?.jsonPrimitive?.boolean == true) step = "complete"
                }
                respond(profile(), headers = headers)
            }
        }))
        private val preferences = AuthDataStore(MemoryPreferences())
        private val repo = AuthRepository(AuthApiService(client, environment))
        val startup = StartupCoordinator(sessions, repo, scope.backgroundScope, preferences)
        val vm = AuthViewModel(repo, sessions, preferences, startup)
        private val owner = ViewModelStore().apply { put("auth", vm) }
        fun close() { owner.clear(); client.close() }
    }

    private fun withFlow(step: String = "photo", gender: String? = null, block: suspend TestScope.(Flow) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val flow = Flow(this, step, gender)
        try { runCurrent(); block(flow) }
        finally { flow.close(); Dispatchers.resetMain() }
    }

    @Test fun nameGenderPhotoSaveInOrderAndBackRetainsSelection() = withFlow(step = "name") { flow ->
        val vm = flow.vm
        assertEquals(CustomProfileSetupScreen.AddFullNameScreen, vm.authUiState.value.currentScreen)
        vm.saveName(); runCurrent()
        assertEquals(CustomProfileSetupScreen.SelectGenderScreen, vm.authUiState.value.currentScreen)
        assertEquals(2, vm.authUiState.value.currentProgressStep)
        vm.saveGender(); runCurrent()
        assertEquals(1, flow.writes.size) // No request before a selection.
        vm.updateGender(Gender.FEMALE)
        flow.release = CompletableDeferred()
        repeat(4) { vm.saveGender() }
        runCurrent()
        assertEquals(2, flow.writes.size)
        assertEquals(setOf("gender"), flow.writes.last().keys)
        assertEquals("FEMALE", flow.writes.last()["gender"]?.jsonPrimitive?.content)
        assertIs<BaseUIState.Loading>(vm.authUiState.value.baseUIState)
        vm.navigateProfileBack(); vm.updateGender(Gender.MALE)
        assertEquals(Gender.FEMALE, vm.authUiState.value.gender)
        assertEquals(CustomProfileSetupScreen.SelectGenderScreen, vm.authUiState.value.currentScreen)
        flow.release!!.complete(Unit); runCurrent()
        assertEquals(CustomProfileSetupScreen.SetProfilePictureScreen, vm.authUiState.value.currentScreen)
        assertEquals(3, vm.authUiState.value.currentProgressStep)
        assertEquals("FEMALE", flow.sessions.currentSession()?.gender)
        vm.navigateProfileBack()
        assertEquals(CustomProfileSetupScreen.SelectGenderScreen, vm.authUiState.value.currentScreen)
        assertEquals(Gender.FEMALE, vm.authUiState.value.gender)
        vm.saveGender(); runCurrent() // An unchanged response must still advance after Back.
        assertEquals(CustomProfileSetupScreen.SetProfilePictureScreen, vm.authUiState.value.currentScreen)
        vm.sendSetupProfileRequest(); runCurrent()
        assertEquals(AccessDestination.MAIN, assertIs<StartupState.Available>(flow.startup.state.value).destination)
    }

    @Test fun backFromGenderReturnsToNameAndUnchangedNameCanContinue() = withFlow { flow ->
        flow.vm.updateGender(Gender.MALE)
        flow.vm.navigateProfileBack()
        assertEquals(CustomProfileSetupScreen.AddFullNameScreen, flow.vm.authUiState.value.currentScreen)
        assertEquals(1, flow.vm.authUiState.value.currentProgressStep)
        flow.vm.saveName(); runCurrent()
        assertEquals(CustomProfileSetupScreen.SelectGenderScreen, flow.vm.authUiState.value.currentScreen)
        assertEquals(Gender.MALE, flow.vm.authUiState.value.gender)
        assertEquals(AccessDestination.GENDER, assertIs<StartupState.Available>(flow.startup.state.value).destination)
    }

    @Test fun bootstrapRestoresPersistedGenderAtPhoto() = withFlow(gender = "MALE") { flow ->
        assertEquals(AccessDestination.PHOTO, assertIs<StartupState.Available>(flow.startup.state.value).destination)
        assertEquals(CustomProfileSetupScreen.SetProfilePictureScreen, flow.vm.authUiState.value.currentScreen)
        flow.vm.navigateProfileBack()
        assertEquals(Gender.MALE, flow.vm.authUiState.value.gender)
        assertEquals(CustomProfileSetupScreen.SelectGenderScreen, flow.vm.authUiState.value.currentScreen)
    }

    @Test fun failedSavesKeepSelectionAndExplicitRetryWorks() = withFlow { flow ->
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.Forbidden, HttpStatusCode.ServiceUnavailable)) {
            flow.responseStatus = status
            flow.vm.updateGender(Gender.MALE)
            flow.vm.saveGender(); runCurrent()
            assertIs<BaseUIState.Error>(flow.vm.authUiState.value.baseUIState)
            assertEquals(CustomProfileSetupScreen.SelectGenderScreen, flow.vm.authUiState.value.currentScreen)
            assertEquals(Gender.MALE, flow.vm.authUiState.value.gender)
            assertNull(flow.sessions.currentSession()?.gender)
            flow.vm.idleScreen()
        }
        flow.responseStatus = HttpStatusCode.OK
        flow.vm.saveGender(); runCurrent()
        assertEquals(CustomProfileSetupScreen.SetProfilePictureScreen, flow.vm.authUiState.value.currentScreen)
        assertEquals(4, flow.writes.size)
    }

    @Test fun expiredSessionReturnsToAuthentication() = withFlow { flow ->
        flow.responseStatus = HttpStatusCode.Unauthorized
        flow.vm.updateGender(Gender.FEMALE)
        flow.vm.saveGender(); runCurrent()
        assertNull(flow.sessions.currentSession())
        assertEquals(AccessDestination.PHONE, assertIs<StartupState.Available>(flow.startup.state.value).destination)
    }

    @Test fun logoutRejectsLateGenderSave() = withFlow { flow ->
        flow.release = CompletableDeferred()
        flow.vm.updateGender(Gender.FEMALE)
        flow.vm.saveGender(); runCurrent()
        assertEquals(1, flow.writes.size)
        flow.sessions.clearSession(); runCurrent()
        flow.release!!.complete(Unit); runCurrent()
        assertNull(flow.sessions.currentSession())
        assertEquals(AccessDestination.PHONE, assertIs<StartupState.Available>(flow.startup.state.value).destination)
    }
}
