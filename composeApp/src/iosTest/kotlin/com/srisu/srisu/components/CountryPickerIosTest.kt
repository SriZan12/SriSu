package com.srisu.srisu.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.srisu.srisu.theme.SriSuTheme
import com.srisu.srisu.utils.Country
import kotlinx.cinterop.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CountryPickerIosTest {
    private fun verify(width: Int,height: Int,scale: Float,dark: Boolean,phone: Boolean) = runSkikoComposeUiTest(
        size=Size(width*2f,height*2f),density=Density(2f,scale)) {
        var selected: String?=null; var closed=false
        setContent { SriSuTheme(darkTheme=dark) { CountryPickerContent(Country.getAllCountriesFromJson(),"NP",phone,{selected=it.code},{closed=true},Modifier.fillMaxSize()) } }
        onNodeWithTag("country-list").performScrollToNode(hasTestTag("country-ZW"))
        onNodeWithText("Zimbabwe").assertIsDisplayed()
        onNodeWithTag("country-search").performTextInput(if(phone) "+977" else "Saint Vincent")
        onNodeWithTag(if(phone) "country-NP" else "country-VC").assertIsDisplayed()
        onNodeWithTag("country-search").performImeAction()
        onNodeWithTag("country-search").assertIsNotFocused()
        capture("$width-$height-$scale-$phone",Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithTag(if(phone) "country-NP" else "country-VC").performClick()
        assertEquals(if(phone) "NP" else "VC",selected)
        onNodeWithTag("country-search").performTextReplacement("zzzzzz")
        onNodeWithText("No matching countries").assertIsDisplayed()
        onNodeWithContentDescription("Clear country search").performClick()
        onNodeWithText("Afghanistan").assertIsDisplayed()
        onNodeWithContentDescription("Close country picker").performClick();assertTrue(closed)
    }
    @Test fun standard() = verify(390,844,1f,false,true)
    @Test fun smallLargeTextDark() = verify(320,640,1.8f,true,false)
    @Test fun landscape() = verify(640,320,1f,false,true)
    @OptIn(ExperimentalForeignApi::class,BetaInteropApi::class)
    private fun capture(name: String,bytes: ByteArray) {
        val dir=NSTemporaryDirectory()+"country-picker"
        NSFileManager.defaultManager.createDirectoryAtPath(dir,true,null,null)
        val path="$dir/ios-$name.png"
        bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()).writeToFile(path,true) }
        println("COUNTRY_RENDER $path")
    }
}
