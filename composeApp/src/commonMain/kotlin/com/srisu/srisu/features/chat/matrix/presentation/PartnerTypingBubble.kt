package com.srisu.srisu.features.chat.matrix.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.srisu.srisu.theme.PureChatTokens
import com.srisu.srisu.theme.chatColors
import org.jetbrains.compose.resources.stringResource
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.chat_partner_typing

/** Ephemeral activity has its own list key; it is never a message or a read receipt. */
@Composable
internal fun PartnerTypingBubble(partner: String, motionEnabled: Boolean) {
    val label = stringResource(Res.string.chat_partner_typing, partner)
    Box(Modifier.fillMaxWidth().padding(top = PureChatTokens.groupGap)) {
        Surface(shape = PureChatTokens.bubbleShape(false, false), color = MaterialTheme.chatColors.incoming,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val transition = if (motionEnabled) rememberInfiniteTransition(label = "Typing") else null
                repeat(3) { index ->
                    val alpha = transition?.animateFloat(.4f, 1f,
                        infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(index * 180)),
                        label = "Typing dot $index")
                    Box(Modifier.size(5.dp).graphicsLayer { this.alpha = alpha?.value ?: 1f }
                        .background(MaterialTheme.chatColors.metadata, CircleShape))
                }
            }
        }
    }
}
