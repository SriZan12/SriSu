package com.srisu.srisu.features.home.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.srisu.srisu.features.moments.presentation.GuardedMomentImage
import com.srisu.srisu.features.moments.presentation.MomentImage
import com.srisu.srisu.theme.spacing

/** Loads protected media bytes; previews pass a loader that returns null to show placeholders. */
typealias HomeImageLoader = suspend (String) -> ByteArray?

@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() })
        if (actionLabel != null) TextButton(onClick = onAction) {
            Text(actionLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = MaterialTheme.spacing.tiny).size(MaterialTheme.spacing.icon),
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun HomeAvatar(
    url: String?,
    load: HomeImageLoader,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    GuardedMomentImage(url, load, contentDescription, modifier.size(size).clip(CircleShape), avatar = true)
}

@Composable
fun HomePhoto(url: String, load: HomeImageLoader, size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val bytes by produceState<ByteArray?>(null, url) { value = load(url) }
    val shaped = modifier.size(size).clip(MaterialTheme.shapes.medium)
    if (bytes != null) MomentImage(bytes, contentDescription, shaped)
    else Box(shaped.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Filled circular icon plate used by quick actions, challenge and plan rows. */
@Composable
fun HomeIconPlate(icon: ImageVector, container: Color, size: Dp, modifier: Modifier = Modifier,
    content: Color = MaterialTheme.colorScheme.surfaceContainerLowest, glyph: Dp = MaterialTheme.spacing.chromeIcon) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(glyph))
    }
}
