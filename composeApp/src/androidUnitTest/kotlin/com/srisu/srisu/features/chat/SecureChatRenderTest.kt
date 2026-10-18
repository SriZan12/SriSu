package com.srisu.srisu.features.chat

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.chat.matrix.MatrixChatRoomState
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatScreen
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import com.srisu.srisu.theme.SriSuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * Deterministic screenshot renders of the secure chat screen. Images are written to
 * build/reports/chat-ui-revist and are review evidence, not golden-image assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecureChatRenderTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun render(
        name: String,
        state: MatrixChatRoomState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        draft: String = "",
        qualifiers: String? = null,
        partner: ChatPartnerIdentity? = ChatFixtures.partner,
        interact: () -> Unit = {},
    ) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"))
        qualifiers?.let(RuntimeEnvironment::setQualifiers)
        val repository = ChatFixtureRepository(state)
        val store = ViewModelStore()
        val vm = SecureChatViewModel(repository, FixedPartnerSource(partner))
        store.put("chat", vm)
        vm.openRoom(state.roomId)
        if (draft.isNotEmpty()) vm.updateMessageInput(draft)
        try {
            compose.setContent {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                    org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
                }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    SriSuTheme(darkTheme = dark) { SecureChatScreen(viewModel = vm, onBack = {}) }
                }
            }
            compose.waitForIdle()
            interact()
            compose.waitForIdle()
            val output = File("build/reports/chat-ui-revist/$name.png").apply { parentFile?.mkdirs() }
            check(compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty())
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test fun empty() = render("01-empty", ChatFixtures.empty())
    @Test fun conversation() = render("02-conversation", ChatFixtures.conversation())
    @Test fun longAndGrouped() = render("03-long-grouped", ChatFixtures.longAndGrouped())
    @Test fun deliveryStates() = render("10-pending-failed-unavailable", ChatFixtures.deliveryStates())
    @Test fun multilineDraft() = render("09-multiline-draft", ChatFixtures.conversation(), draft = ChatFixtures.MULTILINE_DRAFT)
    @Test fun offline() = render("11-offline", ChatFixtures.offline())
    @Test fun loading() = render("11-loading", ChatFixtures.loading())
    @Test fun error() = render("11-error", ChatFixtures.error())
    @Test fun unavailableRoom() = render("11-room-unavailable", ChatFixtures.unavailableRoom())
    @Test fun smallScreen() = render("12-small-screen", ChatFixtures.longAndGrouped(), qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun largeText() = render("12-large-text", ChatFixtures.conversation(), fontScale = 1.6f)
    @Test fun dark() = render("13-dark", ChatFixtures.conversation(), dark = true)
    @Test fun noPartnerIdentity() = render("02-conversation-no-partner-identity", ChatFixtures.conversation(), partner = null)
    @Test fun scrolledHistory() = render("11-scrolled-history-jump", ChatFixtures.longHistory()) {
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(25)
    }
    @Test fun darkEmpty() = render("13-dark-empty", ChatFixtures.empty(), dark = true)
    @Test fun darkDeliveryStates() = render("13-dark-delivery-states", ChatFixtures.deliveryStates(), dark = true)
}
