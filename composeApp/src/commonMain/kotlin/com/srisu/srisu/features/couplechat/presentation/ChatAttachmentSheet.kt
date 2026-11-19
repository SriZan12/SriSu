package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.ChatShareTokens
import com.srisu.srisu.theme.PureChatTokens
import com.srisu.srisu.theme.chatColors
import com.srisu.srisu.theme.chatShareTypography
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatAttachmentSheet(
    controller: ChatMediaController,
    onDismiss: () -> Unit,
    onSpark: (() -> Unit)? = null,
    onChallenge: (() -> Unit)? = null,
    onPlan: (() -> Unit)? = null,
) {
    // Keep native pickers, permission handling and draft recording on their existing paths.
    val media = listOf(
        ShareAction(Res.string.chat_share_photos, ShareArt.Photos) { controller.pick("photo") },
        ShareAction(Res.string.chat_share_camera, ShareArt.Camera) { controller.pick("camera") },
        ShareAction(Res.string.chat_share_video, ShareArt.Video) { controller.pick("video") },
        ShareAction(Res.string.chat_share_voice, ShareArt.Voice) { controller.record(true) },
    )
    val together = listOfNotNull(
        onSpark?.let { ShareAction(Res.string.chat_share_spark, ShareArt.Spark, Res.string.chat_share_spark_detail, it) },
        onChallenge?.let { ShareAction(Res.string.chat_share_challenge, ShareArt.Challenge, Res.string.chat_share_challenge_detail, it) },
        onPlan?.let { ShareAction(Res.string.chat_share_plan, ShareArt.Plan, Res.string.chat_share_plan_detail, it) },
    )
    val colors = MaterialTheme.chatColors
    val scrimAlpha = if (colors.scheme.surface.luminance() < .5f) ChatShareTokens.darkScrimAlpha else ChatShareTokens.lightScrimAlpha
    MaterialTheme(typography = chatShareTypography(MaterialTheme.typography)) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = ChatShareTokens.sheetShape,
            containerColor = colors.scheme.surface,
            contentColor = colors.text,
            tonalElevation = 0.dp,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha),
            dragHandle = {
                Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(colors.scheme.outlineVariant))
                }
            },
        ) {
            Column(Modifier.fillMaxWidth().testTag("chat-quick-share-sheet")
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.chat_share_title), Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(PureChatTokens.control)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.incoming), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, stringResource(Res.string.chat_share_close), Modifier.size(18.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
                    val columns = if (LocalDensity.current.fontScale >= ChatShareTokens.largeTextReflow) 2 else 4
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        media.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { action ->
                                    Column(Modifier.weight(1f).heightIn(min = 82.dp).clip(ChatShareTokens.actionShape)
                                        .clickable(role = Role.Button) { onDismiss(); action.onClick() }
                                        .padding(vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ShareIllustration(action.art, media = true)
                                        Text(stringResource(action.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                    if (together.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = colors.scheme.outlineVariant)
                        Spacer(Modifier.height(22.dp))
                        Text(stringResource(Res.string.chat_share_together), color = colors.metadata,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            together.forEach { action ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 65.dp).clip(ChatShareTokens.actionShape)
                                    .clickable(role = Role.Button) { onDismiss(); action.onClick() }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                    ShareIllustration(action.art, media = false)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(stringResource(action.label), style = MaterialTheme.typography.titleMedium)
                                        action.detail?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.metadata) }
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(17.dp), tint = colors.metadata)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class ShareAction(
    val label: org.jetbrains.compose.resources.StringResource,
    val art: ShareArt,
    val detail: org.jetbrains.compose.resources.StringResource? = null,
    val onClick: () -> Unit,
)

/** Source rectangles from the approved preview's 768px atlas; no theme tint or redrawing. */
private enum class ShareArt(val origin: IntOffset) {
    Photos(IntOffset(17, 33)), Camera(IntOffset(256, 29)), Video(IntOffset(508, 25)),
    Voice(IntOffset(12, 264)), Spark(IntOffset(256, 261)), Challenge(IntOffset(507, 261)), Plan(IntOffset(10, 497)),
}

@Composable
private fun ShareIllustration(art: ShareArt, media: Boolean) {
    val atlas = imageResource(Res.drawable.chat_share_illustrations)
    Box(Modifier.size(if (media) ChatShareTokens.mediaSlot else ChatShareTokens.activitySlot), contentAlignment = Alignment.Center) {
        // Resource loading can briefly return a 1px placeholder. Keep the slot stable until loaded;
        // derive source rectangles from decoded dimensions to also support density-aware decoders.
        if (atlas.width >= 3 && atlas.height >= 3) {
            val painter = remember(atlas, art) {
                BitmapPainter(atlas,
                    IntOffset((art.origin.x * atlas.width / 768f).roundToInt(), (art.origin.y * atlas.height / 768f).roundToInt()),
                    IntSize(atlas.width / 3, atlas.height / 3), filterQuality = FilterQuality.High)
            }
            Image(painter, contentDescription = null,
                modifier = Modifier.requiredSize(if (media) ChatShareTokens.mediaArt else ChatShareTokens.activityArt))
        }
    }
}
