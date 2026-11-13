package com.srisu.srisu.features.chat

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import com.srisu.srisu.features.chat.matrix.ChatPartnerIdentity
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import com.srisu.srisu.features.chat.matrix.MatrixPartnerPresence
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
        theme: ChatThemePreset = ChatThemePreset.PURE,
        dark: Boolean = false,
        fontScale: Float = 1f,
        draft: String = "",
        qualifiers: String? = null,
        partner: ChatPartnerIdentity? = ChatFixtures.partner,
        onOpenSpark: (String?) -> Unit = {},
        openShare: Boolean = false,
        openAppearance: Boolean = false,
        interact: (ChatFixtureRepository) -> Unit = {},
    ) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"))
        qualifiers?.let(RuntimeEnvironment::setQualifiers)
        // Dialogs read the platform configuration, not only the screen's LocalDensity override.
        RuntimeEnvironment.setFontScale(fontScale)
        val repository = ChatFixtureRepository(state)
        val store = ViewModelStore()
        val vm = SecureChatViewModel(repository, FixedPartnerSource(partner))
        store.put("chat", vm)
        vm.openRoom(state.roomId)
        vm.chatTheme(theme.id)
        if (draft.isNotEmpty()) vm.updateMessageInput(draft)
        try {
            compose.setContent {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                    org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
                }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    SriSuTheme(darkTheme = dark) { SecureChatScreen(viewModel = vm, onBack = {}, onOpenSparks = onOpenSpark) }
                }
            }
            compose.waitForIdle()
            state.messages.firstOrNull { it.attachment?.kind in setOf("photo", "video") }?.let { message ->
                val label = if (message.attachment?.kind == "video") "Video preview" else "Photo preview"
                compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
            }
            if (openShare) {
                compose.onNodeWithContentDescription("Attach or share").performClick()
                compose.waitForIdle()
            }
            if (openAppearance) {
                compose.onNodeWithContentDescription("Chat appearance").performClick()
                compose.waitForIdle()
            }
            interact(repository)
            compose.waitForIdle()
            val output = File("build/reports/chat-ui-revist/$name.png").apply { parentFile?.mkdirs() }
            check(compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty())
            compose.runOnIdle {
                val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                    ?: compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                if (view !== compose.activity.window.decorView) compose.activity.window.decorView.draw(canvas)
                view.draw(canvas)
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test fun themePickerPure() = render("theme-picker-pure", ChatFixtures.pure(), qualifiers = "w393dp-h760dp-xhdpi", openAppearance = true) {
        ChatThemePreset.entries.forEach { compose.onNodeWithTag("chat-theme-${it.id}").assertIsDisplayed() }
        compose.onNodeWithTag("chat-theme-pure").assertIsSelected()
        compose.onNodeWithText("Apply theme").assertIsNotEnabled()
    }
    @Test fun themePickerDark() = render("theme-picker-dark", ChatFixtures.pure(), dark = true, theme = ChatThemePreset.IRIS, openAppearance = true) {
        compose.onNodeWithTag("chat-theme-iris").assertIsSelected()
    }
    @Test fun themePickerSmall() = render("theme-picker-small", ChatFixtures.pure(), qualifiers = "w320dp-h568dp-xhdpi", openAppearance = true) {
        ChatThemePreset.entries.forEach { compose.onNodeWithTag("chat-theme-${it.id}").assertIsDisplayed() }
        compose.onNodeWithText("Apply theme").assertIsDisplayed()
    }
    @Test fun themePickerSmallLargeText() = render("theme-picker-small-large-text", ChatFixtures.pure(), qualifiers = "w320dp-h568dp-xhdpi", fontScale = 2f, openAppearance = true) {
        compose.onNodeWithTag("chat-theme-dawn").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("chat-theme-dawn").assertIsSelected()
        compose.onNodeWithText("Apply theme").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Close theme picker").assertIsDisplayed()
    }
    @Test fun themePickerCancelLeavesSavedThemeAndDraft() = render("theme-picker-cancel", ChatFixtures.pure(), theme = ChatThemePreset.CALM, draft = "Keep this draft", openAppearance = true) { repository ->
        compose.onNodeWithTag("chat-theme-iris").performClick().assertIsSelected()
        compose.onNodeWithText("Apply theme").assertIsEnabled()
        compose.onNodeWithContentDescription("Close theme picker").performClick()
        compose.onNodeWithContentDescription("Message").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Keep this draft")))
        compose.onNodeWithContentDescription("Chat appearance").performClick()
        compose.onNodeWithTag("chat-theme-calm").assertIsSelected()
        compose.onNodeWithText("Apply theme").assertIsNotEnabled()
        kotlin.test.assertTrue(repository.sent.isEmpty())
    }
    @Test fun themePickerAppliesChosenIdAndReopensSelected() = render("theme-picker-applied", ChatFixtures.pure(), theme = ChatThemePreset.ORIGINAL, draft = "Keep this draft", openAppearance = true) { repository ->
        compose.onNodeWithTag("chat-theme-dawn").performClick()
        compose.onNodeWithText("Apply theme").performClick()
        compose.onNodeWithTag("chat-theme-picker").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Keep this draft")))
        compose.onNodeWithContentDescription("Chat appearance").performClick()
        compose.onNodeWithTag("chat-theme-dawn").assertIsSelected()
        compose.onNodeWithText("Apply theme").assertIsNotEnabled()
        kotlin.test.assertTrue(repository.sent.isEmpty())
    }

    @Test fun quickSharePure() = render("quick-share-pure", ChatFixtures.pure(), qualifiers = "w393dp-h780dp-xhdpi", openShare = true) {
        listOf("Photos", "Camera", "Video", "Voice", "Spark", "Challenge", "Plan").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Ask").assertDoesNotExist()
        compose.onNodeWithText("Poll").assertDoesNotExist()
    }
    @Test fun quickShareDark() = render("quick-share-dark", ChatFixtures.pure(), dark = true, openShare = true)
    @Test fun quickShareIris() = render("quick-share-iris", ChatFixtures.pure(), theme = ChatThemePreset.IRIS, openShare = true)
    @Test fun quickShareSmallLargeText() = render("quick-share-small-large-text", ChatFixtures.pure(),
        qualifiers = "w320dp-h568dp-xhdpi", fontScale = 2f, openShare = true) {
        compose.onNodeWithText("Plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close share menu").assertIsDisplayed()
    }
    @Test fun quickShareDismissPreservesDraft() = render("quick-share-dismiss", ChatFixtures.pure(), draft = "Keep this draft", openShare = true) {
        compose.onNodeWithContentDescription("Close share menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Message").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Keep this draft")))
        compose.onNodeWithContentDescription("Attach or share").performClick()
        compose.onNodeWithText("Photos").assertIsDisplayed()
    }

    @Test fun typingDoesNotPullReaderOutOfHistory() = render("activity-history", ChatFixtures.longHistory()) { repository ->
        val message = "Message 8"
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(androidx.compose.ui.test.hasText(message))
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { repository.state.value = repository.state.value.copy(partnerTyping = true) }
        compose.waitForIdle()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { repository.state.value = repository.state.value.copy(partnerTyping = false) }
        compose.waitForIdle()
        compose.onNodeWithText(message).assertIsDisplayed()
    }
    @Test fun partnerTypingAppearsAndClearsWithoutBecomingAMessage() = render("activity-typing-cleared", ChatFixtures.empty()) { repository ->
        compose.runOnIdle { repository.state.value = repository.state.value.copy(partnerTyping = true) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Aatithi Rai is typing").assertIsDisplayed()
        compose.runOnIdle { repository.state.value = repository.state.value.copy(partnerTyping = false) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Aatithi Rai is typing").assertDoesNotExist()
        kotlin.test.assertTrue(repository.state.value.messages.isEmpty())
    }

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
    @Test fun offlinePartnerLabel() = render("activity-partner-offline", ChatFixtures.pure().copy(partnerPresence = MatrixPartnerPresence.OFFLINE)) {
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("End-to-end encrypted").assertDoesNotExist()
    }
    @Test fun typingInEmptyRoom() = render("activity-empty-typing", ChatFixtures.empty().copy(partnerTyping = true)) {
        compose.onNodeWithContentDescription("Aatithi Rai is typing").assertIsDisplayed()
    }
    @Test fun typingSmallLargeText() = render("activity-small-large-text", ChatFixtures.pure().copy(partnerTyping = true, partnerPresence = MatrixPartnerPresence.ONLINE), theme = ChatThemePreset.IRIS, fontScale = 2f, qualifiers = "w320dp-h568dp-xhdpi") {
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
    }

    @Test fun pureReference() = render("pure-reference", ChatFixtures.pure(), qualifiers = "w393dp-h800dp-xhdpi", partner = ChatPartnerIdentity("Aatithi", null))
    @Test fun pureSparkKeepsExistingNavigation() {
        val opened = mutableListOf<String?>()
        render("pure-spark-action", ChatFixtures.pure(), onOpenSpark = { opened += it }) {
            compose.onNodeWithText("Open Spark").performClick()
            kotlin.test.assertEquals<String?>("11111111-1111-4111-8111-111111111111", opened.single())
        }
    }
    @Test fun pureSmall() = render("pure-small", ChatFixtures.pure(), qualifiers = "w320dp-h568dp-xhdpi", partner = ChatPartnerIdentity("Aatithi", null))
    @Test fun pureLargeText() = render("pure-large-text", ChatFixtures.pure(), fontScale = 1.6f, partner = ChatPartnerIdentity("Aatithi", null))
    @Test fun pureLongName() = render("pure-long-name", ChatFixtures.pure(), fontScale = 2f, partner = ChatPartnerIdentity("A very long partner name", null))
    /** Available layout height with a keyboard; this does not emulate a native IME transition. */
    @Test fun pureConstrainedComposer() = render("pure-constrained-composer", ChatFixtures.pure(),
        draft = ChatFixtures.MULTILINE_DRAFT, qualifiers = "w393dp-h440dp-xhdpi") {
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun pureDark() = render("pure-dark", ChatFixtures.pure(), dark = true, partner = ChatPartnerIdentity("Aatithi", null))

    @Test fun restoredMedia() = render("14-restored-photo-voice-reply", ChatRecoveryFixtures.media())
    @Test fun emptyConversationCanComposeAndSend() = render("17-empty-composing", ChatFixtures.empty()) {
        compose.onNodeWithContentDescription("Message").performTextInput("Our first message")
        compose.onNodeWithContentDescription("Send message").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Message").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
    @Test fun restoredVideo() = render("15-restored-video", ChatRecoveryFixtures.media(video = true))
    @Test fun restoredMediaDark() = render("16-restored-media-dark", ChatRecoveryFixtures.media(), dark = true)
    @Test fun empty() = render("01-empty", ChatFixtures.empty())
    @Test fun conversation() = render("02-conversation", ChatFixtures.conversation())
    @Test fun loadFadeMidpoint() {
        compose.mainClock.autoAdvance = false
        render("18-load-fade-midpoint", ChatFixtures.conversation()) { compose.mainClock.advanceTimeBy(80) }
    }
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
