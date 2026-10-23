package com.srisu.srisu.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Decorative artwork derived from the Moments ramp; independent of the selected chat preset. */
@Immutable internal data class RomanticColors(val blush: Color, val rose: Color, val ruby: Color, val glint: Color)
internal val ColorScheme.romanticColors: RomanticColors get() = RomanticColors(
    blush=if(background.luminance()<.5f) SriSuPalette.Moments300 else SriSuPalette.Moments200,
    rose=SriSuPalette.Moments500,
    ruby=lerp(SriSuPalette.Moments600,LightError,.30f),
    glint=SriSuPalette.Moments50,
)
