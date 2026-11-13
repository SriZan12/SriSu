# Questions UI — Stage 2 verification

Verified 13 November 2026. Synthetic accounts and an isolated local backend only.

## Source and scope

- Frontend: `SriZan12/SriSu`, branch `codex/questions-ui`, implemented from
  `5312eeeac03b79f7d67f8a1d51db917ad34bb0a6`. Before publication, the branch was
  fast-forwarded to the Home merge `f86afc180e597c68b052aa2b3dce531109f85a34`
  on `dev-core-architecture`; that merge has the same source tree. The original
  Home checkout and its existing changes are preserved.
- Backend: `SrizanKhadka/SriSu`, checkout `SriSu-backend-consolidated-20261029`,
  branch `codex/home-couple-space`, commit
  `3e276ad6a38b1afdd955d760be4519628bd1b3f7`. No backend changes in this task.
- Integration refs checked remotely during the task: frontend `dev-new-theme`
  at `3fa6822ea05578d64cca8891c5e3fdb0a831897a`; backend `dev` at
  `ecc8592307114e7d154bda9612e6826e2ca8ba90`. These differ from the Home feature
  baselines used here. Frontend Home PR #28 and backend Home PR #41 are merged
  into their respective `dev-core-architecture` branches. Deployment is separate.
- [Figma Questions page](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-6):
  root `251:5437`, unpaired `251:6092`, history `251:6184`, search `251:6250`,
  topic `251:6299`, answer `251:6396`, waiting `251:6463`, reveal `251:6530`,
  finished `251:6634`.

The implementation follows the approved Stage 1 adaptation: the real four decks
and 25 questions, one shared daily question with carry/skip behavior, catalogue
search, and recent answered history. It preserves the existing encryption,
immutable submission, protected drafts, recovery and Matrix chat flows.

Fresh Figma design-context and screenshot exports were blocked by the connector's
Starter-plan quota. The inspected design and approved Stage 1 preview informed
the implementation; exact token/asset parity is **not verified**. The illustration
reuses the existing Home artwork, and topic symbols use existing Material icons.
Games, expanded topics, reactions, streaks and intimate preferences are omitted
as agreed, rather than presented as working features. Recent history is bounded
by the existing backend projection.

## Changes and fixes

- Compact white Questions catalogue, lilac current-question card, pastel topics,
  warm list rows, separate search/history/topic destinations and conversation-like
  reveal. The root retains the app tabs; detail screens have a back action.
- Content-driven heights, 20 dp gutters (16 below 360 dp), effective 48 dp touch
  targets, wrapping footer controls, and a one-column topic layout at large text.
  Secondary header actions move into a labelled options menu when space is tight.
- Existing daily endpoint shared with Home; metadata polling only while visible.
  Failed daily requests do not remove the catalogue. Permission failures clear
  shared content. Direct question routes retain their ID through a failed load.
- Existing draft editor adapter preserves immediate edits, cursor and IME
  composition through delayed model updates. Editor focus brings the field into
  view; the primary action consumes keyboard insets once.
- Fixed an iOS runtime navigation crash by serializing the new page argument as
  a validated string. Native graph-registration coverage catches enum NavType
  assumptions that JVM-only serialization tests miss.
- Fixed large-text iOS keyboard focus moving the whole scene under the status
  bar. `MainViewController` now uses `OnFocusBehavior.DoNothing`; Compose screens
  own their insets and focus scrolling. This is a shared host change, so Chat and
  Plan received native keyboard regression checks. No Chat or Plan visual redesign.

## Evidence actually run

| Check | Result and coverage |
| --- | --- |
| Theme audit | Passed; documented tokens and paired text contrast checks. |
| Android unit/component suite | 535 reported, **534 passed**, 1 opt-in test skipped; 0 failures/errors. Includes state, navigation, encryption/draft regressions and rendered UI. |
| Selected iOS simulator suite | 64 reported, 0 failures/errors. **62 exercised**; two opt-in live tests return early without credentials and are not counted as live coverage. Sparks, shared editor and navigation tests. |
| Android installed component tests | **5 passed** on Pixel 4a AVD, Android 15/API 35 ARM64. Native IME, rapid multiline entry, Back dismissal, preserved draft, large text, repeated touch swipes, reachable lower topics/tabs, unpaired state and search-empty clearing. |
| Android installed real-app test | **1 passed**, real MainActivity/navigation/repositories against isolated Django + Synapse. Home → Questions → daily editor; draft survives leave/reopen; empty search and recent history return correctly. No answer submitted. |
| Android build | Debug application and instrumentation APKs assembled successfully. |
| iOS build | Kotlin simulator compilation and full Xcode simulator app build succeeded; final app installed and launched. |
| iOS native app | iPhone 17 simulator, iOS 26.5. Real isolated account, catalogue and daily editor, multiline draft, native software keyboard at standard and accessibility text sizes. Header stays below status bar; field and action remain above keyboard. |
| iOS host regressions | Chat multiline composer at accessibility text size; Plan title → optional place via keyboard Next, Done dismissal, save/reopen draft. No message or invitation sent. |
| Small screens / text | Android and iOS Compose render fixtures at 320×568 and 390×844 logical units; 160% and 200% text, including partner-answered state. Scroll is expected at large text; no text scaling clamp. |
| Reduced motion | Native iOS large-text check with Reduce Motion enabled. Questions introduces no bespoke animations. Simulator setting restored afterward. |
| Diff hygiene | `git diff --check` passed. Original frontend and backend dirty files remain unchanged by this task. |

