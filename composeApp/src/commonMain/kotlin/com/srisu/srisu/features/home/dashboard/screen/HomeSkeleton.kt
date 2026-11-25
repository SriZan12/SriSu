package com.srisu.srisu.features.home.dashboard.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srisu.srisu.theme.HomeSpace as H

private val RestingShimmer: State<Float> = mutableStateOf(-1f)
private val LocalHomeShimmer = staticCompositionLocalOf { RestingShimmer }

@Composable internal fun HomeLoadingMotion(needed: Boolean, content: @Composable () -> Unit) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motion = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val progress = if (needed && motion > 0f && lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
        rememberInfiniteTransition(label = "Home loading").animateFloat(-.6f, 1.6f,
            infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "Home shimmer")
    } else RestingShimmer
    CompositionLocalProvider(LocalHomeShimmer provides progress, content = content)
}

@Composable private fun Bone(modifier: Modifier, shape: Shape = H.pill) {
    val phase = LocalHomeShimmer.current
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = .075f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = .5f)
    Box(modifier.clip(shape).background(base).drawWithContent {
        drawContent()
        val position = phase.value
        if (position >= -.6f) {
            val center = size.width * position
            drawRect(Brush.linearGradient(listOf(highlight.copy(alpha = 0f), highlight, highlight.copy(alpha = 0f)),
                Offset(center - size.width * .35f, 0f), Offset(center + size.width * .35f, size.height)))
        }
    }.clearAndSetSemantics {})
}

@Composable internal fun HomeHeadingSkeleton() {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val heading = Modifier.fillMaxWidth().testTag("home-skeleton").clearAndSetSemantics {
        contentDescription = "Loading Home"
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Bone(Modifier.fillMaxWidth(.7f).height(24.dp * LocalDensity.current.fontScale))
            Bone(Modifier.width(110.dp).height(13.dp * LocalDensity.current.fontScale))
        }
    }
    val notification: @Composable () -> Unit = {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Bone(Modifier.size(24.dp), CircleShape)
        }
    }
    if (largeText) Column(heading) {
        identity(Modifier.fillMaxWidth())
        Box(Modifier.align(Alignment.End)) { notification() }
    } else Row(heading) {
        identity(Modifier.weight(1f))
        notification()
    }
}

@Composable internal fun HomePortraitSkeleton(compact: Boolean) {
    val large = LocalDensity.current.fontScale > 1.3f
    val avatar = if (compact) 76.dp else if (LocalDensity.current.fontScale > 1.5f) 78.dp else 90.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            repeat(2) { index ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bone(Modifier.widthIn(max = 102.dp).fillMaxWidth().height(28.dp))
                    Bone(Modifier.size(avatar), CircleShape)
                    Bone(Modifier.width(65.dp).height(16.dp))
                }
                if (index == 0 && !large) Bone(Modifier.width(if (compact) 67.dp else 76.dp).height(27.dp))
            }
        }
        if (large) Bone(Modifier.align(Alignment.CenterHorizontally).width(76.dp).height(32.dp))
    }
}

@Composable internal fun HomeQuestionSkeleton(compact: Boolean = false) {
    val scale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().clip(H.hero).background(H.lilac).padding(if (compact) 18.dp else 20.dp)
        .clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Bone(Modifier.width(60.dp).height(12.dp * scale))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            for (width in listOf(.82f, .7f, .56f)) Bone(Modifier.fillMaxWidth(width).height(22.dp * scale))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Bone(Modifier.size(34.dp), CircleShape)
            Bone(Modifier.widthIn(max = 140.dp).fillMaxWidth(.55f).height(40.dp))
        }
    }
}

@Composable internal fun HomeRowSkeleton(challenge: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(H.card).background(H.warm).padding(18.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Bone(Modifier.size(38.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Bone(Modifier.fillMaxWidth(.85f).height(16.dp * LocalDensity.current.fontScale))
            Bone(Modifier.fillMaxWidth(.65f).height(11.dp * LocalDensity.current.fontScale))
            if (challenge) Bone(Modifier.fillMaxWidth(.72f).height(12.dp * LocalDensity.current.fontScale))
        }
    }
}

@Composable internal fun HomeSectionSkeleton(kind: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clearAndSetSemantics {}) {
        Bone(Modifier.width(if (kind == "story") 150.dp else 120.dp).height(20.dp * LocalDensity.current.fontScale))
        when (kind) {
            "story" -> {
                val large = LocalDensity.current.fontScale > 1.3f
                if (large) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { Bone(Modifier.fillMaxWidth().height(96.dp), H.card) }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { Bone(Modifier.weight(1f).height(110.dp), H.card) }
                }
            }
            else -> {
                HomeRowSkeleton(challenge = kind == "challenge")
                if (kind == "from") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) { Bone(Modifier.weight(1f).height(40.dp)) }
                }
            }
        }
    }
}
