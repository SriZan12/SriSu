package com.srisu.srisu.features.couplechat.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatSurfaceTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Composable private fun bubble(message:LocalChatMessage) = MessageBubble(message,message.authorId==1L,"Synthetic partner",1,false,
        onMenu={},onReply={},onOriginal={},onRetry={},onMedia={},uploadProgress=null,offlineMedia=emptySet())
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun render(dark:Boolean=false,large:Boolean=false,content:@Composable ()->Unit) {
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {org.jetbrains.compose.resources.PreviewContextConfigurationEffect()}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.8f else 1f)) {
                SriSuTheme(darkTheme=dark) {Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {content()}
                }}
            }
        }
    }
    private fun screenshot(name:String) {
        val output=File("build/reports/chat-ui/$name.png");output.parentFile?.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {
                view.draw(android.graphics.Canvas(bitmap))
                output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            } finally {bitmap.recycle()}
        }
    }
    @Test fun unicodeAndReplyWrapAtLargeTextWithoutHorizontalOverflow() {
        val text="नमस्ते 👋 A quiet thought for the two of us, with enough words to wrap across this small screen."
        render(dark=true,large=true) {bubble(LocalChatMessage("one","room",1,"2026-10-10T10:00:00Z",
            content=ChatContent.Text(text,ChatQuote("previous",2,"An earlier thought")),transport=ChatTransport.QUEUED,timelineKey="synthetic"))}
        compose.onNodeWithText(text).assertExists()
        val root=compose.onRoot().getUnclippedBoundsInRoot()
        val bounds=compose.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertTrue(bounds.left>=root.left);assertTrue(bounds.right<=root.right)
        compose.onNodeWithText("Queued").assertExists()
        screenshot("large-type-dark-reply")
    }
    @Test fun deletionPlaceholderExcludesBodyAndReplyPreviewFromAccessibility() {
        render {bubble(LocalChatMessage("one","room",2,"2026-10-10T10:00:00Z",deleted=true,
            content=ChatContent.Text("Synthetic erased body",ChatQuote("previous",1,"Synthetic erased quote")),timelineKey="synthetic"))}
        compose.onNodeWithText("This message was deleted").assertIsDisplayed()
        compose.onNodeWithText("Synthetic erased body").assertDoesNotExist()
        compose.onNodeWithText("Synthetic erased quote").assertDoesNotExist()
        screenshot("deleted-message-light")
    }
    @Suppress("DEPRECATION")
    @Test fun bothComposeClipboardInterfacesMarkPrivateCopiesSensitive() {
        render {PrivateChatClipboard {
            val clipboard=LocalClipboard.current;val legacy=LocalClipboardManager.current;val scope=rememberCoroutineScope()
            Button(onClick={legacy.setText(AnnotatedString("Synthetic selected text"))}) {Text("Copy legacy")}
            Button(onClick={scope.launch {clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("public","Synthetic selected text")))}}) {Text("Copy selection")}
        }}
        val native=compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        for(label in listOf("Copy legacy","Copy selection")) {
            compose.onNodeWithText(label).performClick();compose.waitForIdle()
            assertEquals("SriSu private message",native.primaryClip!!.description.label)
            assertTrue(native.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
    }
}
