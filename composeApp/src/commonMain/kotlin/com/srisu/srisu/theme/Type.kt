package com.srisu.srisu.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import androidx.compose.material3.Typography
import srisu.composeapp.generated.resources.Res
import srisu.composeapp.generated.resources.poppins_black
import srisu.composeapp.generated.resources.poppins_black_italic
import srisu.composeapp.generated.resources.poppins_bold
import srisu.composeapp.generated.resources.poppins_bold_italic
import srisu.composeapp.generated.resources.poppins_extra_bold
import srisu.composeapp.generated.resources.poppins_extra_bold_italic
import srisu.composeapp.generated.resources.poppins_extra_light
import srisu.composeapp.generated.resources.poppins_extra_light_italic
import srisu.composeapp.generated.resources.poppins_italic
import srisu.composeapp.generated.resources.poppins_light
import srisu.composeapp.generated.resources.poppins_light_italic
import srisu.composeapp.generated.resources.poppins_medium
import srisu.composeapp.generated.resources.poppins_medium_italic
import srisu.composeapp.generated.resources.poppins_regular
import srisu.composeapp.generated.resources.poppins_semi_bold
import srisu.composeapp.generated.resources.poppins_semi_bold_italic
import srisu.composeapp.generated.resources.poppins_thin_italic


@Composable
fun poppinsFontFamily() = FontFamily(
    Font(Res.font.poppins_italic, weight = FontWeight.Thin),
    Font(Res.font.poppins_thin_italic, weight = FontWeight.Thin, style = FontStyle.Italic),
    Font(Res.font.poppins_extra_light, weight = FontWeight.ExtraLight),
    Font(
        Res.font.poppins_extra_light_italic,
        weight = FontWeight.ExtraLight,
        style = FontStyle.Italic
    ),
    Font(Res.font.poppins_light, weight = FontWeight.Light),
    Font(Res.font.poppins_light_italic, weight = FontWeight.Light, style = FontStyle.Italic),
    Font(Res.font.poppins_regular, weight = FontWeight.Normal),
    Font(Res.font.poppins_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.poppins_medium, weight = FontWeight.Medium),
    Font(Res.font.poppins_medium_italic, weight = FontWeight.Medium, style = FontStyle.Italic),
    Font(Res.font.poppins_semi_bold, weight = FontWeight.SemiBold),
    Font(Res.font.poppins_semi_bold_italic, weight = FontWeight.SemiBold, style = FontStyle.Italic),
    Font(Res.font.poppins_bold, weight = FontWeight.Bold),
    Font(Res.font.poppins_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
    Font(Res.font.poppins_extra_bold, weight = FontWeight.ExtraBold),
    Font(
        Res.font.poppins_extra_bold_italic,
        weight = FontWeight.ExtraBold,
        style = FontStyle.Italic
    ),
    Font(Res.font.poppins_black, weight = FontWeight.Black),
    Font(Res.font.poppins_black_italic, weight = FontWeight.Black, style = FontStyle.Italic)
)

@Composable
fun appTypography() = Typography().run {

    val bodyFontFamily = poppinsFontFamily()
    val displayFontFamily = poppinsFontFamily()

    copy(
        displayLarge = displayLarge.copy(fontFamily = displayFontFamily),
        displayMedium = displayMedium.copy(fontFamily = displayFontFamily),
        displaySmall = displaySmall.copy(fontFamily = displayFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = displayFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = displayFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = displayFontFamily),
        titleLarge = titleLarge.copy(fontFamily = displayFontFamily),
        titleMedium = titleMedium.copy(fontFamily = displayFontFamily),
        titleSmall = titleSmall.copy(fontFamily = displayFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = bodyFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = bodyFontFamily),
        bodySmall = bodySmall.copy(fontFamily = bodyFontFamily),
        labelLarge = labelLarge.copy(fontFamily = bodyFontFamily),
        labelMedium = labelMedium.copy(fontFamily = bodyFontFamily),
        labelSmall = labelSmall.copy(fontFamily = bodyFontFamily)
    )
}