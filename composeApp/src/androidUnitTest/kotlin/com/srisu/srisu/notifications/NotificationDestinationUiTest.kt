package com.srisu.srisu.notifications

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.features.moments.*
import com.srisu.srisu.features.moments.data.*
import com.srisu.srisu.features.moments.presentation.*
import com.srisu.srisu.features.plans.data.*
import com.srisu.srisu.features.plans.presentation.*
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationDestinationUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            SriSuTheme { Surface { content() } }
        }
    }
    @Test fun appreciationNotificationShowsExactThreadAndVisitorsCannotReply() {
        val source=object:FakeMoments() {
            override suspend fun noteDetail(id:Long):MomentResult<MomentNote> = NetworkAPIResult.Success(
                MomentNote(id,"Such a lovely afternoon!",replies=listOf(MomentNoteReply(1,"Thank you for the kind words."))))
        }
        val vm=MomentNoteViewModel(source,MomentFixtures.sessions())
        try {
            show { MomentNoteScreen(7,{},vm) }
            compose.waitUntil(10000) { ShadowLooper.idleMainLooper(); vm.state.value.note!=null }
            compose.onNodeWithText("Such a lovely afternoon!").assertIsDisplayed()
            compose.onNodeWithText("Thank you for the kind words.").assertIsDisplayed()
            compose.onNodeWithText("Send reply").assertDoesNotExist()
            capture("appreciation-thread")
        } finally {compose.runOnIdle {vm.viewModelScope.cancel()} }
    }
    @Test fun ideaNotificationReadsExactIdeaAndRequiresAnExplicitChoiceBeforePlanning() {
        val id="11111111-1111-4111-8111-111111111111"
        val idea=DateIdea(id,3,2,"coffee",listOf(1,2),"open",1,MomentFixtures.NOW)
        var chosen: String?=null
        val source=object:DateIdeaSource {
            override suspend fun get(id:String):NetworkAPIResult<DateIdea?> = NetworkAPIResult.Success(idea)
            override suspend fun list(couple:Long):NetworkAPIResult<DateIdeas?> = error("No implicit list")
            override suspend fun create(couple:Long,preset:String,requestId:String):NetworkAPIResult<DateIdea?> = error("No implicit create")
            override suspend fun act(id:String,revision:Long,requestId:String,action:String,values:JsonObject):NetworkAPIResult<DateIdea?> = error("No implicit action")
        }
        val vm=DateIdeaViewModel(source,MomentFixtures.sessions())
        try {
            show { DateIdeaScreen(id,{},{},{chosen=it},vm) }
            compose.waitUntil(10000) { ShadowLooper.idleMainLooper(); vm.state.value.idea!=null }
            compose.onNodeWithText("Coffee together").assertIsDisplayed()
            org.junit.Assert.assertNull(chosen)
            capture("shared-idea")
            compose.onNodeWithText("Choose a time").performClick()
            org.junit.Assert.assertEquals(id,chosen)
        } finally {compose.runOnIdle {vm.viewModelScope.cancel()} }
    }
    private fun capture(name:String)=compose.runOnIdle {
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=File("build/reports/notifications/$name.png");file.parentFile?.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
