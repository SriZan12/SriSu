# Home couple space — native verification

9 November 2026. Both repositories are on local `codex/home-couple-space` branches.
See [implementation provenance and rollout](../../design/home-redesign/stage-3.md)
and [behavior](../../flows/home-couple-space.md). Publication is to feature branches
and linked PRs; no merge, production deployment or production migration occurred.

For the subsequent 11 November compactness/curved-connector pass, see
[the refinement verification](refinement-2026-11-11/README.md). Results below
describe the 9 November implementation and are not fresh device verification of
that later pass.

The subsequent [11 November UI QA](ui-qa-2026-11-11/README.md) fixes portrait
alignment and long-question wrapping, removes a redundant row, and records
the latest 20 Android / 17 iOS Home render checks and their device-testing limits.

The [13 November installed-app follow-up](device-qa-2026-11-13/README.md) restores
native Android/iOS verification and fixes rapid keyboard input loss. Its results
supersede the earlier environment limitations for the specific journeys recorded there.

## Results actually obtained

| Check | Result | Limits |
| --- | --- | --- |
| Full Android unit suite | 501 reported: 500 passed, 1 opt-in HTTP test skipped, zero failures | JVM/Robolectric; not a connected Android device |
| Selected iOS simulator suite | 68 reported, zero failures; 66 regular tests executed | Two opt-in live tests return early in this suite; Home live test executed separately below |
| Android compilation and installed tests | Debug app/test APKs built; all 4 HomeDeviceTest cases passed, no skips | Pixel 4a AVD, Android 15/API 35 ARM64; synthetic Home state in installed ComponentActivity |
| iOS compilation and signed app | Simulator ARM64 and full signed Xcode Debug build/install passed | iOS 26.5 simulator; no physical iPhone |
| Live Home integration | One opt-in native test passed with two real Matrix clients and three synthetic users | Isolated loopback Django/PostgreSQL/Synapse; no production services |
| Backend isolated PostgreSQL | 358 passed, no skips | Disposable PostgreSQL 17 cluster, synthetic accounts, includes row-lock concurrency |
| Backend isolated SQLite | 358 reported, 30 expected skips, zero failures | PostgreSQL-only tests require the separate run above |
| Cross-repository verification | Passed with explicit consolidated-backend override | Contract snapshots, generated route fixtures, builds, isolated checks/tests |
| Migration consistency | No changes detected | Migration 0023 executed only in isolated test databases |
| Theme audit | Passed: documented palette, paired M3 contrast and raw-token guards | Not a complete screen-reader or accessibility audit |
| Diff whitespace | Both repositories passed | Pre-existing work remains intact |

[Machine-readable results](verification.json) record the counts. After the
cross-repository run, the final UI-only corrections were checked again with the
Android suite, Android app/instrumentation compilation, iOS compilation and
selected iOS suite. Backend/contracts were unchanged after their passing runs.

The native Home fixtures cover latest Spark, latest message, quiet day, unpaired,
sent/received invitation, newly paired, initial loading, global and section
failures, compact width and 200% text. Android interaction tests distinguish
Moments creation, question, profile, message, Ask/draft and shared-idea actions.
Home ViewModel tests cover uncertain retry after recreation, no-partner reads,
late callbacks, read-only Matrix ownership and permission loss. Existing Chat
tests now also cover draft conflict preservation and explicit reply-context
replacement. Plan tests cover preserving an unrelated draft and retrying the
same idea conversion after a lost response/restart without ordinary Plan creation.

Backend Home tests cover metadata-only daily responses, carry/skip/reveal,
automatic-vs-partner activity, saved timezone, epoch changes, expired statuses,
authorization/revisions, interest/undo, duplicate conversion, membership
replacement, incoming/today priority and elapsed requests. PostgreSQL exercises
simultaneous daily allocation and simultaneous idea-to-Plan conversion.

## Captures and visual comparison

