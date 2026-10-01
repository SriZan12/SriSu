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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoupleProfileRenderTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun render(page: ProfilePage, name: String, dark: Boolean=false, largeText: Boolean=false, visitor: Boolean=false, populated: Boolean=false, mineOnly: Boolean=false) {
        val sessions=session();val lifetime=ApplicationLifetime();val store=ViewModelStore()
        val headers=headersOf(HttpHeaders.ContentType,"application/json")
        val client=HttpClientFactory.create(sessions,environment,MockEngine { request ->
            val body=when {
                request.url.encodedPath.endsWith("history/") -> """{"data":{"results":[],"next_before":null}}"""
                request.url.encodedPath.endsWith("cover-choices/") -> """{"data":{"results":[],"next_before":null}}"""
                request.url.encodedPath.endsWith("plans/") -> """{"data":{"results":[],"next_before":null}}"""
                request.url.encodedPath.endsWith("interests/") -> CoreContractFixtures.INTEREST
                visitor -> CoreContractFixtures.COUPLE_PROFILE_VISITOR
                else -> if(!populated && !mineOnly) CoreContractFixtures.COUPLE_PROFILE_MEMBER else {
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
            compose.runOnIdle { if(page==ProfilePage.ANSWER) vm.openAnswer("how_met") else if(page!=ProfilePage.PROFILE && page!=ProfilePage.PREVIEW) vm.open(page) }
            compose.waitUntil(10000){org.robolectric.shadows.ShadowLooper.idleMainLooper(); !vm.state.value.listLoading}
            compose.waitForIdle()
            if(mineOnly) {
                compose.onNodeWithText("Your interests").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Pottery").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Cycling").assertExists()
            }
            if(page == ProfilePage.PREVIEW) {
                compose.onNodeWithText("View profile").performClick()
                compose.onAllNodesWithText("Edit").assertCountEquals(0)
                compose.onAllNodesWithText("Save").assertCountEquals(0)
                compose.onNodeWithText("View profile").assertExists()
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
        } finally {compose.runOnIdle{store.clear();lifetime.close();client.close()}}
    }
    @Test fun ownerDayOne()=render(ProfilePage.PROFILE,"android-owner-day-one")
    @Test fun visitorPrivate()=render(ProfilePage.PROFILE,"android-visitor-private",visitor=true)
    @Test fun storyEditor()=render(ProfilePage.STORY_EDIT,"android-story-editor")
    @Test fun songEditor()=render(ProfilePage.SONG,"android-song-editor")
    @Test fun interestsEditor()=render(ProfilePage.INTERESTS,"android-interests-editor")
    @Test fun coverEditor()=render(ProfilePage.COVER,"android-cover-editor")
    @Test fun sharingEditor()=render(ProfilePage.SHARING,"android-sharing-editor")
    @Test fun plans()=render(ProfilePage.PLANS,"android-plans")
    @Test fun newPlan()=render(ProfilePage.NEW_PLAN,"android-new-plan")
    @Test fun individualInterestsAreVisibleWithoutSharedMatches()=render(ProfilePage.PROFILE,"android-individual-interests",mineOnly=true)
    @Test fun readOnlyPreview()=render(ProfilePage.PREVIEW,"android-profile-preview",populated=true)
    @Test fun populatedProfile()=render(ProfilePage.PROFILE,"android-owner-populated",populated=true)
    @Test fun answerSheet()=render(ProfilePage.ANSWER,"android-answer-sheet",populated=true)
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi") fun smallSongEditor()=render(ProfilePage.SONG,"android-song-small")
    @Test fun darkLargeText()=render(ProfilePage.SONG,"android-song-dark-large-text",dark=true,largeText=true)
}
