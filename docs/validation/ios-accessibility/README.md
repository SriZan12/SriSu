# iOS accessibility lifecycle fix — 2026-10-24

Frontend: `SriZan12/SriSu`, `dev-core-architecture`, base `8baf0d7`.
Backend: `SrizanKhadka/SriSu`, `codex/couple-chat-rebuild`, `6e0935c`; unchanged.
The existing iPhone 17 simulator uses iOS 26.5. No branch switch, account reset,
backend migration, or visual redesign is part of this fix. The Plans calendar QA
event retained at the user's request remains untouched.

## Cause and scope

The 09:40:23 diagnostic from the preceding Plans QA failed with EXC_BAD_ACCESS at
0x8 in `AccessibilityElement.get-node → contentOffset → bounds →
CMPAccessibilityElement.accessibilityFrame`. UIKit queried an element whose
Compose backing data was no longer available. The resolved iOS UI library was
1.10.2, despite the Compose plugin's 1.8.2 declaration.

Upstream addresses both layers of this lifecycle problem:

- [#3214](https://github.com/JetBrains/compose-multiplatform-core/pull/3214)
  checks element liveness before reading semantics, including contentOffset.
- [#3403](https://github.com/JetBrains/compose-multiplatform-core/pull/3403)
  moves the disposal flag to Objective-C storage so that checking the flag does
  not itself access a freed Kotlin peer. Released in 1.12.1.
- [1.12.2](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2)
  also fixes the related text-field focus/dialog layout crash (#3459).

The iOS source set explicitly requests Compose UI and Foundation 1.12.2;
their runtime, animation and native support modules resolve together. Native
UI tests use the same version. Kotlin, the Compose Gradle plugin, Material3,
Android dependencies, navigation, networking and encryption remain unchanged.
No accessibility suppression or runtime method replacement is used.

A direct UIKit stale-element regression was attempted in the command-line native
test runner. That runner has no connected UIKit application scene or Metal device;
the attempt failed during host initialization (`MetalRedrawer requires MTLDevice`),
before exercising the target behavior. The unsuitable test was removed rather than
silently skipped or counted as passing. The installed application supplies the real
UIKit/Metal host for accessibility inspection across navigation, sheets, dialogs,
keyboard and application lifecycle. Native Skia tests separately cover shared
feature state/rendering and real SDK encryption, not UIKit accessibility disposal.

## Verification

The signed Debug build was installed over the existing app on **iPhone 17,
iOS 26.5**. Account data was not reset. The installed native framework contains
`CMPAccessibilityElement._isInitialized` and its Objective-C accessors, matching
the published 1.12.2 lifecycle guard; this is not just a changed version string.

### Automated checks

| Check | Actual result |
| --- | --- |
| `:composeApp:compileKotlinIosSimulatorArm64` | Passed |
| `:composeApp:compileDebugKotlinAndroid` | Passed |
| `:composeApp:testDebugUnitTest` | 355 reported, 354 passed, 1 existing opt-in transport test skipped |
| Native simulator suite, excluding opt-in live tests | 268 passed; zero failures/errors/skips |
| Signed Debug `xcodebuild` for arm64 iOS Simulator | Passed; installed and launched successfully |
| `python3 tools/theme/check_theme.py` | Passed: 96 colors, 24 pairs, minimum contrast 4.75 |
| `git diff --check` | Passed |

[Per-class counts](test-results.json) distinguish Android and native results.
The native suite includes all common test classes plus iOS rendering, platform
storage, message motion and real SDK Spark encryption tests. Live classes were
explicitly excluded; no credentials-free early return is counted as a live pass.
State coverage includes chat identity/pagination, playback ownership, drafts,
retries, session recovery, Sparks recovery, Challenges and Plans transitions.

Local build logs: `/tmp/srisu-ios-accessibility-compile.log`,
`/tmp/srisu-ios-accessibility-suite.log`,
`/tmp/srisu-ios-accessibility-xcode.log`. These are local diagnostics, not tracked
artifacts. No backend code changed; no backend test suite was rerun for this
native-library-only change.

### Installed-app checks

All checks below used the updated application and queried actual UIKit
accessibility elements after transitions. These are simulator observations,
separate from the native test runner's render tests.

| Area | Observed result |
| --- | --- |
| Home and navigation | Home loads, scrolls and exposes feature cards; Home/Chat/Profile and back navigation work. |
| Plans | Existing “A little walk” draft retained; title keyboard Next moves to place/Done. Opening the date dialog from a focused field, cancelling it, opening/cancelling the time dialog with numeric keyboard, closing the editor, and switching Upcoming/Invitations/Past all work. Draft remains after three cold relaunches. |
| Moments | Native gallery selects two synthetic photos; both return to the composer. Reordering updates selection/count. Sharing sheet opens and closes; exiting through Keep draft and leave returns Home. Nothing published. |
| Sparks | Both existing synthetic partner answers decrypt and render. All decks opens and changing to Future replaces the card correctly. No new answer or invitation sent. |
| Challenges | Catalogue loads; collection change, activity detail, All activities and Our history work. No invitation sent. |
| Chat | Existing encrypted timeline loads; keyboard, attachment sheet, accessible Reply action/cancellation and appearance sheet open/close correctly. No new message sent or theme changed. |
| Profile | Existing profile and editable fields load from the bottom tab. No profile changes made. |
| Lifecycle | Chat returns intact after backgrounding to Springboard and reopening. Three explicit terminate/relaunch cycles followed by accessibility inspection and navigation succeed. |

There was **no recurrence and no new SriSu crash report** during this pass.
The reports before and after remained only `SriSu-2026-10-24-090843.ips` and
`SriSu-2026-10-24-094023.ips`. Failed `test.kexe` host experiments described above
are separate from these installed-app results. Final app remains signed in on
the Plans agenda. The user's retained Calendar event was not touched.

Evidence: [Home](home.png), [time dialog with keyboard](plans-time-keyboard.png),
[two-photo composer after reordering](moments-multiple-photos.png).

### Remaining limits

No physical-device or spoken VoiceOver/rotor session was run. Accessibility
inspection exercises native element queries but is not a complete screen-reader
audit or proof that every timing-dependent crash is impossible. The system photo
picker did not expose its elements through Device Hub's accessibility bridge;
selection was visually verified using its native UI, and Compose elements became
available again after dismissal.

This was a cross-feature regression pass, not a repetition of every two-partner
network mutation, reminder delivery or calendar-save test from the prior feature
QA. Existing live content loaded and encrypted Spark answers decrypted; automated
tests cover the other feature state/rendering contracts. Android compilation and
tests passed, but no Android device session was run. Backend behavior, permissions,
encryption, theme tokens and application feature code were not modified.
