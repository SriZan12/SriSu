package com.srisu.srisu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SriSuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Figma has seven corner tokens; M3 has five slots. Preserve the remaining tokens
// as semantic Shapes extensions instead of changing their measured radii.
private val FieldShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(50)
val Shapes.field: RoundedCornerShape get() = FieldShape
val Shapes.pill: RoundedCornerShape get() = PillShape