These are actual Compose renders with synthetic data, not generated mockups.
Android uses Robolectric API 35 native graphics; iOS uses Kotlin/Native and the
Skiko Compose test runtime in the booted **SriSu Couple Chat Evaluation** simulator
on **iOS 26.5**. They capture app content, without OS status bars/home indicators.
They are not full installed-app/device screenshots.

Standard layout is 390 × 844 dp/points and compact is 320 × 700, rendered at 2×.
The full composition scrolls; `bottom` files show lower sections rather than a
single image squeezed into one viewport. Test avatars intentionally use initials.
Production reads existing authorized profile photos; reference portraits are not
production fixtures.

| State | Android | iOS |
| --- | --- | --- |
| Latest Spark | [Top](android-spark-1.0-390-top.png), [lower sections](android-spark-bottom.png) | [Top](ios-spark-390-1.0.png) |
| Latest message | [Top](android-message-1.0-390-top.png) | [Top](ios-message-390-1.0.png) |
| Quiet day | [Top](android-quiet-1.0-390-top.png), [lower sections](android-quiet-bottom.png) | [Top](ios-quiet-390-1.0.png) |
| No partner | [Top](android-unpaired-1.0-390-top.png) | [Top](ios-unpaired-390-1.0.png) |
| Invitation sent | [Top](android-sent-1.0-390-top.png) | [Top](ios-sent-390-1.0.png) |
| Invitation received | [Top](android-received-1.0-390-top.png) | [Top](ios-received-390-1.0.png) |
| Newly paired | [Top](android-new-1.0-390-top.png) | [Top](ios-new-390-1.0.png) |
| Loading / failure | [Loading](android-loading-1.0-390-top.png), [failure](android-failed-1.0-390-top.png) | [Loading](ios-loading-390-1.0.png), [failure](ios-failed-390-1.0.png) |
| 320 dp | [Top](android-spark-1.0-320-top.png), [question](android-compact-question.png), [bottom](android-compact-bottom.png) | [Top](ios-spark-320-1.0.png) |
| 320 dp, 200% text | [Top](android-quiet-2.0-320-top.png), [question](android-large-question.png), [actions](android-large-actions.png), [bottom](android-large-bottom.png) | [Top](ios-quiet-320-2.0.png) |

Compared with the approved Stage 2 preview at the same nominal 390 × 844 and
320 × 700 dimensions. The phone-frame/OS chrome in the browser reference is not
part of these content-only native captures. Corrected question wrapping across
native platforms, wrapped tab labels, narrow Moments labels, card-title contrast,
compact avatar-add geometry and the question illustration. At enlarged text the
Day connector moves below the pair, navigation becomes two columns and statistics
stack. The large-text screenshots were inspected after this correction.

Remaining intentional differences: authorized photos/initials instead of demo
portraits; native icons and derived Canvas illustration; platform shadow/font
rasterization and some line breaks; **New** instead of a made-up unread number;
truthful reminder/challenge/status copy. No pixel-perfect identity is claimed.

## Reproduction

From the frontend:

```sh
SRISU_BACKEND_DIR=/Users/srijan/studioprojects/SriSu-backend-consolidated-20261029 \
  DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  python3 tools/workspace.py verify all

JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid \
  :composeApp:compileDebugAndroidTestKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 \
  :composeApp:iosSimulatorArm64Test \
  --device 0055683D-7DBA-44C7-855D-4AA64123C522 \
  --tests 'com.srisu.srisu.features.home.*' \
  --tests 'com.srisu.srisu.features.chat.ChatRecoveryTest' \
  --tests 'com.srisu.srisu.features.plans.*'
```

From the selected backend:

```sh
.venv/bin/python tools/workspace.py migrations
.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin
```

## Installed Android follow-up

All four `HomeDeviceTest` cases passed on **SriSuHomeQA**, a Pixel 4a AVD running
**Android 15 / API 35 ARM64**, 1080 × 2340 at 440 dpi. The installed debug app and
test APK exercise Home's actual Compose UI with synthetic state: independent
Moment/question/chat actions, status-sheet dismissal with Android Back, 200% text
scrolling to the bottom, no-partner state and failure/retry. This is native device
instrumentation, not Robolectric; it is not a live Android account/login or
keyboard journey.

