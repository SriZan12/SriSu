# Home UI QA — 11 November 2026

Follow-up QA of the compact Home, starting from frontend
`1d1f8f268482406d037404c91cd47cc3fa3eccaa` on `codex/home-couple-space`.
Backend remains `3e276ad6a38b1afdd955d760be4519628bd1b3f7`; no API, backend or
migration changes. Existing Xcode edits and untracked design briefs are preserved.

## Findings and corrections

1. Wrapped Moments labels shifted the portraits vertically. A regression test
   reproduced tops at 445 and 415 rendered pixels on a 320 dp screen at 200% text.
   Statuses, portraits and labels now use shared rows. Alignment passes within
   one rendered pixel on Android and iOS; the curved connector remains.
2. A featured Plan added a redundant Shared ideas row. Removed it; Coming up's
   View all still opens Plans & ideas, including the agenda. A navigation test
   checks the retained route.
3. Long questions were squeezed beside decorative artwork. The illustration now
   yields its column when the actual measured question would exceed three lines
   beside it. The full question remains visible without truncation or reduced
   text scaling. Short questions retain the approved illustration and composition.

Recommendation: retain this composition. These are focused corrections rather
than a new design direction. Real-device review remains necessary before making
claims about installed rendering, accessibility services or interaction smoothness.

## Reference

Re-read live [Figma 238:193](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=238-193).
It still contains the older Home. The four approved attachments, Stage 2 preview
and later curved-connector request remain the visual authority; Figma was not
edited. This pass builds on the [matching-dimension refinement review](../refinement-2026-11-11/README.md).
Initials are deliberate synthetic profile fixtures; no private photos are used.

## Checks actually run after the final source change

| Check | Result |
| --- | --- |
| Full Android unit/render suite | 509 reported: 508 passed, one opt-in HTTP skip, zero failures/errors |
| Android Home suite, included above | 20 passed, no skips |
| iOS Home suite | 17 passed, no skips |
| Android and iOS simulator ARM64 compilation | Passed |
| Theme audit and diff whitespace | Passed |

Checks cover 320 × 700, 390 × 844 and 430 × 932 logical dimensions, 200% text,
scrolling, quiet/unpaired/invitation/new-couple/loading/failure states, long names
and questions, incoming and waiting Plans, partner ideas and a stale question.
Action assertions cover the retained agenda entry, chat nudge and idea interest.
These are render/component checks, not a new live-backend or end-to-end run.
See [machine-readable results](verification.json).

Android uses Robolectric API 35 native graphics. iOS uses Kotlin/Native Skiko
Compose in **SriSu Couple Chat Evaluation, iOS 26.5**. The captures are content
renders at 2x, not installed-app screenshots. Android OS nonlinear text scaling
and iOS font metrics can produce different wrapping; text is not forced to match
by suppressing accessibility scaling. Robolectric shadows differ from GPU output.

| Reviewed capture | Android | iOS |
| --- | --- | --- |
| Default Home | [Top](android-spark-1.0-390-top.png), [bottom](android-spark-bottom.png) | [Top](ios-spark-390-1.0.png) |
| Portrait alignment, 320 dp / 200% text | [Before](android-portraits-before.png), [after](android-quiet-2.0-320-top.png) | [After](ios-quiet-320-2.0.png) |
| Long content, 320 dp | [Header](android-long-content-1.0-320-top.png), [question](android-long-question.png) | [Question](ios-long-content-320-1.0-detail.png) |
| Incoming Plan, 320 dp / 200% text | [Actions](android-incoming-large.png) | [Actions](ios-incoming-plan-320-2.0-detail.png) |
| Partner idea | — | [Me too](ios-partner-idea-390-1.0-detail.png) |

Waiting-Plan and stale-question captures were also inspected from test outputs.
No overlap or missing primary labels was found in these inspected states.

## Remaining verification limits

The Android emulator/system image is still unavailable. No installed Android,
fresh signed iOS app, keyboard/Home-to-chat journey, physical-device,
VoiceOver/TalkBack, frame-timing or live-backend run was performed in this QA pass.
The [9 November native checks](../README.md) remain historical evidence, not
coverage of this revision. No universal-phone or pixel-identical claim is made.
No text-entry flow, animation, messaging behavior or privacy contract changed.

## Reproduce

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
./gradlew --offline --no-configuration-cache \
  -Pkotlin.native.home=/Users/srijan/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.21 \
  :composeApp:testDebugUnitTest \
  :composeApp:compileDebugKotlinAndroid \
  :composeApp:compileKotlinIosSimulatorArm64 \
  :composeApp:iosSimulatorArm64Test \
  --device 0055683D-7DBA-44C7-855D-4AA64123C522 \
  --tests 'com.srisu.srisu.features.home.HomeIosRenderTest'
python3 tools/theme/check_theme.py
git diff --check
```

Use an available simulator and matching installed Kotlin/Native distribution.
Offline mode requires cached dependencies. The test filter applies to the iOS
task; the Android task runs its full unit suite.
