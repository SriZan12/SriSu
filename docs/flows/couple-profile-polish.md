# Couple Profile presentation refinement — 2026-10-02

## Scope and baseline

Frontend: SriZan12/SriSu, `dev-core-architecture`, HEAD
`00a198a3e579d32eeb578942bd2c86936257e2c5`. Local and live remote heads matched;
there is no `dev-core` branch. The worktree was clean before this task. No branch
switch, dependency change, commit or push was performed.

The BaseNavigation work is still in the separate navigation PR, not this core
checkout. This change preserves the existing CoupleProfileScreen entry signature,
ViewModel ownership and navigation callbacks so it can remain a presentation-only
change. No navigation refactor was pulled into the task.

The backend checkout is on `codex/modular-navigation-retire-singles` at
`a9db211e0b6c5f9ba55c11670321d01dbf458ac4` and remains untouched. Backend contracts,
permissions, revisions, publication consent, Faves and persistence are unchanged.
The frontend's pinned core-1 digest remains
`31ad507155ad95072d8e71d06e8a0bbb5566179e845f0533c5d693f3574a1047`.

## Reference and observed issues

Design foundation: [Couple UI final, page 1:4](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-4),
and the user's full-resolution `Couple Profile UI Final.png` (11661 × 23196).
Both current Figma context and screenshot requests were rejected by the Starter-plan
call limit. The supplied board was inspected at readable crops, including the
populated profile and song editor; no current child IDs, component properties or
prototype interactions could be verified. No Figma modification/export occurred.

The baseline was rendered using the existing Android Robolectric/native-graphics
harness, with synthetic API fixtures. The first pass identified these issues:

| Observation | Implemented refinement | Purpose |
|---|---|---|
| Long preview text crowds the centered toolbar title | Accessible preview icon and options menu | Restore title hierarchy while retaining both actions |
| Separate cover, avatars and identity feel loosely arranged | Shallower 2.2:1 cover, correct circular overlapping portraits, grouped date, person placeholders | Keep the identity together and useful content nearby |
| Every element inherits the same page gap | Explicit section groups, close heading/content relationship, consistent section separation | Make reading order apparent |
| Story Edit occupies an extra text row | Trailing pencil with named action and normal IconButton touch target; grouped answers and dividers | Keep controls close without interrupting the answer |
| Read-only interests look disabled | Non-interactive, full-contrast labels; shared selections use existing together color | Distinguish shared and individual data without implying an unavailable action |
| Editors use default square fields and duplicated Save controls | Existing field shapes, semantic headings, selected quick-answer chips, one persistent toolbar Save | Make the separate forms coherent; sheet/plan keep their single local action |
| Editors inherit overview scroll and refresh moves content | Independent overview/editor scroll, reserved progress space, stable media placeholders | Preserve reading position and controls during transitions |

Additional refinements: cover preview uses the same ratio as the displayed cover;
date/time choices include text labels and icons; sharing rows are wholly toggleable;
plan response actions wrap; content width is capped at 600 dp for larger windows;
toolbar height scales with text. Dark card surfaces use the existing surfaceContainer
token instead of the much brighter surfaceVariant, scoped only to Couple Profile.

The existing Satoshi/Instrument Serif pairing, 20 dp gutters, theme spacing and shape
scale remain. The initial refinement added no animation, decorative gradients,
fake product data or new network requests; the requested motion follow-up is recorded
below. The cover action has an opaque semantic surface for contrast over
photos, and identity text sits below the image. Avatar decoding is bounded to 192 px;
cover requests keep the existing 1024 px bound. No performance improvement is claimed
without measurement.

