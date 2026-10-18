package com.srisu.srisu.features.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
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

    private fun render(name: String, state: MatrixChatRoomState, dark: Boolean = false, fontScale: Float = 1f, draft: String = "") =
        runSkikoComposeUiTest(size = Size(WIDTH_PX, HEIGHT_PX), density = Density(SCALE, fontScale)) {
            val viewModel = SecureChatViewModel(ChatFixtureRepository(state), FixedPartnerSource(ChatFixtures.partner))
            viewModel.openRoom(state.roomId)
            if (draft.isNotEmpty()) viewModel.updateMessageInput(draft)
            setContent {
                Box(Modifier.size(390.dp, 844.dp).testTag("screen")) {
                    SriSuTheme(darkTheme = dark) { SecureChatScreen(viewModel = viewModel, onBack = {}) }
                }
            }
            waitForIdle()
            val bitmap = onNodeWithTag("screen").captureToImage().asSkiaBitmap()
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

    @Test fun empty() = render("01-empty", ChatFixtures.empty())
    @Test fun conversation() = render("02-conversation", ChatFixtures.conversation())
    @Test fun longAndGrouped() = render("03-long-grouped", ChatFixtures.longAndGrouped())
    @Test fun multilineDraft() = render("09-multiline-draft", ChatFixtures.conversation(), draft = ChatFixtures.MULTILINE_DRAFT)
    @Test fun deliveryStates() = render("10-pending-failed-unavailable", ChatFixtures.deliveryStates())
    @Test fun offline() = render("11-offline", ChatFixtures.offline())
    @Test fun largeText() = render("12-large-text", ChatFixtures.conversation(), fontScale = 1.6f)
    @Test fun dark() = render("13-dark", ChatFixtures.conversation(), dark = true)
}
