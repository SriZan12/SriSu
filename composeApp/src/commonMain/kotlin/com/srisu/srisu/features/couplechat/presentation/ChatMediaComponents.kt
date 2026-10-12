@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.srisu.srisu.features.couplechat.presentation

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
import androidx.compose.ui.unit.dp
import com.srisu.srisu.features.couplechat.data.*
import com.srisu.srisu.theme.*
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
    media.thumbnail?.takeIf { it.isNotBlank() && it.length <= 8192 }?.let(Base64::decode)
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
        InlineVoicePlayer(media, false, 0, 0, loading, error, savedOffline, onOpen, {}, modifier)
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
    savedOffline: Boolean,
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
        savedOffline,
        { controller.openVoice(messageId, media.descriptor.id, draft) },
        controller.player::seek,
        modifier
    )
}

@Composable
internal fun InlineVoicePlayer(
    media: ChatMedia,
    playing: Boolean,
    position: Long,
    duration: Long,
    loading: Boolean,
    error: String?,
    savedOffline: Boolean,
    onPlay: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            FilledIconButton(
                onClick = onPlay, enabled = !loading, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (loading) CircularProgressIndicator(
                    Modifier.size(20.dp).semantics { contentDescription = "Opening voice message" },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                else Icon(
                    if (error != null) Icons.Outlined.Refresh else if (playing) ChatIcons.Pause else Icons.Outlined.PlayArrow,
                    if (error != null) "Retry playback" else if (playing) "Pause voice message" else "Play voice message"
                )
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    if (media.waveform.isNotEmpty()) VoiceWaveform(
                        media.waveform,
                        Modifier.fillMaxWidth().height(24.dp),
                        fraction
                    )
                    val canSeek = duration > 0 && !loading && error == null
                    if (canSeek) Slider(
                        value = fraction, onValueChange = onSeek,
                        thumb = {
                            Box(
                                Modifier.size(8.dp, 20.dp).background(
                                    if (duration > 0 && !loading && error == null) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f),
                                    MaterialTheme.shapes.pill
                                )
                            )
                        },
                        track = {
                            if (media.waveform.isEmpty()) LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f),
                                drawStopIndicator = {})
                        },
                        modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = "Seek voice message" })
                    else if (media.waveform.isEmpty()) LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f),
                        drawStopIndicator = {})
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (loading) "Opening…" else if (duration > 0) "${mediaTime(position)} / ${
                            mediaTime(
                                duration
                            )
                        }"
                        else if (media.durationMs > 0) mediaTime(media.durationMs) else "Voice message",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!savedOffline && !loading) Icon(
                        ChatIcons.Download,
                        "Download voice message",
                        Modifier.size(16.dp)
                    )
                }
            }
        }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
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
    repository: ConversationRepository,
    controller: ChatMediaController,
    onOpen: (String, String) -> Unit
) {
    state.mediaDraft?.let { draft ->
        Text("Ready to send · ${draft.items.size}/3", style = MaterialTheme.typography.bodySmall)
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
                            savedOffline = true,
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
internal fun ChatAttachmentSheet(
    controller: ChatMediaController,
    onDismiss: () -> Unit,
    onCard: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.gutter).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text("Share something", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                ShareTile(
                    "Photos",
                    ChatIcons.Image,
                    Modifier.weight(1f)
                ) { onDismiss(); controller.pick("photo") }
                ShareTile(
                    "Video",
                    ChatIcons.Videocam,
                    Modifier.weight(1f)
                ) { onDismiss(); controller.pick("video") }
                ShareTile(
                    "Camera",
                    ChatIcons.PhotoCamera,
                    Modifier.weight(1f)
                ) { onDismiss(); controller.pick("camera") }
            }
            ChatActionRow(
                "Record a voice draft",
                ChatIcons.Mic,
                { onDismiss(); controller.record(true) })
            HorizontalDivider()
            Text("Something for the two of you", style = MaterialTheme.typography.titleSmall)
            ChatActionRow("Spark", ChatIcons.AutoAwesome, { onDismiss(); onCard("spark") })
            ChatActionRow("Ask", ChatIcons.ChatBubbleOutline, { onDismiss(); onCard("ask") })
            ChatActionRow("Planner", ChatIcons.Event, { onDismiss(); onCard("planner") })
            ChatActionRow("Moment", ChatIcons.Collections, { onDismiss(); onCard("moment") })
            Text(
                "Up to 3 attachments. Videos up to 2 minutes and 16 MB. Location tags are removed from photos.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ShareTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(
                vertical = MaterialTheme.spacing.medium,
                horizontal = MaterialTheme.spacing.small
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun VoiceRecordControl(controller: ChatMediaController, enabled: Boolean) {
    val enabledNow by rememberUpdatedState(enabled)
    // The accessibility action starts locked recording with explicit controls.
    // The pointer gesture supports hold/release, left cancellation and up locking.
    Surface(
        shape = MaterialTheme.shapes.pill,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
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
            modifier = Modifier.size(48.dp)
        ) { Icon(ChatIcons.Mic, null) }
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
