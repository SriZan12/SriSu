package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.features.chat.ChatFixtureRepository
import com.srisu.srisu.features.chat.ChatFixtures
import com.srisu.srisu.features.chat.FixedPartnerSource
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import com.srisu.srisu.theme.SriSuChatTheme
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercise the actual sheet → controller/navigation boundary without starting native media. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatAttachmentSheetTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun routesSevenActionsOnceAndDismissesBeforeHandoff() {
        val state = ChatFixtures.pure()
        val vm = SecureChatViewModel(ChatFixtureRepository(state), FixedPartnerSource(ChatFixtures.partner))
        val store = ViewModelStore().apply { put("chat", vm) }
        vm.openRoom(state.roomId)
        val scope = MainScope()
        var shown by mutableStateOf(true)
        val events = mutableListOf<String>()
        val native = ShareMediaFake { destination ->
            assertFalse(shown, "Dismiss the sheet before handing off to a native action")
            events += destination
        }
        val controller = ChatMediaController(native, vm, scope)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
                SriSuTheme {
                    SriSuChatTheme(ChatThemePreset.PURE) {
                        if (shown) ChatAttachmentSheet(controller,
                            onDismiss = { shown = false; events += "dismiss" },
                            onSpark = { assertFalse(shown); events += "spark" },
                            onChallenge = { assertFalse(shown); events += "challenge" },
                            onPlan = { assertFalse(shown); events += "plan" })
                    }
                }
            }
            listOf("Photos" to "photo", "Camera" to "camera", "Video" to "video", "Voice" to "voice",
                "Spark" to "spark", "Challenge" to "challenge", "Plan" to "plan").forEach { (label, destination) ->
                compose.runOnIdle { shown = true }
                compose.onNodeWithText(label).performClick()
                compose.waitForIdle()
                assertEquals(listOf("dismiss", destination), events.toList())
                events.clear()
                if (destination == "camera") {
                    assertTrue(vm.uiState.value.error.orEmpty().contains("access is off"))
                    vm.clearError()
                }
                assertFalse(controller.preparing, "Cancelled or denied media must release preparation state")
            }
        } finally {
            compose.runOnIdle { controller.close(); store.clear(); scope.cancel() }
        }
    }
}

private class ShareMediaFake(private val onAction: (String) -> Unit) : NativeChatMedia {
    override fun pick(source: String, result: (String, ByteArray?) -> Unit) {
        onAction(source)
        result(if (source == "camera") """{"event":"error","code":"permission"}""" else """{"event":"cancelled"}""", null)
    }
    override fun startRecording(result: (String, ByteArray?) -> Unit) {
        onAction("voice")
        result("""{"event":"cancelled"}""", null)
    }
    override fun safetyQr(text: String): ByteArray? = null
    override fun pickPhotos(limit: Int, result: (NativePhotoBatch) -> Unit) = Unit
    override fun stopRecording(discard: Boolean) = Unit
    override fun play(bytes: ByteArray, mime: String, result: (String) -> Unit) = Unit
    override fun playback(action: String, value: Double) = Unit
    override fun export(bytes: ByteArray, mime: String, result: (String) -> Unit) = Unit
    override fun interrupt() = Unit
    override fun close() = Unit
}
