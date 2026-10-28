package com.srisu.srisu.features.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import com.srisu.srisu.features.chat.matrix.MatrixPartnerPresence
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomState
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatScreen
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.theme.SriSuTheme
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test

/**
 * Renders the secure chat screen inside the iOS simulator test host (Skia/Compose iOS runtime).
 * Output: <simulator tmp>/chat-ui-revist/ios-*.png; the path is printed for collection.
 * This is a Compose UI test render, not a launched app with a live backend.
 */
@OptIn(ExperimentalTestApi::class)
class SecureChatIosRenderTest {
    private companion object {
        const val SCALE = 2f
        const val WIDTH_PX = 390 * SCALE
        const val HEIGHT_PX = 844 * SCALE
    }

    private fun render(name: String, state: MatrixChatRoomState, theme: ChatThemePreset = ChatThemePreset.PURE, dark: Boolean = false, fontScale: Float = 1f, draft: String = "", width: Int = 390, height: Int = 844, partner: com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity = ChatFixtures.partner, openShare: Boolean = false, openAppearance: Boolean = false) =
        runSkikoComposeUiTest(size = Size(width * SCALE, height * SCALE), density = Density(SCALE, fontScale)) {
            com.srisu.srisu.features.couplechat.presentation.NativeChatMediaRuntime.installFactory { LayoutOnlyMedia() }
            val viewModel = SecureChatViewModel(ChatFixtureRepository(state), FixedPartnerSource(partner))
            viewModel.openRoom(state.roomId)
            viewModel.chatTheme(theme.id)
            if (draft.isNotEmpty()) viewModel.updateMessageInput(draft)
            setContent {
                Box(Modifier.size(width.dp, height.dp).testTag("screen")) {
                    SriSuTheme(darkTheme = dark) { SecureChatScreen(viewModel = viewModel, onBack = {}) }
                }
            }
            waitForIdle()
            state.messages.firstOrNull { it.attachment?.kind in setOf("photo", "video") }?.let { message ->
                val label = if (message.attachment?.kind == "video") "Video preview" else "Photo preview"
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
            }
            if (openShare) {
                onNodeWithContentDescription("Attach or share").performClick()
                waitForIdle()
                if (fontScale >= 1.3f) onNodeWithText("Plan").performScrollTo().assertIsDisplayed()
                else listOf("Photos", "Camera", "Video", "Voice", "Spark", "Challenge", "Plan").forEach { onNodeWithText(it).assertIsDisplayed() }
                onNodeWithContentDescription("Close share menu").assertIsDisplayed()
            }
            if (openAppearance) {
                onNodeWithContentDescription("Chat appearance").performClick()
                waitForIdle()
                onNodeWithTag("chat-theme-${theme.id}").assertIsSelected()
                if (fontScale >= 1.3f) onNodeWithTag("chat-theme-dawn").performScrollTo().assertIsDisplayed()
                else ChatThemePreset.entries.forEach { onNodeWithTag("chat-theme-${it.id}").assertIsDisplayed() }
                onNodeWithContentDescription("Close theme picker").assertIsDisplayed()
                onNodeWithText("Apply theme").assertIsDisplayed().assertIsNotEnabled()
            }
            val captureTag = when { openAppearance -> "chat-theme-picker"; openShare -> "chat-quick-share-sheet"; else -> "screen" }
            val bitmap = onNodeWithTag(captureTag).captureToImage().asSkiaBitmap()
            val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
            writePng(name, png)
            viewModel.closeRoom()
            viewModel.viewModelScope.cancel()
        }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun writePng(name: String, bytes: ByteArray) {
        val directory = NSTemporaryDirectory() + "chat-ui-revist"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        val path = "$directory/ios-$name.png"
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true)
        }
        println("CHAT_RENDER $path")
    }

    @Test fun themePickerPure() = render("theme-picker-pure", ChatFixtures.pure(), width = 393, height = 760, openAppearance = true)
    @Test fun themePickerDark() = render("theme-picker-dark", ChatFixtures.pure(), dark = true, theme = ChatThemePreset.IRIS, openAppearance = true)
    @Test fun themePickerSmall() = render("theme-picker-small", ChatFixtures.pure(), width = 320, height = 568, openAppearance = true)
    @Test fun themePickerSmallLargeText() = render("theme-picker-small-large-text", ChatFixtures.pure(), width = 320, height = 568, fontScale = 2f, openAppearance = true)

    @Test fun quickSharePure() = render("quick-share-pure", ChatFixtures.pure(), width = 393, height = 780, openShare = true)
    @Test fun quickShareDark() = render("quick-share-dark", ChatFixtures.pure(), dark = true, openShare = true)
    @Test fun quickShareSmallLargeText() = render("quick-share-small-large-text", ChatFixtures.pure(), width = 320, height = 568, fontScale = 2f, openShare = true)

    @Test fun activityPURELight() = render("activity-pure-light", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.PURE, dark = false)
    @Test fun activityPUREDark() = render("activity-pure-dark", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.PURE, dark = true)
    @Test fun activityORIGINALLight() = render("activity-original-light", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.ORIGINAL, dark = false)
    @Test fun activityORIGINALDark() = render("activity-original-dark", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.ORIGINAL, dark = true)
    @Test fun activityBLENDLight() = render("activity-blend-light", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.BLEND, dark = false)
    @Test fun activityBLENDDark() = render("activity-blend-dark", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.BLEND, dark = true)
    @Test fun activityCALMLight() = render("activity-calm-light", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.CALM, dark = false)
    @Test fun activityCALMDark() = render("activity-calm-dark", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.CALM, dark = true)
    @Test fun activityIRISLight() = render("activity-iris-light", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.IRIS, dark = false)
    @Test fun activityIRISDark() = render("activity-iris-dark", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.IRIS, dark = true)
    @Test fun activityDAWNLight() = render("activity-dawn-light", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.DAWN, dark = false)
    @Test fun activityDAWNDark() = render("activity-dawn-dark", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.ONLINE, partnerTyping = true), theme = ChatThemePreset.DAWN, dark = true)

    @Test fun pureReference() = render("pure-reference", ChatFixtures.pure(), width = 393, height = 800, partner = com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity("Aatithi", null))
    @Test fun pureSmall() = render("pure-small", ChatFixtures.pure(), width = 320, height = 568)
    @Test fun pureLargeText() = render("pure-large-text", ChatFixtures.pure(), fontScale = 1.6f)

    @Test fun empty() = render("01-empty", ChatFixtures.empty())
    @Test fun conversation() = render("02-conversation", ChatFixtures.conversation())
    @Test fun longAndGrouped() = render("03-long-grouped", ChatFixtures.longAndGrouped())
    @Test fun multilineDraft() = render("09-multiline-draft", ChatFixtures.conversation(), draft = ChatFixtures.MULTILINE_DRAFT)
    @Test fun deliveryStates() = render("10-pending-failed-unavailable", ChatFixtures.deliveryStates())
    @Test fun offline() = render("11-offline", ChatFixtures.offline())
    @Test fun largeText() = render("12-large-text", ChatFixtures.conversation(), fontScale = 1.6f)
    @Test fun dark() = render("13-dark", ChatFixtures.conversation(), dark = true)
    @Test fun photoVoiceAndReply() = render("14-restored-photo-voice-reply", ChatRecoveryFixtures.media())
    @Test fun video() = render("15-restored-video", ChatRecoveryFixtures.media(video = true))
    @Test fun darkMedia() = render("16-restored-media-dark", ChatRecoveryFixtures.media(), dark = true)
}

private class LayoutOnlyMedia : com.srisu.srisu.features.couplechat.presentation.NativeChatMedia {
    override fun safetyQr(text: String): ByteArray? = null
    override fun pickPhotos(limit:Int,result:(com.srisu.srisu.features.couplechat.presentation.NativePhotoBatch)->Unit)=Unit
    override fun pick(source: String, result: (String, ByteArray?) -> Unit) = Unit
    override fun startRecording(result: (String, ByteArray?) -> Unit) = Unit
    override fun stopRecording(discard: Boolean) = Unit
    override fun play(bytes: ByteArray, mime: String, result: (String) -> Unit) = Unit
    override fun playback(action: String, value: Double) = Unit
    override fun export(bytes: ByteArray, mime: String, result: (String) -> Unit) = Unit
    override fun interrupt() = Unit
    override fun close() = Unit
}
