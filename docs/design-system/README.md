# SriSu Compose design system

Source: [Srisu → Design System](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=0-1),
inspected 2026-09-20. `figma-spec.json` preserves every written specification on that page,
including all swatches, scales, component variants and usage notes. The inspected page consists of
frames, with no local variables, text styles or component sets; the documentation supplies the token
names.

## Theme entry point

`SriSuTheme(darkTheme = isSystemInDarkTheme(), useSerifHeadings = false)` wraps `AppRoot` on Android
and iOS. Dynamic wallpaper colors are intentionally absent. `AppTheme` remains a small compatibility
wrapper for previews. Shared components inherit the surrounding theme rather than installing their
own theme.

Fonts live in `composeApp/src/commonMain/composeResources/font`, the Compose Multiplatform
equivalent of Android `res/font`. This makes the same bundled fonts available on both platforms
rather than introducing Android-only resource references into shared code. Satoshi Regular 400,
Medium 500 and Bold 700 come from Fontshare's official archive; Instrument Serif Regular 400 comes
from Google Fonts. Their licenses are included here. Existing Poppins resources are no longer
selected by the theme.

## Fidelity decisions

The page's written specification and rendered sample disagree in a few places. Written semantic
tokens and annotations take precedence, as required by the design-to-code workflow:

- Success is documented as `#3F9B65`; its fill is `#3F9B66`.
- Backdrop is documented as `#2C2C2A` at 45%; its rendered fill uses `#2D2C2B` at 45%.
- Typography explicitly describes **Satoshi as default** and **Instrument Serif as an optional
  pairing**. The current Figma preview has the serif pairing enabled. The default app uses the
  documented Satoshi scale. Set `useSerifHeadings = true` centrally to reproduce the serif preview:
  regular weight, 1.1 line height and −0.01em tracking for display/headline roles. UI titles and
  controls stay Satoshi.
- Specimen-board Menlo labels and the iOS SF Pro status-bar sample are documentation/system chrome,
  not the app's body font.
- The shared floating field uses a scoped M3 Typography with the component’s documented 17/13-point
  input/label sizes, preserving the global 16/14/12 body scale. It uses M3’s animated label, a
  minimum 64 dp field height, 12 dp corners and the specified caret/focus/error/disabled tokens.

## Material role mapping

| Figma token                   | Compose role                                           |
|-------------------------------|--------------------------------------------------------|
| background / field-background | surface, background, surfaceContainerLowest            |
| foreground                    | onSurface, onBackground                                |
| accent / focus                | primary                                                |
| white action content          | onPrimary                                              |
| surface (cards, rows)         | surfaceContainer, surfaceContainerLow                  |
| surface-secondary             | surfaceVariant, surfaceContainerHigh, primaryContainer |
| together-soft / moment        | secondaryContainer / secondary                         |
| Spark / light Spark           | tertiary / tertiaryContainer                           |
| muted                         | onSurfaceVariant                                       |
| border                        | outlineVariant                                         |
| driftwood                     | outline                                                |
| danger                        | error                                                  |
| backdrop                      | scrim; sheets use colorScheme.sheetScrim               |

Activity, status and illustration tokens extend `ColorScheme`, e.g.
`MaterialTheme.colorScheme.success`, `.onSuccess`, `.spark`, `.ask`, `.planner`, `.moments`,
`.reminder`, `.caret`. All seven complete brand ramps are retained with activity-based names in
`Color.kt`. Photography uses `.mediaBackground` / `.onMedia`, which intentionally stay a contrasting
pair in both modes. Artwork colors are retained exactly rather than automatically recoloring bitmap
assets.

Figma specifies only light mode. Dark role pairs are derived with the official Material Components
1.12.0 `TonalPalette` HCT implementation from the corresponding brand seeds.
Primary/secondary/tertiary/error use tones **80/20**, containers **30/90**; warm neutral surfaces
use **6/90**, and surface containers use **4/10/12/17/22**. Neutral variant roles use **30/80**.
This is a derived dark theme, not a designer-provided dark mode. `GenerateTones.java` and
`hct-tones.json` record reproducible inputs and outputs. No new runtime color-library dependency is
required.

## Typography mapping

| Figma               | M3             | Size / line height / tracking (sp) |
|---------------------|----------------|------------------------------------|
| Display LG          | displayLarge   | 72 / 79.2 / −2.16                  |
| Display             | displayMedium  | 52 / 58.76 / −1.56                 |
| Heading LG          | displaySmall   | 40 / 51.6 / −1                     |
| Heading             | headlineLarge  | 32 / 41.6 / −0.8                   |
| Heading SM          | headlineMedium | 24 / 31.92 / −0.6                  |
| Subheading          | headlineSmall  | 20 / 26.6 / −0.5                   |
| Section title       | titleLarge     | 20 / 26.6 / −0.5, bold             |
| Card title          | titleMedium    | 16 / 24 / −0.16, bold              |
| Small UI title      | titleSmall     | 14 / 21 / −0.14, medium            |
| Body                | bodyLarge      | 16 / 24 / −0.16                    |
| Body SM             | bodyMedium     | 14 / 21 / −0.14                    |
| Caption             | bodySmall      | 12 / 18 / −0.12                    |
| Large button        | labelLarge     | 16 / 24 / −0.08, bold              |
| Medium/small button | labelMedium    | 14 / 20 / −0.07, bold              |
| Eyebrow             | labelSmall     | 12 / 18 / 1.44, bold               |

## Shapes, spacing and components

M3 shapes are **4, 8, 16, 24, 32 dp** from extraSmall through extraLarge. Figma has seven shape
tokens, so the remaining exact **12 dp field** and **50% pill** are semantic `Shapes.field` and
`Shapes.pill` extensions in `Shape.kt`. All rounded-corner construction stays there. M3 sheets
inherit the 32 dp extraLarge top corners. Avatars retain CircleShape.

`MaterialTheme.spacing` exposes the 4/8/12/16/20/24/32/40/48/64 scale plus the specified 28 dp
section gap. Existing padding/gap uses of these values are migrated without changing content
dimensions indiscriminately. Screen gutters and primary action areas use the 20 dp token. Remaining
image sizes, positioning and feature-specific geometry are not spacing tokens.

`SriSuButton` implements all seven documented variants and three visual sizes: 32/36/40 dp, pill
corners, 12/16 dp horizontal inset, 45% disabled opacity and 14% soft-destructive fill. Existing
shared button entry points reuse it. M3's minimum interactive area and text growth remain enabled
for accessibility, so a button may occupy more layout height than the Figma specimen's visual
height. All feature layouts and navigation behavior are preserved; this is a theme migration, not a
rebuild of the Figma product screens or illustration assets.

## Verification

Run `python3 tools/theme/check_theme.py` for documented palette coverage, paired-role contrast, HCT
derivation and raw-token regressions. Compile with
`./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`.
Android Studio theme previews are in `ThemePreviews.kt`.

Sources: [Material HCT color schemes](https://github.com/material-foundation/material-color-utilities/blob/main/concepts/dynamic_color_scheme.md), [Satoshi](https://www.fontshare.com/fonts/satoshi), [Instrument Serif](https://github.com/google/fonts/tree/main/ofl/instrumentserif).