Accessibility references: [Compose API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
for touch targets, labelled icons and parent-owned checkbox semantics. Apple layout
guidance was consulted, but its web response did not expose enough content to claim
additional platform-specific evidence. [Coil image-loader guidance](https://coil-kt.github.io/coil/image_loaders/)
was used to instrument actual image completion in tests; production loader ownership
and private-media cache policy are unchanged.

## Changed components and assets

- `presentation/ProfileOverview.kt`: scoped identity, metrics, story, song, interest,
  plan-summary and authorized-media presentation helpers.
- `presentation/CoupleProfileScreen.kt`: screen chrome, progress/error presentation,
  scroll ownership and existing story/song/interests/cover/sharing/plan editors.
- `presentation/ProfileDateFields.kt`: labelled date/time selection rows; original
  picker and date validation behavior retained.
- `theme/CoupleProfileTokens.kt`: Couple Profile-only dark surface adjustment.
- `composeResources/values/couple_profile_strings.xml`: resource-based action labels,
  image descriptions, section context and empty-state copy.
- `CoupleProfileRenderTest.kt`: 30 render/interaction scenarios, using the existing
  mock transport and real ViewModel/repository. No new test dependency.

Reused assets: onboarding_landscape; Satoshi and Instrument Serif fonts; existing
Material icons; SriSuButton and theme tokens. No new image export. Existing will.jpg
is used only as a test transport fixture to exercise decoding/cropping; it is never
a production default cover. All names/counts in screenshots are synthetic fixtures.

## Before and after

| Screen | Baseline | Refined |
|---|---|---|
| Member profile | [Before](couple-profile-polish/before/owner.png) | [After](couple-profile-polish/after/owner-populated.png) |
| Song editor | [Before](couple-profile-polish/before/song.png) | [After](couple-profile-polish/after/song-editor.png) |

Additional inspected renders:

- [Song and interests](couple-profile-polish/after/profile-sections.png)
- [Private visitor](couple-profile-polish/after/visitor-private.png), [permitted visitor](couple-profile-polish/after/visitor-published.png)
- [320 dp / 1.6× text / long names](couple-profile-polish/after/owner-long-small.png), [600 dp width](couple-profile-polish/after/owner-wide.png)
- [Dark profile](couple-profile-polish/after/owner-dark.png), [dark large-text editor](couple-profile-polish/after/song-dark-large-text.png)
- [Story reading](couple-profile-polish/after/story-read.png), [story editor](couple-profile-polish/after/story-editor.png), [interests](couple-profile-polish/after/interests-editor.png), [cover](couple-profile-polish/after/cover-editor.png), [positioning](couple-profile-polish/after/cover-position.png), [sharing](couple-profile-polish/after/sharing-editor.png)
- [Date](couple-profile-polish/after/date-editor.png), [new plan](couple-profile-polish/after/new-plan.png)
- [Failed save with retained draft](couple-profile-polish/after/song-failed-save.png), [pending save](couple-profile-polish/after/song-saving.png), [failed image fallback](couple-profile-polish/after/photo-failed.png)

Renders were inspected and iterated. The first pass exposed avatar compression;
fixed before final capture. The first image test captured the placeholder before
asynchronous decoding; a Coil completion listener now verifies successful decoding
before capture, and an invalid-byte fixture verifies failure fallback. Test selectors
were corrected for the preview's accessible icon and duplicate synthetic song text;
Robolectric's main looper is advanced while waiting for asynchronous failure state.
No production permissions or tests were disabled to pass checks.

## Validation

Commands use `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
iOS commands also use `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

| Check | Outcome |
|---|---|
| Baseline `./gradlew :composeApp:testDebugUnitTest --tests '*CoupleProfileRenderTest'` | Passed, 15 existing scenarios |
| Final `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:assembleDebug` | Passed: 108 tests total, 107 passed, 1 existing opt-in live-backend test skipped; Android APK and iOS shared compilation succeeded |
| Included render/interaction suite | 30 passed: member/visitor/preview, long names, small/large screens, dark/large text, missing/decoded/failed image, empty editors, retained failed draft, pending save, unsaved changes, stable refresh and editor return |
| `python3 tools/theme/check_theme.py` | Passed: retained 96 colors, 24 contrast pairs, no raw-style violations |
| `python3 tools/core_contracts.py` | Passed: pinned contract and generated fixtures unchanged |
| `git diff --check` | Passed |
| `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/srisu-polish-xcode CODE_SIGNING_ALLOWED=NO build` | Passed: final actual app/framework link, including visitor empty-state wording |
| Live backend and backend test suites | Not run for this presentation-only task; no API/data/domain changes |
| Physical Android/iOS, system keyboard/insets, VoiceOver/TalkBack traversal, camera/gallery and iOS visual comparison | Not run; Robolectric renders and compilation are not device validation |

Existing Gradle deprecation/expect-actual warnings remain. Live Figma metadata and
prototype inspection remain quota-blocked. Modal screenshots capture the dialog
surface rather than the underlying activity; no pixel-exact or device-gesture claim.

## Brief profile accents — requested follow-up

The existing Days together heart now performs one restrained double beat (16% then
9% growth, settled by 580 ms). The existing Visible Moments sparkle follows with a
14% growth and 7-degree turn, settling by 820 ms. The calendar, numbers, labels and
layout stay still. No repeated timer, particles, opacity flashing or animated counts.
This motion is a user-requested refinement, not an animation extracted from Figma.

`ProfileAccentMotion.kt` owns a finite Compose animation sequence. Its saveable
consumed state is hoisted in CoupleProfileScreen and scoped to the account and actual
profile ID. It plays when at least 75% of the statistics row enters the clipped
viewport while the screen is resumed. Refresh/recomposition, scrolling back,
preview/editor return and saved-state restoration do not replay it. A new profile
visit can play once. Leaving the viewport, opening the answer sheet or losing the
resumed lifecycle cancels it into its resting state. Existing permissions still
determine whether the heart statistic is present for a visitor.

Only icon graphics layers change, preserving layout and accessibility labels.
The existing Compose [MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)
is honored; scale zero skips the sequence and consumes that entrance. The installed
Compose Multiplatform 1.8.2 UIKit source maps iOS Reduce Motion to this scale, so no
new platform adapter or dependency was added. Platform setting toggles have not
been manually exercised on devices.

`ProfileAccentMotionTest.kt` exercises finite playback, refreshed values and editor
return, off-screen entry/cancellation, saved-state restoration, account/profile
switching, reduced motion, and a covered profile. The tests use a controlled frame
clock; assertions wait for Android layout before advancing animation time. Rendered
rest/heart-peak/Moments-peak frames were inspected with synthetic statistics. Tests
caught a visibility reset on account switching, fixed by keeping visibility with
the layout node while the consumed state remains scoped to the account/profile.

No backend/API changes, new assets, dependency changes, commit, push or Figma write.
Earlier local refinement changes remain intact on `dev-core-architecture`.

Inspected motion frames: [rest](couple-profile-polish/after/motion-rest.png),
[heart beat](couple-profile-polish/after/motion-heart-beat.png),
[Moments twinkle](couple-profile-polish/after/motion-moments-twinkle.png).
These are actual Compose renders of the statistics component, not device recordings.

Follow-up verification:

- `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:assembleDebug`
  passed: 115 total tests, 114 passed, one pre-existing opt-in live-backend test
  skipped. All seven new animation tests and 30 existing profile render/interaction
  scenarios passed. Android APK and iOS shared compilation succeeded.
- `python3 tools/theme/check_theme.py` and `git diff --check` passed.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/srisu-polish-xcode CODE_SIGNING_ALLOWED=NO build`
  passed after the motion changes, including the actual iOS app/framework link.
- Native device motion, live backend, VoiceOver/TalkBack traversal and iOS rendered
  motion were not exercised. The earlier Figma quota limitation remains.

## Publication authorization

After the implementation and validation above, the user requested that all pending
code changes be pushed. The complete frontend refinement, motion, tests and visual
record are approved for a normal commit/push to `origin/dev-core-architecture`.
The backend checkout has no pending changes from this task. This publication does
not require a backend rollout or migration and does not deploy the app. Earlier
no-commit/no-push statements describe the implementation phases before this request.
