package com.srisu.srisu.features.couplechat.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srisu.srisu.theme.RomanticColors
import com.srisu.srisu.theme.romanticColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal val RomanticIntensity.durationMillis: Int get() = when(this) {
    RomanticIntensity.SWEET -> 3000
    RomanticIntensity.AFFECTIONATE -> 3400
    RomanticIntensity.DEEP -> 3900
}

@Stable internal class RomanticAnimationState {
    var effect by mutableStateOf<RomanticEffect?>(null)
        private set
    val progress = Animatable(0f)
    suspend fun play(value: RomanticEffect) {
        // Uses the same platform duration-scale / Reduce Motion support as profile accents.
        if(coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return
        try {
            progress.snapTo(0f)
            effect = value
            progress.animateTo(1f,tween(value.intensity.durationMillis,easing=LinearEasing))
        } finally { effect = null }
    }
}

/** No replay queue: a newer arrival replaces the current effect, never accumulating layers. */
@Composable internal fun RomanticMessageOverlay(events: Flow<RomanticEffect>, accountId: Long?, roomId: String?,
    enabled: Boolean, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val animation = remember(accountId,roomId) { RomanticAnimationState() }
    LaunchedEffect(events,animation,active) {
        if(active) events.collectLatest { effect ->
            if(effect.accountId==accountId && effect.roomId==roomId) animation.play(effect)
        }
    }
    if(active) animation.effect?.let { effect ->
        RomanticParticles(effect, {animation.progress.value}, modifier)
    }
}

private data class RomanticParticle(val x: Float, val sizeDp: Float, val start: Float, val travel: Float,
    val swayDp: Float, val phase: Float, val tilt: Float, val bubble: Boolean, val heartInside: Boolean, val dark: Boolean)

private fun particles(effect: RomanticEffect): List<RomanticParticle> {
    val random = Random(effect.seed)
    val count = when(effect.intensity) { RomanticIntensity.SWEET -> 8; RomanticIntensity.AFFECTIONATE -> 22; RomanticIntensity.DEEP -> 44 }
    return List(count) { index ->
        val bubble = effect.intensity==RomanticIntensity.SWEET || index%6==0
        val start = random.nextFloat() * when(effect.intensity) {
            RomanticIntensity.SWEET -> .15f; RomanticIntensity.AFFECTIONATE -> .40f; RomanticIntensity.DEEP -> .20f
        }
        RomanticParticle(x=.06f+random.nextFloat()*.88f,
            sizeDp=when(effect.intensity) {
                RomanticIntensity.SWEET -> 14f+random.nextFloat()*15f
                RomanticIntensity.AFFECTIONATE -> 10f+random.nextFloat()*14f
                RomanticIntensity.DEEP -> 12f+random.nextFloat()*26f
            },start=start,travel=(1f-start)*(.82f+random.nextFloat()*.18f),
            swayDp=8f+random.nextFloat()*20f,phase=random.nextFloat()*(2*PI).toFloat(),
            tilt=-18f+random.nextFloat()*36f,bubble=bubble,heartInside=bubble && index%3==0,dark=index%3==1)
    }
}

// Original, slightly tapered heart with soft asymmetric lobes. No font, emoji, or icon asset.
private fun petalHeart() = Path().apply {
    moveTo(.50f,.94f)
    cubicTo(.36f,.79f,.04f,.57f,.04f,.31f)
    cubicTo(.04f,.07f,.33f,.02f,.50f,.24f)
    cubicTo(.69f,.00f,.96f,.09f,.96f,.32f)
    cubicTo(.96f,.58f,.66f,.79f,.50f,.94f)
    close()
}
private fun heartGlint() = Path().apply {
    moveTo(.18f,.33f); cubicTo(.17f,.21f,.28f,.16f,.36f,.23f)
}

/** Frame state is read only during drawing; neither the list nor its messages observe progress. */
@Composable internal fun RomanticParticles(effect: RomanticEffect, progress: () -> Float, modifier: Modifier = Modifier) {
    val particles = remember(effect) { particles(effect) }
    val heart = remember { petalHeart() }
    val glint = remember { heartGlint() }
    val colors = MaterialTheme.colorScheme.romanticColors
    val lightFill = remember(colors) { Brush.linearGradient(listOf(colors.blush,colors.rose),Offset.Zero,Offset(1f,1f)) }
    val deepFill = remember(colors) { Brush.linearGradient(listOf(colors.rose,colors.ruby),Offset.Zero,Offset(1f,1f)) }
    // Deliberately no pointerInput, clickable, focus, or accessibility nodes.
    Canvas(modifier.clipToBounds().clearAndSetSemantics {}) {
        if(size.width<=0f || size.height<=0f) return@Canvas
        val elapsed = progress().coerceIn(0f,1f)
        for(particle in particles) {
            val fraction = (elapsed-particle.start)/particle.travel
            if(fraction<=0f || fraction>=1f) continue
            val rise = FastOutSlowInEasing.transform(fraction)
            val diameter = particle.sizeDp.dp.toPx()
            val radius = diameter/2
            val sway = sin(fraction*(2*PI).toFloat()+particle.phase)*particle.swayDp.dp.toPx()
            val edgeInset = minOf(radius,size.width/2)
            val center = Offset((particle.x*size.width+sway).coerceIn(edgeInset,size.width-edgeInset),
                size.height+diameter-rise*(size.height+diameter*2))
            val fadeIn = (fraction/.13f).coerceIn(0f,1f)
            val fadeOut = ((1f-fraction)/.30f).coerceIn(0f,1f)
            val alpha = fadeIn*fadeOut*(if(particle.bubble).58f else .80f)
            if(particle.bubble) {
                drawCircle(colors.blush.copy(alpha=alpha*.16f),radius,center)
                drawCircle(colors.rose, radius, center, alpha=alpha, style=Stroke(1.dp.toPx()))
                drawArc(colors.glint,195f,78f,false,center-Offset(radius*.75f,radius*.75f),Size(diameter*.75f,diameter*.75f),
                    alpha=alpha*.9f,style=Stroke(1.2f.dp.toPx(),cap=StrokeCap.Round))
                if(particle.heartInside) drawFloatingHeart(heart,glint,lightFill,colors,center,diameter*.42f,particle.tilt,alpha*.8f)
            } else drawFloatingHeart(heart,glint,if(particle.dark)deepFill else lightFill,colors,center,diameter,
                particle.tilt+sin(fraction*PI.toFloat()+particle.phase)*9f,alpha)
        }
    }
}

private fun DrawScope.drawFloatingHeart(heart: Path, glint: Path, fill: Brush, colors: RomanticColors,
    center: Offset, diameter: Float, tilt: Float, alpha: Float) {
    withTransform({
        translate(center.x-diameter/2,center.y-diameter/2)
        rotate(tilt,pivot=Offset(diameter/2,diameter/2))
        scale(diameter,diameter,pivot=Offset.Zero)
    }) {
        drawPath(heart,fill,alpha=alpha)
        drawPath(glint,colors.glint,alpha=alpha*.8f,style=Stroke(.045f,cap=StrokeCap.Round))
    }
}
