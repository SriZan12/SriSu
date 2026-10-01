package com.srisu.srisu.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Source: Srisu / Design System, node 0:1. Written semantic tokens take precedence
// over divergent rendered swatches. Dark companions: Material HCT, see hct-tones.json.
internal val LightPrimary = Color(0xFF2D2C2B)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFEFE9E6)
internal val LightOnPrimaryContainer = Color(0xFF2D2C2B)
internal val LightSecondary = Color(0xFF3B197F)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFECE3EA)
internal val LightOnSecondaryContainer = Color(0xFF3B197F)
internal val LightTertiary = Color(0xFF8144A8)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFEFE8F5)
internal val LightOnTertiaryContainer = Color(0xFF361948)
internal val LightBackground = Color(0xFFFFFFFF)
internal val LightOnBackground = Color(0xFF2D2C2B)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF2D2C2B)
internal val LightSurfaceVariant = Color(0xFFEFE9E6)
internal val LightOnSurfaceVariant = Color(0xFF63605D)
internal val LightOutline = Color(0xFFC6C1B9)
internal val LightOutlineVariant = Color(0xFFE2DED9)
internal val LightError = Color(0xFFD73431)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410003)
internal val LightInverseSurface = Color(0xFF2D2C2B)
internal val LightInverseOnSurface = Color(0xFFF6F1EF)
internal val LightInversePrimary = Color(0xFFC9C6C4)
internal val LightSurfaceTint = Color(0xFF2D2C2B)
internal val LightScrim = Color(0xFF2C2C2A)
internal val LightSurfaceDim = Color(0xFFE2DED9)
internal val LightSurfaceBright = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF6F1EF)
internal val LightSurfaceContainer = Color(0xFFF6F1EF)
internal val LightSurfaceContainerHigh = Color(0xFFEFE9E6)
internal val LightSurfaceContainerHighest = Color(0xFFE2DED9)

val SriSuLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceTint = LightSurfaceTint,
    scrim = LightScrim,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

internal val DarkPrimary = Color(0xFFC9C6C4)
internal val DarkOnPrimary = Color(0xFF31302F)
internal val DarkPrimaryContainer = Color(0xFF484645)
internal val DarkOnPrimaryContainer = Color(0xFFE6E2E0)
internal val DarkSecondary = Color(0xFFD0BCFF)
internal val DarkOnSecondary = Color(0xFF3A177E)
internal val DarkSecondaryContainer = Color(0xFF513395)
internal val DarkOnSecondaryContainer = Color(0xFFE9DDFF)
internal val DarkTertiary = Color(0xFFE4B5FF)
internal val DarkOnTertiary = Color(0xFF4D0774)
internal val DarkTertiaryContainer = Color(0xFF66288C)
internal val DarkOnTertiaryContainer = Color(0xFFF4D9FF)
internal val DarkError = Color(0xFFFFB4AC)
internal val DarkOnError = Color(0xFF690007)
internal val DarkErrorContainer = Color(0xFF93000E)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkBackground = Color(0xFF141312)
internal val DarkOnBackground = Color(0xFFE6E1E0)
internal val DarkSurface = Color(0xFF141312)
internal val DarkOnSurface = Color(0xFFE6E1E0)
internal val DarkInverseSurface = Color(0xFFE6E1E0)
internal val DarkInverseOnSurface = Color(0xFF32302F)
internal val DarkSurfaceDim = Color(0xFF141312)
internal val DarkSurfaceBright = Color(0xFF3B3938)
internal val DarkSurfaceContainerLowest = Color(0xFF0F0E0D)
internal val DarkSurfaceContainerLow = Color(0xFF1C1B1A)
internal val DarkSurfaceContainer = Color(0xFF211F1E)
internal val DarkSurfaceContainerHigh = Color(0xFF2B2A29)
internal val DarkSurfaceContainerHighest = Color(0xFF363433)
internal val DarkSurfaceVariant = Color(0xFF494644)
internal val DarkOnSurfaceVariant = Color(0xFFCAC6C2)
internal val DarkOutline = Color(0xFF94908D)
internal val DarkOutlineVariant = Color(0xFF494644)
internal val DarkInversePrimary = Color(0xFF605E5D)
internal val DarkSurfaceTint = Color(0xFFC9C6C4)
internal val DarkScrim = Color(0xFF2C2C2A)

val SriSuDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceTint = DarkSurfaceTint,
    scrim = DarkScrim,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