The isolated live backend uses existing Home services, including migration
`social.0023`. Deploy that compatible backend **before** this client if it is not
already available. This task introduces no endpoint, schema change or migration.

### Screenshots

Files prefixed `android-native-` come from the installed Compose hierarchy; their
bottom blank area reserves the native keyboard, which is outside that capture.
IME visibility and button bounds were asserted by the device tests. Files prefixed
`ios-native-`, plus the Chat/Plan regression captures, are full simulator screens
and include the native keyboard. Other files are synthetic render fixtures.

| State | Android | iOS |
| --- | --- | --- |
| Standard catalogue | [Render](android-decks.png) | [Installed app](ios-native-questions.png) |
| 320×568 | [Render](android-decks-small.png) | [Render](ios-decks-small.png) |
| 200% text | [Render](android-catalogue-active-small-200.png) | [Render](ios-catalogue-active-small-200.png) |
| Partner answered, 200% | [Render](android-partner-small.png) | [Render](ios-partner-small.png) |
| No partner | [Render](android-unpaired.png) | [Render](ios-unpaired.png) |
| Empty history | [Render](android-history-empty.png) | [Render](ios-history-empty.png) |
| Answer editor | [Render](android-answer.png) | [Render](ios-answer.png) |
| Revealed answers | [Render](android-reveal.png) | [Render](ios-reveal.png) |
| Standard keyboard | [Installed component](android-native-keyboard.png) | [Installed app](ios-native-keyboard.png) |
| Large-text keyboard | [Installed component](android-native-large-keyboard.png) | [Installed app](ios-native-large-keyboard.png) |
| Shared iOS host regression | — | [Chat](ios-chat-keyboard-regression.png), [Plan](ios-plan-keyboard-regression.png) |

### Limits

- No physical Android or iPhone test, sustained frame-time measurement, or claim
  of 60/120 fps. Android gesture checks passed; native iOS drag/scroll automation
  repeatedly failed in Device Hub with `noWindowsAvailable`, so iOS touch-scroll
  smoothness remains a manual-device check. Native keyboard focus scrolling did
  work and was inspected.
- Unpaired/empty/error states use synthetic render/state coverage; no new live
  unpair/re-pair operation or two-phone encrypted answer submission was performed.
  Existing protocol tests passed, but that is not a new end-to-end encryption test.
- The shared iOS host change was checked on Questions, Chat and Plan. Authentication
  screens already own IME/scroll insets in source, but live OTP/profile keyboard
  regression is not claimed.
- No new backend test run was needed for unchanged backend code. Earlier backend
  evidence is recorded separately; it is not counted as a current-task pass.
- This report records local verification. The Questions pull request records
  publication and the CI result for its exact head commit; local passing checks
  alone do not establish CI status.

## Reproduction

Use the repository's documented JDK/Android SDK setup. The local QA backend is
separate from any personal or production service; never copy its tokens into a
command, source, screenshot or log.

```sh
python3 tools/theme/check_theme.py
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug \
  :composeApp:assembleDebugAndroidTest :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:iosSimulatorArm64Test --device <dedicated-simulator-uuid> \
  --tests 'com.srisu.srisu.features.sparks.*' \
  --tests 'com.srisu.srisu.components.*' \
  --tests 'com.srisu.srisu.navigation.NavigationContractTest'
adb -s <qa-emulator> shell am instrument -w -r \
  -e class com.srisu.srisu.features.sparks.QuestionsDeviceTest \
  com.srisu.srisu.test/androidx.test.runner.AndroidJUnitRunner
git diff --check
```

The separate `QuestionsAppDeviceTest` is deliberately opt-in: it requires a
guarded, app-private fixture for the synthetic QA account and localhost API.
It clears that fixture and test session after execution. Run iOS Gradle tests
and the Xcode build sequentially because they share generated KSP outputs.
