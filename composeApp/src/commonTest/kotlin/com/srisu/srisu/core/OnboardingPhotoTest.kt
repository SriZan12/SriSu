package com.srisu.srisu.core

import androidx.lifecycle.ViewModelStore
import coil3.toUri
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.auth.data.local.datastore.AuthDataStore
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.domain.*
import com.srisu.srisu.features.auth.domain.repository.AuthRepository
import com.srisu.srisu.features.auth.presentation.vm.AuthViewModel
import com.srisu.srisu.utils.MediaFile
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPhotoTest {
    @Test fun selectedBytesSurviveFailedUploadAndPickerAccessIsNotNeededForRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessions = session()
        var uploaded = false
        var reads = 0
        var writes = 0
        var fail = true
        val selectedBytes = byteArrayOf(7, 9, 11)
        val bodies = mutableListOf<ByteArray>()
        val client = HttpClientFactory.create(sessions, environment, MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (request.method == HttpMethod.Patch) {
                    writes++
                    assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
                    bodies += request.body.toByteArray()
                    if (fail) return@addHandler respond("""{"error":{"code":"unavailable","message":"Try again.","fields":{}}}""", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType,"application/json"))
                    uploaded = true
                }
                respond("""{"data":{"user":{"id":1,"full_name":"Alex","username":"alex","gender":"MALE","is_phone_verified":true,"is_profile_complete":$uploaded,"profile_photo":${if(uploaded) "\"https://example.test/photo.jpg\"" else "null"}},"progress":{"phone_verified":true,"profile_complete":$uploaded,"next_step":"${if(uploaded) "complete" else "photo"}","photo_skipped":false,"couple_id":null,"membership":"unlinked"}}}""", headers = headersOf(HttpHeaders.ContentType,"application/json"))
            }
        }))
        val preferences = AuthDataStore(MemoryPreferences())
        val repo = AuthRepository(AuthApiService(client, environment))
        val startup = StartupCoordinator(sessions, repo, backgroundScope, preferences)
        val vm = AuthViewModel(repo, sessions, preferences, startup) {
            reads++
            if (reads > 1) null else MediaFile(id=null,fileName="profile.jpg",mimeType="image/jpeg",fileBytes=selectedBytes)
        }
        val owner = ViewModelStore().apply { put("photo",vm) }
        try {
            runCurrent()
            vm.updateProfilePictureUri("content://synthetic/temporary-photo".toUri()); runCurrent()
            assertContentEquals(selectedBytes, vm.authUiState.value.preparedProfilePhoto?.fileBytes)
            vm.updateProfilePictureUri(null); runCurrent() // Picker cancellation preserves the selection.
            assertEquals(1, reads)
            vm.updateProfilePictureUri("content://synthetic/unreadable".toUri()); runCurrent()
            assertIs<BaseUIState.Error>(vm.authUiState.value.baseUIState)
            assertContentEquals(selectedBytes, vm.authUiState.value.preparedProfilePhoto?.fileBytes)
            vm.idleScreen()
            vm.sendSetupProfileRequest(); vm.sendSetupProfileRequest(); runCurrent()
            assertEquals(1,writes)
            assertIs<BaseUIState.Error>(vm.authUiState.value.baseUIState)
            assertContentEquals(selectedBytes,vm.authUiState.value.preparedProfilePhoto?.fileBytes)
            fail=false; vm.idleScreen(); vm.sendSetupProfileRequest(); runCurrent()
            assertEquals(2,reads)
            assertEquals(2,writes)
            assertTrue(bodies.all { it.decodeToString().contains("Content-Type: image/jpeg") })
            assertEquals(AccessDestination.MAIN,assertIs<StartupState.Available>(startup.state.value).destination)
            assertEquals("https://example.test/photo.jpg", sessions.currentSession()?.profilePhoto)
        } finally { owner.clear(); client.close(); Dispatchers.resetMain() }
    }
}