/** Reference ramps are preserved exactly; UI code uses semantic ColorScheme extensions. */
internal object SriSuPalette {
    val Spark50 = Color(0xFFF7F4FA)
    val Spark100 = Color(0xFFEFE8F5)
    val Spark200 = Color(0xFFE0D1EC)
    val Spark300 = Color(0xFFC9B0DC)
    val Spark400 = Color(0xFFA67DC4)
    val Spark500 = Color(0xFF8144A8)
    val Spark600 = Color(0xFF673587)
    val Spark700 = Color(0xFF4E2667)
    val Spark800 = Color(0xFF361948)
    val Spark900 = Color(0xFF1D0A28)
    val Ask50 = Color(0xFFF3F8FD)
    val Ask100 = Color(0xFFE6F2FB)
    val Ask200 = Color(0xFFCEE5F7)
    val Ask300 = Color(0xFFA9D1F1)
    val Ask400 = Color(0xFF6DB1E6)
    val Ask500 = Color(0xFF0A8FD8)
    val Ask600 = Color(0xFF0672AE)
    val Ask700 = Color(0xFF035785)
    val Ask800 = Color(0xFF023C5F)
    val Ask900 = Color(0xFF012137)
    val Planner50 = Color(0xFFFEFAF3)
    val Planner100 = Color(0xFFFDF4E8)
    val Planner200 = Color(0xFFFBEAD0)
    val Planner300 = Color(0xFFF7D9AD)
    val Planner400 = Color(0xFFF1C072)
    val Planner500 = Color(0xFFE9A300)
    val Planner600 = Color(0xFFBC8200)
    val Planner700 = Color(0xFF906300)
    val Planner800 = Color(0xFF674600)
    val Planner900 = Color(0xFF3C2700)
    val Reminder50 = Color(0xFFFFF8F4)
    val Reminder100 = Color(0xFFFFF0E8)
    val Reminder200 = Color(0xFFFFE1D1)
    val Reminder300 = Color(0xFFFFCAAF)
    val Reminder400 = Color(0xFFFBA678)
    val Reminder500 = Color(0xFFF47D31)
    val Reminder600 = Color(0xFFC56325)
    val Reminder700 = Color(0xFF974B1A)
    val Reminder800 = Color(0xFF6C3410)
    val Reminder900 = Color(0xFF3F1B06)
    val Moments50 = Color(0xFFFFF6F9)
    val Moments100 = Color(0xFFFFEEF2)
    val Moments200 = Color(0xFFFFDCE6)
    val Moments300 = Color(0xFFFEC2D3)
    val Moments400 = Color(0xFFF999B7)
    val Moments500 = Color(0xFFEF6A9A)
    val Moments600 = Color(0xFFC0547B)
    val Moments700 = Color(0xFF943F5E)
    val Moments800 = Color(0xFF6A2B42)
    val Moments900 = Color(0xFF3D1624)
    val Emphasis50 = Color(0xFFF1F1F8)
    val Emphasis100 = Color(0xFFE4E3F1)
    val Emphasis200 = Color(0xFFCAC7E2)
    val Emphasis300 = Color(0xFFA49ECC)
    val Emphasis400 = Color(0xFF6D60A8)
    val Emphasis500 = Color(0xFF3B197F)
    val Emphasis600 = Color(0xFF2E1265)
    val Emphasis700 = Color(0xFF210B4C)
    val Emphasis800 = Color(0xFF150535)
    val Emphasis900 = Color(0xFF08021C)
    val Action50 = Color(0xFFF1F1F1)
    val Action100 = Color(0xFFE3E3E3)
    val Action200 = Color(0xFFC8C7C7)
    val Action300 = Color(0xFFA09F9F)
    val Action400 = Color(0xFF656564)
    val Action500 = Color(0xFF2D2C2B)
    val Action600 = Color(0xFF222121)
    val Action700 = Color(0xFF181716)
    val Action800 = Color(0xFF0E0D0D)
    val Action900 = Color(0xFF050504)
}

@Immutable
internal data class SriSuExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val spark: Color,
    val ask: Color,
    val planner: Color,
    val moments: Color,
    val reminder: Color,
    val caret: Color,
    val textSecondary: Color,
    val disabledOutline: Color,
    val illustrationSun: Color,
    val illustrationPartner: Color,
    val illustrationMist: Color,
    val illustrationSky: Color,
    val illustrationHaze: Color,
    val illustrationDusk: Color,
    val illustrationLeafLight: Color,
    val illustrationLeaf: Color,
    val illustrationLeafDark: Color,
    val illustrationLeafDeep: Color,
    val illustrationBlush: Color,
    val illustrationCheek: Color,
    val illustrationInk: Color,
)

internal val LightExtendedColors = SriSuExtendedColors(
    onSuccess = LightPrimary,
    success = Color(0xFF3F9B65),
    warning = Color(0xFFFFCE00),
    spark = Color(0xFF8144A8),
    ask = Color(0xFF0A8FD8),
    planner = Color(0xFFE9A300),
    moments = Color(0xFFEF6A9A),
    reminder = Color(0xFFF47D31),
    caret = Color(0xFF00A4FF),
    textSecondary = Color(0xFF44423F),
    disabledOutline = Color(0xFFD0D0D0),
    illustrationSun = Color(0xFFFFCDB3),
    illustrationPartner = Color(0xFFDBCAE8),
    illustrationMist = Color(0xFFEFE8F5),
    illustrationSky = Color(0xFFD0EAFF),
    illustrationHaze = Color(0xFFCBD5ED),
    illustrationDusk = Color(0xFFB6C3E5),
    illustrationLeafLight = Color(0xFFAAB9E0),
    illustrationLeaf = Color(0xFF8EA1D4),
    illustrationLeafDark = Color(0xFF7389C8),
    illustrationLeafDeep = Color(0xFF6078BF),
    illustrationBlush = Color(0xFFFFCCE3),
    illustrationCheek = Color(0xFFFFBEDB),
    illustrationInk = Color(0xFF301E6B),
)

