@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.material.icons.outlined.CalendarMonth

import androidx.compose.material.icons.outlined.AutoAwesome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
import com.srisu.srisu.features.chat.matrix.presentation.SecureChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

@Composable
internal fun privatePhotoBitmap(bytes: ByteArray?): ImageBitmap? {
    return key(bytes) {
        val image by produceState<ImageBitmap?>(null) {
            value = withContext(Dispatchers.Default) { bytes?.let { decodePrivatePhoto(it) } }
        }
        image
    }
}

@Composable
internal fun PrivatePhoto(
    bytes: ByteArray?,
    description: String,
    modifier: Modifier = Modifier,
    fit: Boolean = false
) {
    val image = privatePhotoBitmap(bytes)
    image?.let {
        Image(
            it,
            description,
            modifier,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop
        )
    }
        ?: PhotoUnavailable(modifier)
}

@Composable
private fun PhotoUnavailable(modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Icon(ChatIcons.Image, null, Modifier.size(28.dp))
            Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun thumbnail(media: ChatMedia): ByteArray? = try {
    media.thumbnail?.takeIf { it.isNotBlank() && it.length <= 1024 * 1024 }?.let(Base64::decode)
} catch (_: Exception) {
    null
}

/** Thumbnails come from the encrypted body, never from a public media URL. */
@Composable
internal fun MediaPreview(
    media: ChatMedia, onOpen: () -> Unit, modifier: Modifier = Modifier,
    savedOffline: Boolean = false, loading: Boolean = false, error: String? = null,
    edgeToEdge: Boolean = false, showDownloadBadge: Boolean = true
) {
    if (media.kind == "voice") {
        InlineVoicePlayer(media, false, 0, 0, loading, error, onOpen, {}, modifier)
        return
    }
    val bytes = remember(media.thumbnail) { thumbnail(media) }
    // Keep geometry independent of decode completion, and distinguish waiting
    // from a corrupt/missing thumbnail instead of flashing an unavailable label.
    val decoded by key(bytes) {
        produceState<Pair<Boolean, ImageBitmap?>>(false to null) {
            value =
                true to withContext(Dispatchers.Default) { bytes?.let { decodePrivatePhoto(it) } }
        }
    }
    val preview = decoded.second
    val aspect =
        if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceIn(
            .85f,
            1.8f
        ) else 1.4f
    Column(
        modifier.clip(if (edgeToEdge) androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.small)
            .clickable(
                onClickLabel = if (error != null) "Retry attachment" else "Open ${media.kind}",
                onClick = onOpen
            )
            .semantics {
                stateDescription = when {
                    error != null -> error; loading -> "Opening attachment"; !decoded.first -> "Loading preview"; !savedOffline -> "Downloads when opened"; else -> "Available offline"
                }
            }) {
        Box(
            Modifier.fillMaxWidth().then(Modifier.aspectRatio(aspect))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (preview != null) Image(
                preview, if (media.kind == "video") "Video preview" else "Photo preview",
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            if (decoded.first && preview == null && !loading) Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                if (media.kind != "video") Icon(ChatIcons.Image, null, Modifier.size(28.dp))
                else Spacer(Modifier.height(48.dp))
                Text(
                    "Preview unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!decoded.first && !loading) CircularProgressIndicator(
                Modifier.align(Alignment.Center)
                    .size(24.dp)
                    .semantics { contentDescription = "Loading preview" }, strokeWidth = 2.dp
            )
            if (media.kind == "video" || loading) Surface(
                shape = MaterialTheme.shapes.pill,
                color = MaterialTheme.colorScheme.mediaBackground.copy(alpha = .75f),
                contentColor = MaterialTheme.colorScheme.onMedia,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(
                        Modifier.size(24.dp)
                            .semantics { contentDescription = "Opening attachment" },
                        color = MaterialTheme.colorScheme.onMedia,
                        strokeWidth = 2.dp
                    )
                    else Icon(Icons.Outlined.PlayArrow, "Play video")
                }
            }
            if (media.kind == "video" && media.durationMs > 0 || showDownloadBadge && !savedOffline) Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.mediaBackground.copy(alpha = .75f),
                contentColor = MaterialTheme.colorScheme.onMedia,
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(MaterialTheme.spacing.small)
            ) {
                Row(
                    Modifier.padding(
                        horizontal = MaterialTheme.spacing.small,
                        vertical = MaterialTheme.spacing.tiny
                    ), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)
                ) {
                    if (showDownloadBadge && !savedOffline) Icon(
                        ChatIcons.Download,
                        "Download to open",
                        Modifier.size(16.dp)
                    )
                    if (media.kind == "video" && media.durationMs > 0) Text(
                        mediaTime(media.durationMs),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        error?.let {
            Text(
                it,
                Modifier.padding(MaterialTheme.spacing.small),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Only the active bubble reads ticking playback state; the list never observes position. */
@Composable
internal fun InlineVoiceMessage(
    messageId: String,
    media: ChatMedia,
    controller: ChatMediaController,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    draft: Boolean = false
) {
    val active =
        controller.player.activeMessageId == messageId && controller.player.activeAttachmentId == media.descriptor.id
    InlineVoicePlayer(
        media,
        active && controller.player.playing,
        if (active) controller.player.position else 0,
        if (active) controller.player.duration else 0,
        loading || active && controller.player.playbackLoading,
        error ?: if (active) controller.player.playbackError else null,
        { controller.openVoice(messageId, media.descriptor.id, draft) },
        controller.player::seek,
        modifier,
        speed = controller.player.speed,
        onSpeed = controller.player::cycleSpeed
    )
}

/** Compact voice card geometry; the play disc keeps a 48dp touch target around its visual size. */
internal object ChatVoiceLayout {
    val playButton = 44.dp
    val playIcon = 22.dp
    val loader = 18.dp
    val track = 32.dp
    val bar = 3.dp
    val barGap = 3.dp
    val barMin = 4.dp
    val thumb = DpSize(4.dp, 24.dp)
    const val placeholderLevel = 40
}

internal fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

@Composable
internal fun InlineVoicePlayer(
    media: ChatMedia,
    playing: Boolean,
    position: Long,
    duration: Long,
    loading: Boolean,
    error: String?,
    onPlay: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    onSpeed: (() -> Unit)? = null
) {
    val voiceInk = LocalContentColor.current
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val canSeek = duration > 0 && !loading && error == null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledIconButton(
                onClick = onPlay, enabled = !loading, modifier = Modifier.size(ChatVoiceLayout.playButton),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = voiceInk.copy(alpha = 0f),
                    contentColor = voiceInk,
                    disabledContainerColor = voiceInk.copy(alpha = 0f),
                    disabledContentColor = voiceInk
                )
            ) {
                if (loading) CircularProgressIndicator(
                    Modifier.size(ChatVoiceLayout.loader).semantics { contentDescription = "Opening voice message" },
                    strokeWidth = 2.dp,
                    color = voiceInk
                )
                else Icon(
                    if (error != null) Icons.Outlined.Refresh else if (playing) ChatIcons.Pause else PureChatIcons.Play,
                    if (error != null) "Retry playback" else if (playing) "Pause voice message" else "Play voice message",
                    Modifier.size(18.dp)
                )
            }
            Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                // Old events without levels use uniform low bars; never invent an audio waveform.
                VoiceBars(
                    media.waveform.ifEmpty { listOf(ChatVoiceLayout.placeholderLevel) },
                    fraction,
                    Modifier.fillMaxSize().clearAndSetSemantics {}
                )
                if (canSeek) Slider(
                    value = fraction, onValueChange = onSeek,
                    // Bars already show progress; the invisible thumb keeps drag-to-seek.
                    thumb = { Box(Modifier.size(ChatVoiceLayout.thumb)) },
                    track = {},
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "Seek voice message" })
            }
            Text(
                if (loading) "…" else if (duration > 0) mediaTime(if (playing || position > 0) position else duration)
                else if (media.durationMs > 0) mediaTime(media.durationMs) else "0:00",
                modifier = if (onSpeed != null) Modifier.clickable(onClickLabel = "Change playback speed", onClick = onSpeed)
                    .semantics { stateDescription = "Playback speed ${speedLabel(speed)}" } else Modifier,
                style = pureChatFooter(MaterialTheme.typography.bodySmall).copy(fontFeatureSettings = "tnum"),
                color = if (MaterialTheme.chatColors.pure) voiceInk.copy(alpha = .7f) else MaterialTheme.chatColors.metadata,
                maxLines = 1
            )
        }
        error?.let {
            Text(
                it,
                Modifier.padding(horizontal = MaterialTheme.spacing.compact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Rounded bars resampled to the available width; bars before [progress] use the accent. */
@Composable
internal fun VoiceBars(levels: List<Int>, progress: Float, modifier: Modifier = Modifier) {
    val played = if (MaterialTheme.chatColors.pure) LocalContentColor.current else MaterialTheme.colorScheme.primary
    val remaining = if (MaterialTheme.chatColors.pure) LocalContentColor.current.copy(alpha = .45f) else MaterialTheme.chatColors.metadata.copy(alpha = .4f)
    Canvas(modifier) {
        val bar = ChatVoiceLayout.bar.toPx()
        val pitch = bar + ChatVoiceLayout.barGap.toPx()
        val capacity = ((size.width + ChatVoiceLayout.barGap.toPx()) / pitch).toInt().coerceAtLeast(1)
        val count = if (levels.size > 1) minOf(capacity, levels.size) else capacity
        val minimum = ChatVoiceLayout.barMin.toPx()
        repeat(count) { index ->
            val level = levels[(index * levels.size / count).coerceAtMost(levels.lastIndex)].coerceIn(0, 100)
            val height = (level / 100f * size.height).coerceIn(minimum, size.height)
            val x = index * pitch + bar / 2
            drawLine(
                if ((index + .5f) / count <= progress) played else remaining,
                Offset(x, (size.height - height) / 2 + bar / 2),
                Offset(x, (size.height + height) / 2 - bar / 2),
                strokeWidth = bar,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun VoiceWaveform(
    levels: List<Int>,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    val color = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
    Canvas(modifier.semantics {
        contentDescription =
            if (levels.isEmpty()) "Audio waveform unavailable" else "Recorded audio waveform"
    }) {
        if (levels.isEmpty()) {
            drawLine(
                muted,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            val step = size.width / levels.size
            levels.forEachIndexed { index, level ->
                val amplitude = (level.coerceIn(0, 100) / 100f * size.height / 2).coerceAtLeast(1f)
                drawLine(
                    if (progress == null || index.toFloat() / levels.size < progress) color else muted,
                    Offset((index + 0.5f) * step, size.height / 2 - amplitude),
                    Offset((index + 0.5f) * step, size.height / 2 + amplitude),
                    strokeWidth = (step * .55f).coerceIn(1f, 4.dp.toPx()),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

internal fun mediaTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
internal fun MediaDraftPreview(
    state: ConversationState,
    repository: SecureChatViewModel,
    controller: ChatMediaController,
    onOpen: (String, String) -> Unit
) {
    state.mediaDraft?.let { draft ->
        Text("Ready to send", style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            draft.items.forEach { media ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        Modifier.width(if (media.kind == "voice") 260.dp else 144.dp)
                            .padding(MaterialTheme.spacing.small)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (media.kind == "voice") "Voice draft" else if (media.kind == "video") "Video" else "Photo",
                                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(onClick = { repository.removeDraftAttachment(media.descriptor.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove ${media.kind}"
                                )
                            }
                        }
                        if (media.kind == "voice") InlineVoiceMessage(
                            draft.messageId,
                            media,
                            controller,
                            loading = false,
                            error = state.mediaRequestError.takeIf { state.mediaRequest?.attachmentId == media.descriptor.id },
                            draft = true
                        )
                        else MediaPreview(
                            media,
                            onOpen = { onOpen(draft.messageId, media.descriptor.id) },
                            savedOffline = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoiceRecordControl(controller: ChatMediaController, enabled: Boolean) {
    val controlSize = PureChatTokens.control
    val enabledNow by rememberUpdatedState(enabled)
    // The accessibility action starts locked recording with explicit controls.
    // The pointer gesture supports hold/release, left cancellation and up locking.
    Surface(
        shape = MaterialTheme.shapes.pill,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.sizeIn(minWidth = controlSize, minHeight = controlSize).semantics {
            role = Role.Button; contentDescription = "Record voice message"
            if (!enabled) disabled()
            onClick("Start locked recording") { if (enabled) controller.record(true); enabled }
        }.pointerInput(controller) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!enabledNow) return@awaitEachGesture
                val quickRelease =
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                if (quickRelease != null) {
                    quickRelease.consume(); controller.record(true); return@awaitEachGesture
                }
                if (currentEvent.changes.none { it.pressed } || currentEvent.changes.any { it.isConsumed }) return@awaitEachGesture
                controller.record()
                val origin = down.position;
                var locked = false;
                var cancelled = false;
                var released = false;
                var finished = false
                try {
                    do {
                        val event = awaitPointerEvent();
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            cancelled = true; break
                        }
                        val offset = change.position - origin
                        if (!locked && offset.x < -80.dp.toPx()) {
                            cancelled = true; change.consume(); break
                        }
                        if (!locked && offset.y < -80.dp.toPx()) {
                            locked = true; controller.lock(); change.consume()
                        }
                        if (!change.pressed) {
                            released = true; break
                        }
                    } while (true)
                    if (cancelled || (!locked && !released)) controller.stop(true)
                    else if (!locked) controller.stop(false, send = true)
                    finished = true
                } finally {
                    if (!finished) controller.stop(true)
                }
            }
        }) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(controlSize)
        ) { Icon(PureChatIcons.Mic, null, Modifier.size(PureChatTokens.icon)) }
    }
}

@Composable
internal fun RecordingPanel(controller: ChatMediaController) {
    Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Icon(
                if (controller.locked) Icons.Outlined.Lock else ChatIcons.Mic,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(MaterialTheme.spacing.icon)
            )
            Text(
                "Recording · ${mediaTime(controller.elapsed)}",
                style = MaterialTheme.typography.titleSmall
            )
        }
        VoiceWaveform(controller.levels, Modifier.fillMaxWidth().height(28.dp))
        if (!controller.locked) Text(
            "Release to send · slide left to cancel · slide up to lock",
            style = MaterialTheme.typography.bodySmall
        )
        Row {
            IconButton(onClick = { controller.stop(true) }) {
                Icon(
                    ChatIcons.DeleteOutline,
                    "Delete recording"
                )
            }
            if (controller.locked) {
                TextButton(onClick = { controller.stop(false) }) {
                    Icon(ChatIcons.Stop, null); Text(
                    "Preview"
                )
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = {
                    controller.stop(
                        false,
                        send = true
                    )
                }) { Icon(Icons.AutoMirrored.Filled.Send, "Send recording") }
            }
        }
    }
}