Device captures: [paired](android-device-paired.png),
[200% text at the bottom](android-device-large-text-bottom.png),
[no partner](android-device-unpaired.png). These capture the installed Compose content, excluding OS chrome.
The regular native render captures above remain the controlled dimension comparison.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :composeApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.srisu.srisu.features.home.HomeDeviceTest
```

## Installed iOS and live integration follow-up

A signed Debug build was installed on **SriSu Couple Chat Evaluation (iOS 26.5)**
and authenticated through the real app against an isolated loopback backend with
mock OTP. The full Swift build found a missing `capabilities` argument in the
notification registration bridge; supplying an empty list preserves the existing
iOS capability declaration and fixes the build. Kotlin compilation alone had not
covered this Swift call.

Observed in the installed app:

- Home → Chat → Home retained Home's scroll position. A synthetic unsent draft
  survived navigation; a Home nudge offered Keep/Add/Replace instead of silently
  overwriting it. Choosing Keep preserved it.
- The chat composer remained above the software keyboard. The Plan editor's
  focused field stayed visible and its lower actions could be scrolled into view.
- Actual account status, coupled day count, Plan and locally decrypted partner
  message appeared on Home. Notification permission was not granted.
- At the simulator's largest text setting, after a cold launch, the Day connector
  moved below the avatars, navigation used two columns and statistics stacked;
  content scrolled clear of navigation. The simulator override did not update the
  already-mounted Compose controller until restart, so warm Dynamic Type changes
  are not claimed as verified. Reduced Motion was enabled during the check;
  preferences were restored afterward. No frame-timing or screen-reader audit.

`HomeLiveFlowTest` separately exercised real Darwin HTTP repositories, Home
ViewModels and two real Trixnity clients. See [sanitized results](live-integration.json).
It verified paired/unpaired states, unrelated-user denial, private status sharing,
idea interest, duplicate-safe conversion, separate Plan acceptance, encrypted
partner text, Home not marking it read, explicit conversation read and return,
answer omission from Home, partner-answer activity and account-loss cleanup.

This uses a fresh isolated `home_qa` PostgreSQL database and Synapse server,
synthetic accounts, loopback ports 8940/8948, no SMS/push and no production data.
The migration was also applied successfully to this new isolated app database.

To reproduce the opt-in native test, prepare three synthetic accounts (two paired
with a ready private Matrix room, one unpaired), no existing Home activities and
fresh session credentials in a private, ignored JSON fixture. Its shape is
`{base_url, couple_id, room_id, people:[{id,tokens:{access,refresh}}, ...]}`.
The test rejects any base URL other than `http://127.0.0.1:8940/`. Set both
`SRISU_HOME_LIVE` and `SIMCTL_CHILD_SRISU_HOME_LIVE` to the fixture's absolute path
and select `com.srisu.srisu.features.home.HomeLiveFlowTest` in the simulator command
above. Keep other conversations using those test accounts closed: they can
legitimately advance a read receipt. Use fresh credentials and fresh Home activity
fixtures on reruns; refresh-token rotation and earlier Plans affect the test setup.
Never put the fixture, local service secrets or token-bearing logs in source control.

## Remaining release checks

- Android native Home instrumentation passed, including status-sheet Back.
  A full Android authenticated Home → chat/Plan keyboard journey remains outside
  this harness; the corresponding software-keyboard flow was inspected on iOS.
- Test physical Android/iOS devices, full VoiceOver/TalkBack journeys, hardware
  keyboards, real profile-photo loading, frame timing and warm text-size changes.
- Exercise pairing/unpairing and membership changes, intermittent networking,
  reminder permissions and response races end to end against the intended release
  environment. Isolated contract/concurrency tests are not production delivery.
- New statuses/ideas have no push channel. Generic Nudge drafts have no attached
  Plan reference. Shared question timezone has no editor in this phase.

Deploy additive migration 0023 and backend first, then the client. No claim of
bug-free behavior, universal phone coverage or production readiness is implied by
the passing isolated checks.