internal val DarkExtendedColors = SriSuExtendedColors(
    onSuccess = Color(0xFF00391D),
    success = Color(0xFF7ED99E),
    warning = Color(0xFFEFC100),
    spark = Color(0xFFE4B5FF),
    ask = Color(0xFF94CCFF),
    planner = Color(0xFFFFBA39),
    moments = Color(0xFFFFB1C7),
    reminder = Color(0xFFFFB68F),
    caret = Color(0xFF98CBFF),
    textSecondary = Color(0xFFCAC6C2),
    disabledOutline = Color(0xFF494644),
    illustrationSun = Color(0xFFFFCDB3),
    illustrationPartner = Color(0xFFDBCAE8),
    illustrationMist = Color(0xFFEFE8F5),
    illustrationSky = Color(0xFFD0EAFF),
    illustrationHaze = Color(0xFFCBD5ED),
    illustrationDusk = Color(0xFFB6C3E5),
    illustrationLeafLight = Color(0xFFAAB9E0),
    illustrationLeaf = Color(0xFF8EA1D4),
    illustrationLeafDark = Color(0xFF7389C8),
    illustrationLeafDeep = Color(0xFF6078BF),
    illustrationBlush = Color(0xFFFFCCE3),
    illustrationCheek = Color(0xFFFFBEDB),
    illustrationInk = Color(0xFF301E6B),
)

internal val LocalSriSuColors = staticCompositionLocalOf { LightExtendedColors }

val ColorScheme.success: Color
    @Composable get() = LocalSriSuColors.current.success
val ColorScheme.warning: Color
    @Composable get() = LocalSriSuColors.current.warning
val ColorScheme.spark: Color
    @Composable get() = LocalSriSuColors.current.spark
val ColorScheme.ask: Color
    @Composable get() = LocalSriSuColors.current.ask
val ColorScheme.planner: Color
    @Composable get() = LocalSriSuColors.current.planner
val ColorScheme.moments: Color
    @Composable get() = LocalSriSuColors.current.moments
val ColorScheme.reminder: Color
    @Composable get() = LocalSriSuColors.current.reminder
val ColorScheme.caret: Color
    @Composable get() = LocalSriSuColors.current.caret
val ColorScheme.textSecondary: Color
    @Composable get() = LocalSriSuColors.current.textSecondary
val ColorScheme.disabledOutline: Color
    @Composable get() = LocalSriSuColors.current.disabledOutline
val ColorScheme.illustrationSun: Color
    @Composable get() = LocalSriSuColors.current.illustrationSun
val ColorScheme.illustrationPartner: Color
    @Composable get() = LocalSriSuColors.current.illustrationPartner
val ColorScheme.illustrationMist: Color
    @Composable get() = LocalSriSuColors.current.illustrationMist
val ColorScheme.illustrationSky: Color
    @Composable get() = LocalSriSuColors.current.illustrationSky
val ColorScheme.illustrationHaze: Color
    @Composable get() = LocalSriSuColors.current.illustrationHaze
val ColorScheme.illustrationDusk: Color
    @Composable get() = LocalSriSuColors.current.illustrationDusk
val ColorScheme.illustrationLeafLight: Color
    @Composable get() = LocalSriSuColors.current.illustrationLeafLight
val ColorScheme.illustrationLeaf: Color
    @Composable get() = LocalSriSuColors.current.illustrationLeaf
val ColorScheme.illustrationLeafDark: Color
    @Composable get() = LocalSriSuColors.current.illustrationLeafDark
val ColorScheme.illustrationLeafDeep: Color
    @Composable get() = LocalSriSuColors.current.illustrationLeafDeep
val ColorScheme.illustrationBlush: Color
    @Composable get() = LocalSriSuColors.current.illustrationBlush
val ColorScheme.illustrationCheek: Color
    @Composable get() = LocalSriSuColors.current.illustrationCheek
val ColorScheme.illustrationInk: Color
    @Composable get() = LocalSriSuColors.current.illustrationInk

val ColorScheme.transparent: Color get() = surface.copy(alpha = 0f)
val ColorScheme.mediaBackground: Color get() = LightPrimary
val ColorScheme.onMedia: Color get() = LightOnPrimary
val ColorScheme.sheetScrim: Color get() = scrim.copy(alpha = 0.45f)

val ColorScheme.onSuccess: Color
    @Composable get() = LocalSriSuColors.current.onSuccess
