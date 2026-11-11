package com.srisu.srisu.features.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.srisu.srisu.features.chat.matrix.MatrixChatAvailability
import com.srisu.srisu.features.coupleprofile.data.CoupleStatuses
import com.srisu.srisu.features.home.dashboard.screen.*
import com.srisu.srisu.features.home.dashboard.state.*
import com.srisu.srisu.features.plans.data.DateIdeas
import com.srisu.srisu.features.sparks.data.*
import com.srisu.srisu.navigation.navhost.*
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Installed Android rendering, scrolling, touch and system-back checks. No network/account fixture. */
class HomeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val me = HomePersonUi(1,"Srijan",null)
    private val partner = HomePersonUi(2,"Aatithi",null)
    private val question = SparkSession("11111111-1111-4111-8111-111111111111",10,1,
        "22222222-2222-4222-8222-222222222222","!synthetic:example.test",1,"deep-7-v1",
        "What made you feel close to me this week?","deep","open",1)
    private val paired = HomeUiState(HomeStatus.READY,name="Srijan",couple=HomeCoupleUi(
        10,partner,248,listOf(HomeMomentUi(me,true,0,0),HomeMomentUi(partner,false,0,0)),true,
        null,HomeSection.Empty,HomeSection.Empty,0,title="Lovebirds",account=1,
        question=HomeSection.Ready(SparkDaily(question,"2026-11-09","Asia/Kathmandu")),questionFresh=true,
        statuses=CoupleStatuses(10,serverTime="2026-11-09T06:00:00Z"),statusesFresh=true,
        ideas=HomeSection.Ready(DateIdeas(10)),ideasFresh=true,planFresh=true,
        questionsDone=0,plansDone=0,chatAvailability=MatrixChatAvailability.Ready(),unread=false,
        statusState=HomeStatus.READY,sharingRequest=HomeSection.Ready(false)))
    private fun show(state:HomeUiState=paired,scale:Float=1f) {
        compose.setContent {
            val d=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density,scale)) {
                SriSuTheme(darkTheme=false) {
                    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={AppBottomBar({},BottomDestination.HOME,true)}) {padding ->
                        HomeContent(state,{null},{events+="retry"},HomeActions(
                            onCreateMoment={events+="moment"},onQuestion={events+="question"},
                            onChat={events+="chat"},onFindPartner={events+="find"}),Modifier.padding(padding).consumeWindowInsets(padding))
                    }
                }
            }
        }
    }
    private fun scroll(label:String)=compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))

    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val file=File(instrumentation.targetContext.filesDir,"home-device-$name.png")
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }

    @Test fun independentActionsAndStatusSheetSystemBack() {
        show();capture("paired");compose.onNodeWithContentDescription("Add a Moment").performClick()
        scroll("Go first");compose.onNodeWithText("Go first").performClick()
        scroll("Open chat →");compose.onNodeWithText("Open chat →").performClick()
        assertEquals(listOf("moment","question","chat"),events)
        scroll("Set status");compose.onNodeWithText("Set status").performClick()
        compose.onNodeWithText("Feeling cosy").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);compose.onNodeWithText("Feeling cosy").assertDoesNotExist()
        compose.onNodeWithText("Set status").assertIsDisplayed()
    }
    @Test fun twoHundredPercentTextCanReachTheBottom() {
        show(scale=2f)
        scroll("Go first");compose.onNodeWithText("Go first").assertIsDisplayed()
        scroll("Plans done");compose.onNodeWithText("Plans done").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed();compose.onNodeWithText("Profile").assertIsDisplayed();capture("large-text-bottom")
    }
    @Test fun unpairedDoesNotInventPartnerActivity() {
        show(HomeUiState(HomeStatus.READY,name="Srijan"))
        capture("unpaired");compose.onNodeWithText("Find your partner").performClick()
        compose.onNodeWithText("Your story so far").assertDoesNotExist()
        compose.onNodeWithText("Go first").assertDoesNotExist();assertEquals(listOf("find"),events)
    }
    @Test fun failureOffersRetryRatherThanFalseEmptyState() {
        show(HomeUiState(HomeStatus.FAILED));compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Find your partner").assertDoesNotExist();assertEquals(listOf("retry"),events)
    }
}
