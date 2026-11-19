package com.srisu.srisu.features.profile

import androidx.test.core.app.ApplicationProvider
import com.srisu.srisu.baseframework.BaseUIState
import com.srisu.srisu.core.*
import com.srisu.srisu.core.data.local.*
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.features.home.profile.domain.repository.ProfileRepository
import com.srisu.srisu.features.home.profile.presentation.vm.EditProfileViewModel
import com.srisu.srisu.utils.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import coil3.toUri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSaveFlowTest {
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val profile = """{"data":{"user":{"id":1,"full_name":"Synthetic Name","username":"synthetic","is_profile_complete":true,"is_phone_verified":true}}}"""
    private val dao = object : CatalogueDao() {
        override suspend fun read(scope: String): CatalogueSnapshot? = null
        override suspend fun insert(snapshot: CatalogueSnapshot) {}
        override suspend fun delete(scope: String) {}
    }
    @Test fun savedPhotoTextEditOmitsHiddenFieldsAndInvalidNewPhotoKeepsTheDraft() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        AppContext.setUp(context)
        val sessions=session()
        val populated=profile.replace("\"username\":\"synthetic\"", "\"username\":\"synthetic\",\"profile_photo\":\"https://example.invalid/already-saved.jpg\"")
        var writes=0
        val client=HttpClientFactory.create(sessions,environment,MockEngine {request ->
            when {
                request.url.encodedPath.endsWith("interests/") -> respond("""{"data":{"interests":[]}}""",headers=headers)
                request.method==HttpMethod.Put -> {
                    writes++
                    val body=request.body.toByteArray().decodeToString()
                    for(field in listOf("dob","gender","zodiac_sign","phone_number")) assertFalse(body.contains("name=$field") || body.contains("name=\"$field\""),field)
                    if(writes==1) assertFalse(body.contains("profile_photo")) else assertTrue(body.contains("profile_photo"))
                    respond(populated,headers=headers)
                }
                else -> respond(populated,headers=headers)
            }
        })
        val api=ProfileApiService(client,environment)
        val vm=EditProfileViewModel(ConnectivityObserver(),sessions,ProfileRepository(api,BaseApiService(client),InterestCatalogueRepository(dao,api,environment,sessions)))
        val invalid=java.io.File(context.cacheDir,"invalid-profile.png").apply {writeText("not an image")}
        try {
            withTimeout(5000) {vm.isInitialized.first {it}}
            vm.updateBio("Keep my edited biography")
            vm.updateProfile()
            withTimeout(5000) {vm.editProfileUIState.first {it.baseUIState is BaseUIState.Success<*>}}
            assertEquals(1,writes)
            vm.idleScreen();vm.updateBio("Keep this unsaved draft")
            vm.updateProfilePictureUri(invalid.toURI().toString().toUri());vm.updateProfile()
            val error=withTimeout(5000) {vm.editProfileUIState.first {it.baseUIState is BaseUIState.Error}}
            assertTrue((error.baseUIState as BaseUIState.Error).message.orEmpty().contains("5 MB"))
            assertEquals("Keep this unsaved draft",error.bio);assertEquals(1,writes)
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",invalid)
            assertNotNull(FileManager().createProfilePhotoFromPath(invalid.toURI().toString()))
            withTimeout(5000) {while(vm.editProfileUIState.value.baseUIState !is BaseUIState.Idle) {vm.idleScreen();yield()}}
            vm.idleScreen();vm.updateProfilePictureUri(invalid.toURI().toString().toUri());vm.updateProfile()
            withTimeout(5000) {vm.editProfileUIState.first {it.baseUIState is BaseUIState.Success<*>}}
            assertEquals(2,writes)
        } finally {invalid.delete();androidx.lifecycle.ViewModelStore().also {it.put("profile",vm);it.clear()};client.close();Dispatchers.resetMain()}
    }
    @Test fun structuredValidationShowsAnActionableMessage() {
        val failure=ApiError(NetworkAPIResult.ErrorType.BAD_REQUEST,"validation_failed","Couldn't complete the request",fields=mapOf("username" to listOf("unique")))
        assertEquals("This username is taken. Choose another.",com.srisu.srisu.features.home.profile.presentation.vm.profileUpdateError(failure))
    }
    @Test fun failedInitialReadCannotSaveEmptyProfileAndRetryRecovers() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        AppContext.setUp(ApplicationProvider.getApplicationContext())
        val sessions = session()
        var fail = true; var saves = 0
        val client = HttpClientFactory.create(sessions, environment, MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("interests/") -> respond("""{"data":{"interests":[]}}""", headers = headers)
                request.method == HttpMethod.Put -> { saves++; respond(profile, headers = headers) }
                fail -> respond("", HttpStatusCode.Forbidden)
                else -> respond(profile, headers = headers)
            }
        })
        val api = ProfileApiService(client, environment)
        val vm = EditProfileViewModel(ConnectivityObserver(), sessions,
            ProfileRepository(api, BaseApiService(client), InterestCatalogueRepository(dao, api, environment, sessions)))
        try {
            withTimeout(5000) { vm.initializationError.first { it != null } }
            assertFalse(vm.isInitialized.value)
            vm.updateProfile(); assertEquals(0, saves)
            fail = false; vm.retryInitialization()
            withTimeout(5000) { vm.isInitialized.first { it } }
            assertEquals("Synthetic Name", vm.editProfileUIState.value.fullName)
        } finally { androidx.lifecycle.ViewModelStore().also { it.put("profile", vm); it.clear() }; client.close(); Dispatchers.resetMain() }
    }
    @Test fun countrySelectionAcceptsTheNormalizedCityLookup() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        AppContext.setUp(ApplicationProvider.getApplicationContext())
        val sessions = session()
        val client = HttpClientFactory.create(sessions, environment, MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("interests/") -> respond("""{"data":{"interests":[]}}""", headers = headers)
                request.url.host == "countriesnow.space" -> respond("""{"error":false,"msg":"cities","data":["Kathmandu"]}""", headers = headers)
                else -> respond(profile, headers = headers)
            }
        })
        val api = ProfileApiService(client, environment)
        val vm = EditProfileViewModel(ConnectivityObserver(), sessions,
            ProfileRepository(api, BaseApiService(client), InterestCatalogueRepository(dao, api, environment, sessions)))
        try {
            withTimeout(5000) { vm.isInitialized.first { it } }
            vm.updateCountry(requireNotNull(Country.getCountryModelFromName("Nepal")))
            withTimeout(5000) { vm.editProfileUIState.first { it.cities?.contains("Kathmandu") == true } }
            assertEquals(listOf("Kathmandu"),vm.editProfileUIState.value.cities)
        } finally { androidx.lifecycle.ViewModelStore().also { it.put("profile", vm); it.clear() }; client.close(); Dispatchers.resetMain() }
    }
    @Test fun doubleSaveFinishesWithoutCityLookupAndKeepsRefreshedTokens() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        AppContext.setUp(ApplicationProvider.getApplicationContext())
        val sessions = session(); val stamp = sessions.stamp()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var saves = 0
        val client = HttpClientFactory.create(sessions, environment, MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("interests/") -> respond("""{"data":{"interests":[]}}""", headers = headers)
                request.url.host == "countriesnow.space" -> awaitCancellation()
                request.method == HttpMethod.Put -> {
                    saves++; entered.complete(Unit); release.await()
                    sessions.rotateIfCurrent("rotated-access", "rotated-refresh", stamp)
                    respond(profile, headers = headers)
                }
                else -> respond(profile, headers = headers)
            }
        })
        val api = ProfileApiService(client, environment)
        val vm = EditProfileViewModel(ConnectivityObserver(), sessions,
            ProfileRepository(api, BaseApiService(client), InterestCatalogueRepository(dao, api, environment, sessions)))
        try {
            withTimeout(5000) { vm.isInitialized.first { it } }
            vm.updateProfile(); entered.await(); vm.updateProfile(); release.complete(Unit)
            withTimeout(5000) { vm.editProfileUIState.first { it.baseUIState is BaseUIState.Success<*> } }
            assertEquals(1, saves)
            assertEquals("rotated-access", sessions.accessToken()); assertEquals(stamp, sessions.stamp())
        } finally { androidx.lifecycle.ViewModelStore().also { it.put("profile", vm); it.clear() }; client.close(); Dispatchers.resetMain() }
    }
}
