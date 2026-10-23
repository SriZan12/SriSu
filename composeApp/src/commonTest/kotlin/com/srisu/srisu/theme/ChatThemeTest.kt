package com.srisu.srisu.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.srisu.srisu.features.couplechat.data.ChatThemePreset
import kotlin.test.*

class ChatThemeTest {
    private fun contrast(a: Color,b: Color): Float {
        val first=a.luminance();val second=b.luminance()
        return (maxOf(first,second)+.05f)/(minOf(first,second)+.05f)
    }
    @Test fun textMetadataAndControlsMeetContrastAlongEveryGradient() {
        for(base in listOf(SriSuLightColorScheme,SriSuDarkColorScheme)) for(preset in ChatThemePreset.entries) {
            val colors=chatColors(preset,base)
            for(surface in (0..100).map {lerp(colors.outgoingStart,colors.outgoingEnd,it/100f)}+colors.incoming+colors.scheme.background) {
                for(ink in listOf(colors.text,colors.metadata,colors.receipt))
                    assertTrue(contrast(surface,ink)>=4.5f,"${preset.id}: ${contrast(surface,ink)}")
            }
            assertTrue(contrast(colors.scheme.primary,colors.scheme.onPrimary)>=4.5f)
        }
    }
}
