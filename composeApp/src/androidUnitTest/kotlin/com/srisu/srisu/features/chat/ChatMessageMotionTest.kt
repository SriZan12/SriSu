package com.srisu.srisu.features.chat

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.chat.matrix.MatrixChatMessage
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import com.srisu.srisu.features.chat.matrix.presentation.chatMessageEntrance
import com.srisu.srisu.features.chat.matrix.presentation.rememberChatLoadAlpha
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatMessageMotionTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun loadFadeFinishesWithoutRecomposingOrMeasuringContentEveryFrame() {
        lateinit var alpha: State<Float>
        var compositions = 0
        var measurements = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            alpha = rememberChatLoadAlpha(true)
            SideEffect { compositions++ }
            Box(Modifier.size(100.dp).graphicsLayer {
                this.alpha = alpha.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }.layout { measurable, constraints ->
                measurements++
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }.background(Color.Black)) // theme-audit: image pixels — synthetic luminance probe, not product colors
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val originalCompositions = compositions
        val originalMeasurements = measurements
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertTrue(alpha.value > 0f && alpha.value < 1f) }
        compose.mainClock.advanceTimeBy(192)
        compose.runOnIdle {
            assertEquals(1f, alpha.value, 0f)
            assertEquals(originalCompositions, compositions)
            assertEquals(originalMeasurements, measurements)
        }
    }

    @Test fun reducedMotionIsVisibleImmediatelyAndReenablingDoesNotReplayHistory() {
        val enabled = mutableStateOf(false)
        lateinit var alpha: State<Float>
        compose.mainClock.autoAdvance = false
        compose.setContent { alpha = rememberChatLoadAlpha(enabled.value) }
        compose.runOnIdle { assertEquals(1f, alpha.value, 0f); enabled.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(1f, alpha.value, 0f) }
    }

    @Test fun disablingMotionDuringTheRevealImmediatelyFinishesIt() {
        val enabled = mutableStateOf(true)
        lateinit var alpha: State<Float>
        compose.mainClock.autoAdvance = false
        compose.setContent { alpha = rememberChatLoadAlpha(enabled.value) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(1f, alpha.value, 0f) }
    }

    private fun message(id: String, transaction: String? = null) = MatrixChatMessage(
        id, "Synthetic message", "@synthetic:test", true, 1, MatrixMessageDelivery.SENDING,
        transactionId = transaction,
    )

    @Test fun sentRowFadesOnceAndAcknowledgementDoesNotRestartIt() {
        val messages = mutableStateOf(listOf(message("existing")))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) { // theme-audit: image pixels — synthetic luminance probe, not product colors
                LazyColumn(Modifier.size(300.dp), reverseLayout = true) {
                    items(messages.value, key = { it.stableKey }) { message ->
                        Box(chatMessageEntrance(true).fillMaxWidth().height(48.dp)
                            .background(Color.Black).testTag(message.stableKey)) // theme-audit: image pixels — synthetic luminance probe, not product colors
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { messages.value = listOf(message("txn:local", "local")) + messages.value }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        val halfway = brightness("txn:local")
        assertTrue("Expected a partially faded row, got $halfway", halfway in 1..254)
        compose.runOnIdle {
            messages.value = messages.value.map { if (it.transactionId == "local") it.copy(id = "server-id", delivery = MatrixMessageDelivery.SENT, read = true) else it }
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Acknowledgement must continue, not restart, the fade", brightness("txn:local") <= halfway)
        compose.mainClock.advanceTimeBy(160)
        assertEquals(0, brightness("txn:local"))
    }

    @Test fun paginationAndMetadataUpdatesKeepTheReadingAnchor() {
        val messages = mutableStateOf((0..39).map { message("row-$it") })
        lateinit var list: LazyListState
        compose.setContent {
            list = rememberLazyListState()
            LazyColumn(Modifier.size(300.dp), state = list, reverseLayout = true) {
                items(messages.value, key = { it.stableKey }) { message ->
                    Box(chatMessageEntrance(true).fillMaxWidth().height(48.dp).testTag(message.stableKey))
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
        val anchor = list.layoutInfo.visibleItemsInfo.first().key
        val offset = list.firstVisibleItemScrollOffset
        compose.runOnIdle {
            messages.value = listOf(message("new-arrival")) + messages.value.map { it.copy(read = true) } + (40..99).map { message("row-$it") }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(anchor, list.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(offset, list.firstVisibleItemScrollOffset)
        }
    }

    private fun brightness(tag: String): Int {
        compose.waitForIdle()
        val center = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
        var result = -1
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            result = android.graphics.Color.red(bitmap.getPixel(center.x.toInt(), center.y.toInt())) // theme-audit: image pixels — synthetic luminance probe, not product colors
            bitmap.recycle()
        }
        return result
    }
}
