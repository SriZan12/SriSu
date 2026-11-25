package com.srisu.srisu.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.*

class DarkSurfaceTest {
    private fun contrast(a: Color, b: Color): Float {
        val x=a.luminance(); val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun homeAndQuestionDarkTextRemainsReadableOnEverySurface() {
        val p=DarkHomeSpace
        for(surface in listOf(p.paper,p.warm,p.lilac,p.pink,p.apricot,p.sky)) {
            assertTrue(surface.luminance()<.05f,"Dark surfaces must stay dark")
            for(text in listOf(p.ink,p.muted,p.purple)) assertTrue(contrast(surface,text)>=4.5f)
        }
        assertTrue(contrast(p.ink,p.paper)>=4.5f)
    }
    @Test fun darkSelectedSurfacesStayNeutralAndReadable() {
        val surface=SriSuDarkColorScheme.selectedSurface
        assertTrue(maxOf(surface.red,surface.green,surface.blue)-minOf(surface.red,surface.green,surface.blue)<.05f)
        assertTrue(contrast(surface,SriSuDarkColorScheme.onSelectedSurface)>=4.5f)
        assertEquals(SriSuLightColorScheme.secondaryContainer,SriSuLightColorScheme.selectedSurface)
    }
}
