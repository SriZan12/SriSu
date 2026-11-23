package com.srisu.srisu.core

import android.graphics.Bitmap
import com.srisu.srisu.core.config.ApiEnvironment
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.session.*
import com.srisu.srisu.features.auth.data.remote.api.AuthApiService
import com.srisu.srisu.features.auth.data.remote.dto.ProfileSetupDTO
import com.srisu.srisu.features.auth.data.remote.response.*
import com.srisu.srisu.features.home.profile.data.dto.ProfileUpdateDTO
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.utils.*
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import kotlin.test.*

/** Real multipart HTTP against tools/core_integration.py's disposable database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileRepairTransportTest {
    @Test fun preparedPhotoAndInterestSelectionsSurviveReload() {
        val fixturePath = System.getenv("SRISU_CORE_INTEGRATION_FILE")
        assumeTrue("Use the disposable backend runner", fixturePath != null)
        val fixture = ApiJson.parseToJsonElement(File(fixturePath!!).readText()).jsonObject
        val environment = ApiEnvironment(fixture.getValue("base_url").jsonPrimitive.content, development = true)
        val id = fixture.getValue("account_id").jsonPrimitive.long
        val sessions = SessionCoordinator(MemorySession()).apply { saveSession(ApiJson.encodeToString(Session(
            id = id, access = fixture.getValue("access").jsonPrimitive.content)), Constants.Auth.SESSION_KEY) }
        val client = HttpClientFactory.create(sessions, environment, OkHttp.create {})
        AppContext.setUp(RuntimeEnvironment.getApplication())
        val file = File.createTempFile("synthetic", ".png", RuntimeEnvironment.getApplication().cacheDir)
        try { runBlocking { withTimeout(20_000) {
            val image = Bitmap.createBitmap(1800, 1200, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.CYAN) // theme-audit: image pixels
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            val media = assertNotNull(prepareProfilePhoto(file.absolutePath))
            val auth = AuthApiService(client)
            assertIs<NetworkAPIResult.Success<ProfileResponse?>>(auth.updateGender("FEMALE").result)
            val upload = assertIs<NetworkAPIResult.Success<ProfileResponse?>>(auth.sendProfileSetupRequest(ProfileSetupDTO(), media).result)
            assertFalse(upload.response?.user?.profilePhoto.isNullOrBlank())
            val restored = assertNotNull(assertIs<NetworkAPIResult.Success<ProfileResponse?>>(auth.getProfile().result).response?.user)
            assertEquals(upload.response?.user?.profilePhoto, restored.profilePhoto)
            val profile = ProfileApiService(client, environment)
            val catalogue = assertNotNull(assertIs<NetworkAPIResult.Success<InterestResponse?>>(profile.getInterestList().result).response?.interests)
            val option = assertNotNull(catalogue.first())
            suspend fun save(removed: Boolean) = profile.sendUpdateProfileRequest(id, ProfileUpdateDTO(
                fullName = restored.fullName, username = "synthetic_repair", gender = "FEMALE",
                userInterests = listOf(User.UserInterest(interest = option.id, name = "stale client label", removed = removed))), null, null)
            repeat(2) { assertIs<NetworkAPIResult.Success<ProfileResponse?>>(save(false).result) }
            val saved = assertNotNull(assertIs<NetworkAPIResult.Success<ProfileResponse?>>(auth.getProfile().result).response?.user?.userInterests)
            assertEquals(option.name, saved.filterNotNull().single { it.removed != true }.name)
            assertIs<NetworkAPIResult.Success<ProfileResponse?>>(save(true).result)
            val cleared = assertIs<NetworkAPIResult.Success<ProfileResponse?>>(auth.getProfile().result).response?.user?.userInterests.orEmpty()
            assertTrue(cleared.filterNotNull().none { it.removed != true })
        } } } finally { file.delete(); client.close() }
    }
}
