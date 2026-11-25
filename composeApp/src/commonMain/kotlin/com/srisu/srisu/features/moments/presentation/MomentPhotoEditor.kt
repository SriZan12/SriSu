package com.srisu.srisu.features.moments.presentation

import com.srisu.srisu.theme.sriSuFilterChipColors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.srisu.srisu.features.couplechat.presentation.decodePrivatePhoto
import com.srisu.srisu.features.moments.data.MomentCrop
import com.srisu.srisu.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*
import kotlin.math.min
import kotlin.math.roundToInt

internal data class MomentPhotoPixels(val bitmap: ImageBitmap? = null, val failed: Boolean = false, val retry: () -> Unit = {})

/** Never decode on a gesture/frame, and never put private source bytes into a public image cache. */
@Composable internal fun rememberMomentPixels(photo: LocalMomentPhoto, load: suspend (String) -> ByteArray?): MomentPhotoPixels {
    var retry by remember(photo.id) { mutableIntStateOf(0) }
    val state by key(photo.id, photo.bytes, photo.remote?.image) { produceState(MomentPhotoPixels(), retry) {
        value = MomentPhotoPixels()
        try {
            val bytes = photo.bytes ?: photo.remote?.image?.let { load(it) }
            val bitmap = bytes?.let { withContext(Dispatchers.Default) { decodePrivatePhoto(it) } }
            value = MomentPhotoPixels(bitmap, failed = bitmap == null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = MomentPhotoPixels(failed = true) }
    } }
    return state.copy(retry = { retry++ })
}

private fun photoFrame(size: IntSize, aspect: Double): Rect {
    val width = min(size.width.toDouble(), size.height * aspect).coerceAtLeast(0.0)
    val height = width / aspect
    return Rect(((size.width - width) / 2).toFloat(), ((size.height - height) / 2).toFloat(),
        ((size.width + width) / 2).toFloat(), ((size.height + height) / 2).toFloat())
}

/** Both the editor and composer draw the same source rectangle that the server will publish. */
@Composable internal fun MomentFramedPhoto(
    pixels: MomentPhotoPixels, crop: MomentCrop, description: String, modifier: Modifier,
    onCrop: (((MomentCrop) -> MomentCrop) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val bitmap = pixels.bitmap
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val currentCrop by rememberUpdatedState(crop)
    val change by rememberUpdatedState(onCrop)
    val sourceAspect = bitmap?.let { it.width.toDouble() / it.height } ?: 1.0
    val left = stringResource(Res.string.moment_crop_left)
    val right = stringResource(Res.string.moment_crop_right)
    val up = stringResource(Res.string.moment_crop_up)
    val down = stringResource(Res.string.moment_crop_down)
    val gesture = if (bitmap == null || onCrop == null) Modifier else Modifier.pointerInput(bitmap, bounds) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val frame = photoFrame(bounds, currentCrop.aspect(sourceAspect))
            if (frame.width > 0 && frame.height > 0) {
                // Transform the current state at dispatch, not the last composed value:
                // several pointer events can arrive before the next render frame.
                change?.invoke { latest -> latest.zoom(zoom.toDouble(), sourceAspect,
                    ((centroid.x - frame.left) / frame.width).toDouble(),
                    ((centroid.y - frame.top) / frame.height).toDouble())
                    .move((pan.x / frame.width).toDouble(), (pan.y / frame.height).toDouble()) }
            }
        }
    }
    Box(modifier.background(colors.mediaBackground).onSizeChanged { bounds = it }.then(gesture).semantics {
        contentDescription = description
        stateDescription = if (bitmap != null) "Photo ready" else if (pixels.failed) "Photo unavailable" else "Photo loading"
        if (onCrop != null && bitmap != null) customActions = listOf(
            CustomAccessibilityAction(left) { onCrop { it.move(-.1, 0.0) }; true },
            CustomAccessibilityAction(right) { onCrop { it.move(.1, 0.0) }; true },
            CustomAccessibilityAction(up) { onCrop { it.move(0.0, -.1) }; true },
            CustomAccessibilityAction(down) { onCrop { it.move(0.0, .1) }; true })
    }, contentAlignment = Alignment.Center) {
        if (bitmap != null) Canvas(Modifier.fillMaxSize()) {
            val frame = photoFrame(IntSize(size.width.roundToInt(), size.height.roundToInt()), crop.aspect(sourceAspect))
            val x = (crop.x * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val y = (crop.y * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val rightEdge = ((crop.x + crop.width) * bitmap.width).roundToInt().coerceIn(x + 1, bitmap.width)
            val bottomEdge = ((crop.y + crop.height) * bitmap.height).roundToInt().coerceIn(y + 1, bitmap.height)
            if (onCrop == null) {
                drawImage(bitmap, srcOffset = IntOffset(x, y), srcSize = IntSize(rightEdge - x, bottomEdge - y),
                    dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt().coerceAtLeast(1), frame.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High)
            } else clipRect {
                // Show surrounding pixels under a mask, so the user can find a clipped face before dragging.
                val photoWidth = frame.width / crop.width
                val photoHeight = frame.height / crop.height
                drawImage(bitmap, dstOffset = IntOffset((frame.left - crop.x * photoWidth).roundToInt(),
                    (frame.top - crop.y * photoHeight).roundToInt()),
                    dstSize = IntSize(photoWidth.roundToInt().coerceAtLeast(1), photoHeight.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High)
                val mask = colors.mediaBackground.copy(alpha = MomentTokens.scrimAlpha)
                drawRect(mask, size = Size(size.width, frame.top.coerceAtLeast(0f)))
                drawRect(mask, Offset(0f, frame.bottom), Size(size.width, (size.height - frame.bottom).coerceAtLeast(0f)))
                drawRect(mask, Offset(0f, frame.top), Size(frame.left.coerceAtLeast(0f), frame.height))
                drawRect(mask, Offset(frame.right, frame.top), Size((size.width - frame.right).coerceAtLeast(0f), frame.height))
            }
            if (onCrop != null) {
                val line = MomentTokens.outline.toPx()
                drawRect(colors.onMedia, frame.topLeft, frame.size, style = Stroke(line))
                for (i in 1..2) {
                    val fx = frame.left + frame.width * i / 3
                    val fy = frame.top + frame.height * i / 3
                    drawLine(colors.onMedia.copy(alpha = MomentTokens.chromeAlpha), Offset(fx, frame.top), Offset(fx, frame.bottom), line)
                    drawLine(colors.onMedia.copy(alpha = MomentTokens.chromeAlpha), Offset(frame.left, fy), Offset(frame.right, fy), line)
                }
            }
        } else if (pixels.failed) Column(Modifier.padding(MaterialTheme.spacing.medium), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.moment_photo_failed), color = colors.onMedia, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = pixels.retry) { Text(stringResource(Res.string.moment_retry), color = colors.onMedia) }
        }
        else CircularProgressIndicator(color = colors.onMedia)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun MomentPhotoEditor(
    photo: LocalMomentPhoto, image: suspend (String) -> ByteArray?, initial: MomentCrop?,
    onCancel: () -> Unit, onApply: (MomentCrop?) -> Unit,
) {
    val space = MaterialTheme.spacing
    val pixels = rememberMomentPixels(photo, image)
    val bitmap = pixels.bitmap
    // Primitive saveable values retain uncommitted edits through resize/recreation; Cancel never writes the draft.
    var x by rememberSaveable(photo.id) { mutableDoubleStateOf(initial?.x ?: 0.0) }
    var y by rememberSaveable(photo.id) { mutableDoubleStateOf(initial?.y ?: 0.0) }
    var width by rememberSaveable(photo.id) { mutableDoubleStateOf(initial?.width ?: 1.0) }
    var height by rememberSaveable(photo.id) { mutableDoubleStateOf(initial?.height ?: 1.0) }
    val crop = MomentCrop(x, y, width, height)
    val update: (MomentCrop) -> Unit = { x = it.x; y = it.y; width = it.width; height = it.height }
    val sourceAspect = bitmap?.let { it.width.toDouble() / it.height } ?: 1.0
    BackHandler(onBack = onCancel)
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= MomentComposerTokens.twoPaneMinWidth && maxWidth > maxHeight
            val controlsHeight = maxHeight * MomentComposerTokens.editorControlsFraction
            Column(Modifier.widthIn(max = MomentComposerTokens.maxWidth).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = space.small), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("moment-crop-cancel")) {
                        Icon(Icons.Outlined.Close, stringResource(Res.string.moment_cancel))
                    }
                    Text(stringResource(Res.string.moment_crop_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onApply(crop.takeUnless { it.full }) }, enabled = bitmap != null,
                        modifier = Modifier.testTag("moment-crop-apply")) { Text(stringResource(Res.string.moment_crop_apply)) }
                }
                val preview: @Composable (Modifier) -> Unit = { modifier ->
                    MomentFramedPhoto(pixels, crop, stringResource(Res.string.moment_crop_canvas),
                        modifier.clip(MaterialTheme.shapes.medium).testTag("moment-crop-canvas")) { transform ->
                        update(transform(MomentCrop(x, y, width, height)))
                    }
                }
                val controls: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(space.tiny)) {
                        Text(stringResource(Res.string.moment_crop_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.small)) {
                            val ratios = listOf(stringResource(Res.string.moment_crop_original) to sourceAspect, "1:1" to 1.0, "4:5" to .8, "16:9" to (16.0 / 9))
                            ratios.forEach { (label, ratio) ->
                                FilterChip(selected = kotlin.math.abs(crop.aspect(sourceAspect) - ratio) < .001,
                                    onClick = { update(MomentCrop.centered(sourceAspect, ratio)) }, enabled = bitmap != null,
                                    label = { Text(label) }, modifier = Modifier.heightIn(min = space.touchTarget).testTag("moment-crop-ratio-$label"), colors = sriSuFilterChipColors())
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.moment_crop_zoom), style = MaterialTheme.typography.labelMedium)
                            Slider(value = crop.zoomLevel(sourceAspect).toFloat().coerceIn(1f, MomentCrop.MaxZoom.toFloat()),
                                onValueChange = {
                                    val latest = MomentCrop(x, y, width, height)
                                    update(latest.zoom(it / latest.zoomLevel(sourceAspect), sourceAspect))
                                },
                                valueRange = 1f..MomentCrop.MaxZoom.toFloat(), enabled = bitmap != null,
                                modifier = Modifier.weight(1f).padding(horizontal = space.small).testTag("moment-crop-zoom")
                                    .semantics { contentDescription = "Photo zoom" })
                        }
                        TextButton(onClick = { update(MomentCrop.Full) }, enabled = bitmap != null,
                            modifier = Modifier.testTag("moment-crop-reset")) { Text(stringResource(Res.string.moment_crop_reset)) }
                    }
                }
                if (wide) Row(Modifier.weight(1f).padding(horizontal = space.medium).padding(bottom = space.small),
                    horizontalArrangement = Arrangement.spacedBy(space.medium)) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.weight(1f).fillMaxHeight())
                } else Column(Modifier.weight(1f).padding(horizontal = space.medium).padding(bottom = space.small),
                    verticalArrangement = Arrangement.spacedBy(space.small)) {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    controls(Modifier.heightIn(max = controlsHeight).fillMaxWidth())
                }
            }
        }
    }
}
