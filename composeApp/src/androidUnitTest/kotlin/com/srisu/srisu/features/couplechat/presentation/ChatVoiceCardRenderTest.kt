package com.srisu.srisu.features.couplechat.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.srisu.srisu.features.couplechat.data.AttachmentDescriptor
import com.srisu.srisu.features.couplechat.data.ChatMedia
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import com.srisu.srisu.theme.SriSuChatTheme
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.theme.chatColors
import com.srisu.srisu.theme.pill
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

/** Received and sent voice cards in isolation; PNGs go to build/reports/chat-voice as review evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalResourceApi::class)
class ChatVoiceCardRenderTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val levels = listOf(20, 45, 70, 35, 60, 90, 50, 30, 75, 55, 25, 65, 85, 40, 30, 55, 70, 35, 20, 50, 80, 45, 30, 60)
    private fun voice(waveform: List<Int>) =
        ChatMedia(AttachmentDescriptor("voice", "!room:test", "voice"), "voice", "audio/wav", durationMs = 18000, waveform = waveform)

    @Composable
    private fun Card(own: Boolean, content: @Composable (Modifier) -> Unit) {
        Surface(
            shape = MaterialTheme.shapes.pill,
            color = MaterialTheme.chatColors.incoming,
            contentColor = MaterialTheme.chatColors.text,
            modifier = Modifier.width(ChatMessageLayout.voiceWidth)
        ) {
            Column(
                (if (own) Modifier.background(MaterialTheme.chatColors.outgoing) else Modifier)
                    .padding(MaterialTheme.spacing.tiny).padding(end = MaterialTheme.spacing.small)
            ) { content(Modifier.fillMaxWidth()) }
        }
    }

    private fun render(name: String, dark: Boolean) {
        var speedClicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
            SriSuTheme(darkTheme = dark) {
                SriSuChatTheme(ChatThemePreset.ORIGINAL) {
                    var speed by remember { mutableFloatStateOf(1.5f) }
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        Card(own = false) {
                            InlineVoicePlayer(voice(levels), true, 3000, 18000, false, null, {}, {}, it,
                                speed = speed, onSpeed = { speedClicks++; speed = 2f })
                        }
                        Card(own = true) {
                            InlineVoicePlayer(voice(levels), false, 0, 0, false, null, {}, {}, it, onSpeed = {})
                        }
                        Card(own = false) {
                            InlineVoicePlayer(voice(emptyList()), false, 0, 0, false, null, {}, {}, it, onSpeed = {})
                        }
                        Card(own = true) {
                            InlineVoicePlayer(voice(emptyList()), false, 0, 0, false, "Couldn’t play this recording. Tap to retry.", {}, {}, it, onSpeed = {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Pause voice message").assertExists()
        compose.onNodeWithContentDescription("Seek voice message").assertExists()
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playback speed 1.5×")).performClick()
        compose.waitForIdle()
        assertEquals(1, speedClicks)
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playback speed 2×")).assertExists()
        compose.onNodeWithContentDescription("Retry playback").assertExists()
        val output = File("build/reports/chat-voice/$name.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun light() = render("voice-cards-light", dark = false)
    @Test fun dark() = render("voice-cards-dark", dark = true)

    @Test fun speedLabels() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("2×", speedLabel(2f))
    }
}
