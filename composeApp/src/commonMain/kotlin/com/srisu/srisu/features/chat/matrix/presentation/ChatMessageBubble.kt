package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.srisu.srisu.features.chat.matrix.MatrixMessageDelivery
import com.srisu.srisu.theme.ChatTokens
import com.srisu.srisu.theme.spacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_secure_content_unavailable
import srisu.composeapp.generated.resources.chat_secure_received_at
import srisu.composeapp.generated.resources.chat_secure_status_failed
import srisu.composeapp.generated.resources.chat_secure_status_sending
import srisu.composeapp.generated.resources.chat_secure_status_sent
import srisu.composeapp.generated.resources.chat_secure_tap_to_retry
import srisu.composeapp.generated.resources.chat_status_failed
import srisu.composeapp.generated.resources.chat_status_pending

private class BubbleColors(val container: Color, val content: Color, val meta: Color)

@Composable
private fun bubbleColors(mine: Boolean, unavailable: Boolean): BubbleColors {
    val scheme = MaterialTheme.colorScheme
    return when {
        unavailable -> BubbleColors(scheme.surface, scheme.onSurfaceVariant, scheme.onSurfaceVariant)
        // Figma's thread uses the foreground ink for your messages and a warm surface for theirs.
        mine -> BubbleColors(scheme.primary, scheme.onPrimary, scheme.onPrimary.copy(alpha = 0.82f))
        else -> BubbleColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.onSurfaceVariant)
    }
}

@Composable
internal fun ChatMessageRow(
    item: ChatTimelineItem.Message,
    maxBubbleWidth: Dp,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = item.message
    val retry = message.retryTransactionId?.takeIf { message.delivery == MatrixMessageDelivery.FAILED }
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = if (item.joinsPrevious) ChatTokens.groupedGap else ChatTokens.groupGap),
        horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
    ) {
        ChatBubble(item, Modifier.widthIn(max = maxBubbleWidth))
        if (retry != null) {
            Text(
                stringResource(Res.string.chat_secure_tap_to_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .heightIn(min = MaterialTheme.spacing.touchTarget)
                    .clickable(role = Role.Button) { onRetry(retry) }
                    .padding(horizontal = MaterialTheme.spacing.tiny)
                    .wrapContentHeight(),
            )
        }
    }
}

@Composable
private fun ChatBubble(item: ChatTimelineItem.Message, modifier: Modifier) {
    val message = item.message
    val colors = bubbleColors(message.mine, message.unavailable)
    val shape = remember(message.mine, item.joinsPrevious, item.joinsNext) {
        ChatTokens.bubbleShape(message.mine, item.joinsPrevious, item.joinsNext)
    }
    val time = remember(item.time) { formatChatTime(item.time) }
    val statusLabel = when (message.delivery) {
        MatrixMessageDelivery.SENDING -> stringResource(Res.string.chat_secure_status_sending)
        MatrixMessageDelivery.FAILED -> stringResource(Res.string.chat_secure_status_failed)
        MatrixMessageDelivery.SENT -> if (message.mine) stringResource(Res.string.chat_secure_status_sent) else null
    }
    val body = if (message.unavailable) stringResource(Res.string.chat_secure_content_unavailable) else message.body
    val description = listOfNotNull(body, stringResource(Res.string.chat_secure_received_at, time, statusLabel ?: ""))
        .joinToString(". ").trimEnd(',', ' ')
    Surface(
        modifier = modifier
            .then(
                if (message.unavailable) Modifier.border(
                    width = MaterialTheme.spacing.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ) else Modifier,
            )
            .clearAndSetSemantics { contentDescription = description },
        shape = shape,
        color = colors.container,
    ) {
        Box(
            Modifier.padding(
                horizontal = ChatTokens.bubbleHorizontalPadding,
                vertical = ChatTokens.bubbleVerticalPadding,
            ),
        ) {
            val textStyle = MaterialTheme.typography.bodyLarge.let {
                if (message.unavailable) it.copy(fontStyle = FontStyle.Italic) else it
            }
            if (message.unavailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.meta,
                        modifier = Modifier.size(ChatTokens.metaIcon),
                    )
                    TextWithMeta(body, textStyle, colors.content) {
                        if (item.showMeta) MessageMeta(time, message.mine, message.delivery, colors.meta)
                    }
                }
            } else {
                TextWithMeta(body, textStyle, colors.content) {
                    if (item.showMeta) MessageMeta(time, message.mine, message.delivery, colors.meta)
                }
            }
        }
    }
}

/**
 * Places [meta] on the last text line when it fits (WhatsApp/Telegram-style), otherwise on its own
 * line at the end. Avoids a reserved blank line under every short message.
 */
@Composable
private fun TextWithMeta(
    text: String,
    style: TextStyle,
    color: Color,
    meta: @Composable () -> Unit,
) {
    val layoutRef = remember { arrayOfNulls<TextLayoutResult>(1) }
    val gap = ChatTokens.metaGap
    Layout(
        content = {
            Text(text, style = style, color = color, onTextLayout = { layoutRef[0] = it })
            Box { meta() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val textPlaceable = measurables[0].measure(loose)
        val metaPlaceable = measurables[1].measure(loose)
        val gapPx = gap.roundToPx()
        val layout = layoutRef[0]
        if (metaPlaceable.width == 0) {
            return@Layout layout(textPlaceable.width, textPlaceable.height) { textPlaceable.place(0, 0) }
        }
        val lastLineRight = layout?.let {
            val last = it.lineCount - 1
            // RTL last lines end on the left; place meta below for those to avoid overlap.
            if (it.getParagraphDirection(it.getLineStart(last)) == ResolvedTextDirection.Rtl) null
            else it.getLineRight(last).toInt()
        }
        val inlineWidth = lastLineRight?.let { it + gapPx + metaPlaceable.width }
        if (inlineWidth != null && inlineWidth <= constraints.maxWidth) {
            val width = maxOf(textPlaceable.width, inlineWidth).coerceAtLeast(constraints.minWidth)
            val height = maxOf(textPlaceable.height, metaPlaceable.height)
            layout(width, height) {
                textPlaceable.place(0, 0)
                metaPlaceable.place(width - metaPlaceable.width, height - metaPlaceable.height)
            }
        } else {
            val width = maxOf(textPlaceable.width, metaPlaceable.width)
                .coerceIn(constraints.minWidth, if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity)
            val height = textPlaceable.height + metaPlaceable.height
            layout(width, height) {
                textPlaceable.place(0, 0)
                metaPlaceable.place(width - metaPlaceable.width, textPlaceable.height)
            }
        }
    }
}

@Composable
private fun MessageMeta(time: String, mine: Boolean, delivery: MatrixMessageDelivery, color: Color) {
    Row(
        Modifier.padding(top = MaterialTheme.spacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny),
    ) {
        Text(
            time,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = ChatTokens.metaTextSize,
                lineHeight = ChatTokens.metaLineHeight,
                letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing,
            ),
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        if (mine) when (delivery) {
            MatrixMessageDelivery.SENDING -> Icon(
                painterResource(Res.drawable.chat_status_pending),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(ChatTokens.metaIcon),
            )
            MatrixMessageDelivery.SENT -> Icon(
                Icons.Filled.Done,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(ChatTokens.metaIcon),
            )
            MatrixMessageDelivery.FAILED -> Icon(
                painterResource(Res.drawable.chat_status_failed),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(ChatTokens.metaIcon),
            )
        }
    }
}
