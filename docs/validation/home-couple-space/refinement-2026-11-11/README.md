# Home visual refinement — 11 November 2026

This pass addresses oversized controls/spacing and the straight partner link.
Frontend branch: `codex/home-couple-space`, starting at
`8cb5e117f7f4a78712ae679d626fd337b5bb4bca`. Backend remains at
`3e276ad6a38b1afdd955d760be4519628bd1b3f7`; no backend, contract or migration changes.
Existing Xcode project edits and untracked design briefs were preserved.

## Reference and corrections

Re-read live [Figma 238:193](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=238-193),
including its screenshot. It still shows the older Home. The approved four
attachments and Stage 2 source remain the visual authority. The requested curve
supersedes the straight connector in that browser preview. Figma was not edited.

Reference source:
`/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/srisu-home-stage-two.html`.

- Retain the 24 sp main heading; remove surplus header button padding by making
  the title/subtitle one accessible profile target.
- Use 22 dp gutters, 17 dp below 360 dp; reduce accumulated section spacing.
- Use 76 dp portraits on compact phones and 90 dp at ordinary widths. Center the
  pair with the reference's fixed middle column rather than three weighted columns.
- Draw a responsive dashed quadratic arch between the inner portrait edges.
  The date pill rests near its crest; its smaller visible shape retains a 48 dp
  touch area. Status bubbles also retain 48 dp minimum touch height.
- Match 11 sp secondary text, a 44 dp answer strip, tighter question spacing and
  20 dp default navigation icons. Tab hit areas and enlarged-text reflow remain.
- Keep retry spacing in the stale-question case. Let long couple titles wrap
  while reserving space for the profile arrow.
- Make render/instrumentation fixtures use the production shell's zero scaffold
  insets and consumed bottom padding, avoiding duplicate test-only safe areas.

Existing actions, private-answer omission, data sources and navigation routes
are preserved. Pure chat, Quick Share and Palette components were not changed.
The shared tab bar's compact default styling also appears on its other root tabs.

## Checks actually run

| Check | Result |
| --- | --- |
| Full Android unit/render suite | 502 reported; 501 passed, one opt-in HTTP test skipped |
| Final forced Android Home rerun | 13 passed, zero failures/skips |
| Final forced iOS Home rerun | 12 passed, zero failures/skips |
| Android application / instrumentation compilation | Passed |
| iOS simulator ARM64 compilation | Passed |
| Theme audit and diff whitespace | Passed |

The final focused rerun rebuilt both platforms after the last layout corrections.
See [machine-readable counts and capture dimensions](verification.json).

Android captures use Robolectric API 35 native graphics. iOS captures use the
Kotlin/Native Skiko Compose runtime in **SriSu Couple Chat Evaluation, iOS 26.5**.
These are synthetic content-only renders at 2x, not installed-app screenshots.
The initial missing dependency cache was restored. Kotlin/Native 2.3.21 was
explicitly selected from the already installed distribution; no dependency
versions or repository configuration were changed.

## Visual review

Inspected against the approved preview at matching 390 × 844 and 320 × 700
logical dimensions, accounting for the browser reference's phone/status-bar
chrome. Added 430 × 932 coverage. Verified the curve, smaller controls, no-partner
and quiet states, large-text wrapping and scrolling to the final statistics.
The demo portraits are intentionally replaced by initials in test fixtures.

| State | Android | iOS |
| --- | --- | --- |
| Standard, 390 × 844 | [Top](android-spark-1.0-390-top.png), [bottom](android-spark-bottom.png) | [Top](ios-spark-390-1.0.png) |
| Compact, 320 × 700 | [Top](android-spark-1.0-320-top.png) | [Top](ios-spark-320-1.0.png) |
| Wide, 430 × 932 | [Message](android-message-1.0-430-top.png) | [Message](ios-message-430-1.0.png) |
| Quiet day | [Top](android-quiet-1.0-390-top.png) | [Top](ios-quiet-390-1.0.png) |
| No partner | [Top](android-unpaired-1.0-390-top.png) | [Top](ios-unpaired-390-1.0.png) |
| 320 × 700, 200% text | [Top](android-quiet-2.0-320-top.png), [bottom](android-large-bottom.png) | [Top](ios-quiet-320-2.0.png) |

Remaining differences: platform font metrics/line breaks and shadows, native
icons/illustration, actual content and truthful state labels. Robolectric's
captures do not show the same elevation shadows as the iOS renderer; they are
not evidence of installed Android GPU rendering. No pixel-identical or universal
phone coverage is claimed. Large text intentionally grows and scrolls.

Fresh installed Android verification could not run: the emulator/system image
were absent and the SDK installation stalled; it was stopped. The instrumented
test source compiles, but its four device cases were not rerun for this pass.
No fresh signed iOS app/keyboard, physical-device, live-backend, screen-reader or
frame-timing run was performed. Earlier checks remain dated in the parent record.
No animation or text-entry flow changed.

## Reproduce the focused final checks

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
./gradlew --offline --no-configuration-cache --rerun-tasks \
  -Pkotlin.native.home=/Users/srijan/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.21 \
  :composeApp:testDebugUnitTest --tests 'com.srisu.srisu.home.HomeRenderTest' \
  :composeApp:compileKotlinIosSimulatorArm64 \
  :composeApp:iosSimulatorArm64Test \
  --device 0055683D-7DBA-44C7-855D-4AA64123C522 \
  --tests 'com.srisu.srisu.features.home.HomeIosRenderTest'
```

Offline mode requires the dependencies to have been downloaded first. Use the
available simulator UUID and installed matching Kotlin/Native distribution.
