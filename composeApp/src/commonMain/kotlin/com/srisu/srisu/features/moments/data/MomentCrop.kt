package com.srisu.srisu.features.moments.data

import kotlinx.serialization.Serializable
import kotlin.math.min

/** Coordinates in the orientation-corrected source, independent of screen size and photo order. */
@Serializable
data class MomentCrop(val x: Double, val y: Double, val width: Double, val height: Double) {
    val valid: Boolean get() = listOf(x, y, width, height).all(Double::isFinite) &&
        x >= 0 && y >= 0 && width > 0 && height > 0 && width <= 1 && height <= 1 &&
        x + width <= 1.000001 && y + height <= 1.000001
    val full: Boolean get() = this == Full

    fun aspect(sourceAspect: Double): Double = sourceAspect * width / height

    /** Fingers move the image, so the visible source window moves in the opposite direction. */
    fun move(dx: Double, dy: Double): MomentCrop = copy(
        x = (x - dx * width).coerceIn(0.0, (1 - width).coerceAtLeast(0.0)),
        y = (y - dy * height).coerceIn(0.0, (1 - height).coerceAtLeast(0.0)))

    /** Zoom around a focal point in the visible window, clamped to a 4× crop without blank edges. */
    fun zoom(factor: Double, sourceAspect: Double, focalX: Double = .5, focalY: Double = .5): MomentCrop {
        if (!factor.isFinite() || factor <= 0) return this
        val base = centered(sourceAspect, aspect(sourceAspect))
        val newWidth = (width / factor).coerceIn(base.width / MaxZoom, base.width)
        val newHeight = (newWidth * height / width).coerceAtMost(1.0)
        return MomentCrop(
            (x + (width - newWidth) * focalX.coerceIn(0.0, 1.0)).coerceIn(0.0, (1 - newWidth).coerceAtLeast(0.0)),
            (y + (height - newHeight) * focalY.coerceIn(0.0, 1.0)).coerceIn(0.0, (1 - newHeight).coerceAtLeast(0.0)),
            newWidth, newHeight)
    }

    fun zoomLevel(sourceAspect: Double): Double = centered(sourceAspect, aspect(sourceAspect)).width / width

    companion object {
        val Full = MomentCrop(0.0, 0.0, 1.0, 1.0)
        const val MaxZoom = 4.0
        fun centered(sourceAspect: Double, targetAspect: Double): MomentCrop {
            require(sourceAspect.isFinite() && sourceAspect > 0 && targetAspect.isFinite() && targetAspect > 0)
            val width = min(1.0, targetAspect / sourceAspect)
            val height = min(1.0, sourceAspect / targetAspect)
            return MomentCrop((1 - width) / 2, (1 - height) / 2, width, height)
        }
    }
}
