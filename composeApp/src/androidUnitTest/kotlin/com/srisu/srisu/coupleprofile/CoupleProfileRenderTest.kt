package com.srisu.srisu.coupleprofile

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.core.*
import com.srisu.srisu.core.data.remote.*
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.features.coupleprofile.data.*
import com.srisu.srisu.features.coupleprofile.presentation.*
import com.srisu.srisu.features.home.profile.data.InterestCatalogueRepository
import com.srisu.srisu.features.home.profile.data.remote.api.ProfileApiService
import com.srisu.srisu.theme.SriSuTheme
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoupleProfileRenderTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class, coil3.annotation.DelicateCoilApi::class)
    private fun render(page: ProfilePage, name: String, dark: Boolean=false, largeText: Boolean=false, visitor: Boolean=false, populated: Boolean=false, mineOnly: Boolean=false, published: Boolean=false, longNames: Boolean=false, media: Boolean=false, failedSave: Boolean=false, scrollReturn: Boolean=false, lowerProfile: Boolean=false, brokenMedia: Boolean=false, refreshing: Boolean=false, saving: Boolean=false, discardCheck: Boolean=false) {
        val imageResult = AtomicReference<String?>(null)
        val existingLoader = SingletonImageLoader.get(compose.activity)
        val renderLoader = ImageLoader.Builder(compose.activity).eventListenerFactory {
            object : EventListener() {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) { imageResult.set("success") }
                override fun onError(request: ImageRequest, result: ErrorResult) { imageResult.set("error") }
            }
        }.build()
        if (media) SingletonImageLoader.setUnsafe(renderLoader)
        val gate = CompletableDeferred<Unit>()
        val reads = AtomicInteger(); val writes = AtomicInteger()
        val sessions=session();val lifetime=ApplicationLifetime();val store=ViewModelStore()
        val headers=headersOf(HttpHeaders.ContentType,"application/json")
        val client=HttpClientFactory.create(sessions,environment,MockEngine { request ->
            if (request.url.encodedPath.endsWith("/fixture-photo")) {
                return@MockEngine respond(if (brokenMedia) byteArrayOf(1, 2, 3) else File("src/commonMain/composeResources/drawable/will.jpg").readBytes(), headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
            if (refreshing && request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/1/") && reads.incrementAndGet() > 1) gate.await()
            if (saving && request.method == HttpMethod.Patch) { writes.incrementAndGet(); gate.await() }
            if (failedSave && request.method == HttpMethod.Patch) return@MockEngine respond("", HttpStatusCode.Conflict, headers)
            val body=when {
                request.url.encodedPath.endsWith("history/") -> """{"data":{"results":[],"next_before":null}}"""
                request.url.encodedPath.endsWith("cover-choices/") -> """{"data":{"results":[],"next_before":null}}"""
                request.url.encodedPath.endsWith("plans/") -> """{"data":{"results":[],"next_before":null}}"""
                request.url.encodedPath.endsWith("interests/") -> CoreContractFixtures.INTEREST
                visitor && !published -> CoreContractFixtures.COUPLE_PROFILE_VISITOR
                else -> if(!populated && !mineOnly && !published && !longNames && !media) CoreContractFixtures.COUPLE_PROFILE_MEMBER else {
                    val envelope=ApiJson.parseToJsonElement(CoreContractFixtures.COUPLE_PROFILE_MEMBER).jsonObject
                    val data=envelope.getValue("data").jsonObject.toMutableMap()
                    data["story"]=buildJsonArray { add(buildJsonObject{put("author_id",1);put("prompt","how_met");put("answer","We met at a community library.")});add(buildJsonObject{put("author_id",2);put("prompt","how_met");put("answer","Choosing the same book.")}) }
                    data["song"]=buildJsonObject{put("title","Our song");put("artist","A favourite artist");put("note","A little reminder of our first trip.")}
                    data["interests"]=buildJsonObject{put("shared",buildJsonArray{add("Reading");add("Travel")});put("mine",buildJsonArray{add("Reading");add("Travel")});put("partner",buildJsonArray{add("Reading");add("Travel")})}
                    if(mineOnly) data["interests"]=buildJsonObject {
                        put("shared",buildJsonArray{})
                        put("mine",buildJsonArray{add("Pottery")})
                        put("partner",buildJsonArray{add("Cycling")})
                    }
                    if (longNames) data["members"] = buildJsonArray {
                        add(buildJsonObject { put("id",1);put("name","Annapurna Alexandra Shrestha");put("photo_url",JsonNull) })
                        add(buildJsonObject { put("id",2);put("name","Siddhartha Christopher Maharjan");put("photo_url",JsonNull) })
                    }
                    if (media) data["cover"] = buildJsonObject { put("url","https://example.test/api/social/fixture-photo");put("focal_y",.3) }
                    if (published) {
                        data["viewer"] = JsonPrimitive("visitor");data["can_edit"] = JsonPrimitive(false);data["can_fave"] = JsonPrimitive(true)
                        data["published_sections"] = buildJsonArray { listOf("identity","story","song","interests","cover").forEach { add(it) } }
                        data["interests"] = buildJsonObject { put("shared",buildJsonArray { add("Reading");add("Travel") }) }
                        listOf("revisions","sharing","days_together","plan_count").forEach(data::remove)
                    }
                    JsonObject(envelope+("data" to JsonObject(data))).toString()
                }
            }
            respond(body,headers=headers)
        })
        val vm=CoupleProfileViewModel(CoupleProfileRepository(client,environment,sessions),sessions,InterestCatalogueRepository(MemoryCatalogue(),ProfileApiService(client,environment),environment,sessions),lifetime)
        store.put("profile",vm)
        try {
            compose.setContent {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                    org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
                }
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,if(largeText)1.6f else 1f)) {
                    SriSuTheme(darkTheme=dark) { CoupleProfileScreen(1,onBack={},vm=vm) }
                }
            }
            compose.waitUntil(10000){vm.state.value.profile!=null}
            compose.runOnIdle { if(page==ProfilePage.ANSWER) vm.openAnswer("how_met") else if(page==ProfilePage.POSITION) { vm.open(ProfilePage.COVER); vm.chooseCover(CoverChoice(99,"https://example.test/api/social/fixture-photo")) } else if(page!=ProfilePage.PROFILE && page!=ProfilePage.PREVIEW) vm.open(page) }
            compose.waitUntil(10000){org.robolectric.shadows.ShadowLooper.idleMainLooper(); !vm.state.value.listLoading}
            if(media) compose.waitUntil(10000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.state.value.images.isNotEmpty() }
            if (media) {
                compose.waitUntil(10000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); imageResult.get() != null }
                check(imageResult.get() == if (brokenMedia) "error" else "success")
            }
            compose.waitForIdle()
            if(mineOnly) {
                compose.onNodeWithText("Your interests").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Pottery").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Cycling").assertExists()
            }
            if(page == ProfilePage.PREVIEW) {
                compose.onNodeWithContentDescription("View profile").performClick()
                compose.onAllNodesWithText("Edit").assertCountEquals(0)
                compose.onAllNodesWithText("Save").assertCountEquals(0)
                compose.onNodeWithText("View profile").assertExists()
            }
            if (visitor || published) {
                compose.onAllNodesWithContentDescription("Profile options").assertCountEquals(0)
                compose.onAllNodesWithContentDescription("View profile").assertCountEquals(0)
                compose.onAllNodesWithText("Your interests").assertCountEquals(0)
                compose.onAllNodesWithText("Save").assertCountEquals(0)
            }
            if (failedSave) {
                compose.onNodeWithText("Song name").performTextInput("A song worth keeping")
                compose.onAllNodesWithText("Save").assertCountEquals(1)
                compose.onNodeWithText("Save").performClick()
                compose.waitUntil(10000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.state.value.error != null }
                compose.onNodeWithText("A song worth keeping").performScrollTo().assertIsDisplayed()
                check(vm.state.value.page == ProfilePage.SONG && vm.state.value.draft?.dirty == true)
            }
            if (scrollReturn) {
                compose.onAllNodesWithText("Our song")[0].performScrollTo()
                val before = compose.onAllNodesWithText("Our song")[0].fetchSemanticsNode().boundsInRoot.top
                compose.runOnIdle { vm.open(ProfilePage.SONG) }
                compose.onNodeWithText("Song name").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.waitUntil(10000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.state.value.page == ProfilePage.PROFILE && !vm.state.value.loading }
                compose.waitForIdle()
                val after = compose.onAllNodesWithText("Our song")[0].fetchSemanticsNode().boundsInRoot.top
                check(kotlin.math.abs(before-after) < 2f) { "Overview scroll moved on returning from editor" }
            }
            if (lowerProfile) compose.onNodeWithText("Together").performScrollTo()
            if (refreshing) {
                val before = compose.onNodeWithContentDescription("View profile").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle { vm.refresh() }
                compose.waitUntil(10000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); reads.get() > 1 }
                compose.onNodeWithContentDescription("View profile").assertIsDisplayed()
                check(compose.onNodeWithContentDescription("View profile").fetchSemanticsNode().boundsInRoot == before)
            }
            if (saving) {
                compose.onNodeWithText("Song name").performTextInput("Our draft")
                compose.onNodeWithText("Save").performClick()
                compose.waitUntil(10000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); writes.get() == 1 }
                compose.onNodeWithText("Saving…").assertIsNotEnabled()
                check(vm.state.value.page == ProfilePage.SONG)
            }
            if (discardCheck) {
                compose.onNodeWithText("Song name").performTextInput("Our draft")
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
                compose.onNodeWithText("Keep editing").performClick()
                compose.onNodeWithText("Our draft").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
            }
            val output=File("build/reports/couple-profile/$name.png").apply{parentFile?.mkdirs()}
            check(compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty())
            compose.runOnIdle {
                val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                output.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        } finally {
            compose.runOnIdle { store.clear(); lifetime.close(); client.close() }
            gate.complete(Unit)
            if (media) SingletonImageLoader.setUnsafe(existingLoader)
            renderLoader.shutdown()
        }
    }
    @Test fun ownerDayOne()=render(ProfilePage.PROFILE,"android-owner-day-one")
    @Test fun visitorPrivate()=render(ProfilePage.PROFILE,"android-visitor-private",visitor=true)
    @Test fun storyEditor()=render(ProfilePage.STORY_EDIT,"android-story-editor")
    @Test fun songEditor()=render(ProfilePage.SONG,"android-song-editor")
    @Test fun interestsEditor()=render(ProfilePage.INTERESTS,"android-interests-editor")
    @Test fun storyRead()=render(ProfilePage.STORY,"android-story-read",populated=true)
    @Test fun coverPosition()=render(ProfilePage.POSITION,"android-cover-position",media=true)
    @Test fun coverEditor()=render(ProfilePage.COVER,"android-cover-editor")
    @Test fun sharingEditor()=render(ProfilePage.SHARING,"android-sharing-editor")
    @Test fun plans()=render(ProfilePage.PLANS,"android-plans")
    @Test fun newPlan()=render(ProfilePage.NEW_PLAN,"android-new-plan")
    @Test fun individualInterestsAreVisibleWithoutSharedMatches()=render(ProfilePage.PROFILE,"android-individual-interests",mineOnly=true)
    @Test fun readOnlyPreview()=render(ProfilePage.PREVIEW,"android-profile-preview",populated=true)
    @Test fun populatedProfile()=render(ProfilePage.PROFILE,"android-owner-populated",populated=true)
    @Test fun publishedVisitor()=render(ProfilePage.PROFILE,"android-visitor-published",visitor=true,published=true)
    @Test fun lowerProfile()=render(ProfilePage.PROFILE,"android-profile-sections",populated=true,lowerProfile=true)
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun smallLongNames()=render(ProfilePage.PROFILE,"android-owner-long-small",longNames=true,largeText=true)
    @Test @Config(qualifiers="w600dp-h960dp-xhdpi") fun wideProfile()=render(ProfilePage.PROFILE,"android-owner-wide",populated=true)
    @Test fun photoCover()=render(ProfilePage.PROFILE,"android-photo-cover",media=true)
    @Test fun failedPhoto()=render(ProfilePage.PROFILE,"android-photo-failed",media=true,brokenMedia=true)
    @Test fun refreshKeepsContentStable()=render(ProfilePage.PROFILE,"android-profile-refreshing",populated=true,refreshing=true)
    @Test fun savingWaitsForConfirmation()=render(ProfilePage.SONG,"android-song-saving",saving=true)
    @Test fun unsavedChangesCanBeKept()=render(ProfilePage.SONG,"android-unsaved-dialog",discardCheck=true)
    @Test fun darkProfile()=render(ProfilePage.PROFILE,"android-owner-dark",dark=true,populated=true)
    @Test fun failedSongSaveRetainsInput()=render(ProfilePage.SONG,"android-song-failed-save",failedSave=true)
    @Test fun returningFromEditorPreservesProfilePosition()=render(ProfilePage.PROFILE,"android-return-to-profile",populated=true,scrollReturn=true)
    @Test fun dateEditor()=render(ProfilePage.DATE,"android-date-editor")
    @Test fun answerSheet()=render(ProfilePage.ANSWER,"android-answer-sheet",populated=true)
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun smallSongEditor()=render(ProfilePage.SONG,"android-song-small")
    @Test fun darkLargeText()=render(ProfilePage.SONG,"android-song-dark-large-text",dark=true,largeText=true)
}
