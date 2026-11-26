# Global, Faves and Discover — verification record

Status: implemented and verified for review, 26 November 2026. Live-backend and
native UIKit keyboard checks are complete. Publication targets
`dev-core-architecture`; merging is not authorized.
The evidence below distinguishes automated state/API tests, synthetic native UI
fixtures and actual account/backend sessions. This is not a broad release sign-off.

## Source and scope

| Source | Branch and starting commit |
| --- | --- |
| Frontend `SriZan12/SriSu` | `codex/global-faves-figma`, based on `dev-core-architecture` at `e4acb680b137116c5b4e8dd6db09ecd7e49198a5` |
| Backend `SrizanKhadka/SriSu` | `codex/global-faves-figma`, based on `dev-core-architecture` at `d3a7cc04a1ac61760a2d06b3ec8d765f1bb81c7d` |

The feature is implemented in the existing project folders. Those commits
are starting baselines; final feature revisions are recorded in the paired PRs. The user
authorized completing live-backend and iOS keyboard checks, pushing both feature
branches, creating PRs into `dev-core-architecture`, and checking/fixing CI.
**Merging is not authorized.** Unrelated local work,
including backend personal media and `.DS_Store` changes, is excluded from scope.

Design source: [Together page, 1:7](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-7),
[Discover, 311:8955](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=311-8955),
[Faves, 311:9758](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=311-9758),
and [Go private, 311:13549](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=311-13549).
These boards were inspected visually through the browser. The Figma API quota
was exhausted, so layer properties, exact measurements, assets and hidden
prototype wiring were not retrieved through design-context tools. Component-level
or pixel-exact fidelity is not established. Existing SriSu theme and accessibility
rules supply derived values; dark appearance uses the approved neutral roles.

## Product decisions and boundaries

- Private Faves remain individual saves. They are not disclosed to the saved
  couple or the saver’s partner and are not converted into follows.
- The user chose a separate explicit public-follow opt-in. A follower shares
  their own consented public identity with the followed couple. The incoming
  list is visible only to that couple’s current partners. There is no fabricated
  “Faved you” count or historical backfill from private saves.
- The user chose Go private to withdraw existing public Moments as well as
  profile publication. Either current partner may restrict access. Later profile
  approval does not automatically republish withdrawn Moments or recreate follows.
- Root Home / Questions / Chat / Profile navigation is retained. Discover’s
  “You two” action returns to Home; this work does not replace Chat with a Moments
  root tab. Public visitors do not gain access to another couple’s private chat.
- Search is authorized on the server against currently approved public identity
  and shared interests. Video, photo and text Moments use existing permission,
  expiry, viewer and private-appreciation paths.

## Verification evidence

| Check | Actual result | Coverage limit |
| --- | --- | --- |
| Full backend CI-equivalent checks | 534 tests: 493 passed, 41 skipped; dependencies, Django, contracts and migrations passed | Maintained isolated SQLite suites; PostgreSQL and other explicitly conditional tests skipped |
| Full Android CI-equivalent checks | Final regression: 767 tests, 759 passed, 8 opt-in transport skips; app and instrumented-test compilation passed earlier | Live Global/Faves transport ran separately and passed; final shared logic also compiled for iOS |
| Final isolated SQLite selected suites | 133 tests: 120 passed, 13 PostgreSQL-only skips | Synthetic disposable database, not a running app |
| Final disposable PostgreSQL: feed, public follows, couple profile and Moment publication | 128 passed, no skips | Includes competing follow/revoke writes, guarded media withdrawal, and bounded-query photo/video sequences; cluster stopped afterward |
| Backend migration drift / core contracts / diff | No changes detected; schema and 14 shared fixtures validated; clean diff | No user database migrated |
| Live authenticated Django HTTP | Passed: 74 requests across 10 scenario groups; real synthetic video derivatives included | Disposable loopback server and synthetic accounts; not production data or a WAN performance benchmark |
| Production Ktor repository / ViewModel transport integration | Passed | Actual Ktor, JWT-authenticated HTTP, public-follow controller and selected-Moment viewer against disposable Django; not a native-app navigation test |
| Android JVM focused tests | 85 passed, no skips | State, cancellation, requests, privacy, selected-Moment continuation, legacy Moment/video/note states, contract snapshot, public viewer rendering and saved-list privacy/scroll regressions |
| iOS simulator focused tests | 80 passed, no skips | iPhone 17 Pro simulator, iOS 26.5; shared state tests and Skiko native Compose rendering/interaction tests, including public viewer sending/retry/revocation; before the final input-component replacement, which is covered by the UIKit tests |
| Android emulator instrumented tests | 4 passed across two classes | Android 15, dedicated emulator: real search IME, selected Moment callback, library/back, 30-card scroll, manual-only refresh, small dark empty state, and appreciation first-send/retry/keyboard dismissal at actual 1.8× system font; synthetic accounts |
| Android app and test APK builds | Passed | Dedicated emulator installation completed; no physical Android account changed |
| iOS shared/native test executable build | Passed | Compiles and links production shared UI; not a signed app release or UIKit keyboard test |
| Actual iOS UIKit keyboard | Passed: portrait/landscape search, input/clear/submit/Back, full appreciation input, retry editing/draft retention, visible IME/Send bounds and success dismissal | iPhone 17 Pro, iOS 26.5; separate synthetic QA app; accessibilityExtraExtraLarge, dark mode, 320pt main content / native-width modal |
| Theme and paired core snapshot validation | Passed | Rechecked after implementation; not a pixel-comparison tool |
| Backend unrelated local work | All 17 baseline SHA-256 hashes unchanged, rechecked 26 November | Compared against `/tmp/srisu-nickname-pr-merge-preservation.json`; personal media and .DS_Store remain outside feature changes |
| CI | Backend #50 isolated-tests passed; frontend result is recorded on its associated PR | Optional Sourcery review skipped due to quota; required build/test results must be read at the exact PR heads |

PostgreSQL evidence is in `/tmp/srisu-public-follow-postgres-final.log`. Final
SQLite counts were captured in tool output, not redirected to a log. Frontend logs
are `/tmp/srisu-explore-complete.log`, `/tmp/srisu-explore-device-final.log` and
`/tmp/srisu-public-moment-device.log`. These are local diagnostic artifacts, not
portable source files. The test sources and screenshots below provide durable verification artifacts;
The paired PRs record the final source revisions and CI results.

Live HTTP evidence is in `/tmp/srisu-explore-http-final-report.json`: `passed: true`,
`request_count: 74`, and `video_verified: true`. Its 10 scenario groups cover JWT
authentication; Global query/filter/pagination; private Faves; explicit individual
follow and conditional duplicates; member-only followers; selected-Moment
pagination; private media/identity; appreciation permissions; Go private
media/cookie withdrawal; and no automatic republication. The report records
methods, paths, response statuses and elapsed times without credentials or bodies.

`/tmp/srisu-explore-live-kmp.log` records a successful Gradle run and successful
paired KMP ↔ Django loopback integration, with temporary server/credentials
removed. Durable test sources are `ExploreTransportIntegrationTest.kt`, frontend
`tools/core_integration.py --explore`, and backend `tools/core_integration_server.py`,
`tools/explore_integration_fixture.py`, and `tools/check_explore_http.py`. The
production Ktor check also verifies selecting a Moment beyond page one, navigation
through its chronological sequence, private appreciation permissions, and removal
of cached viewer data after withdrawal. Its synthetic integration fixture is
opt-in; ordinary offline unit runs skip the live-transport class.

## Coverage and remaining checks

| Area | Verified | Limits |
| --- | --- | --- |
| Discover/search | Debounce/cancellation, consent-filtered names/interests, empty/catalogue failure/retry, separate tab scroll; authenticated HTTP and production Ktor search/filter/continuation | Physical-device end-to-end account navigation and WAN latency not measured |
| Private Faves | Add/remove/retry, duplicate/opposite writes, exact saved total, empty active feed vs saved library, unavailable couples | Real multi-device latency not measured |
| Public follows | Explicit disclosure, identity prerequisites, follow/unfollow, incoming list/cursor, revoke and account change; real Ktor controller and conditional HTTP writes | No notification for private Faves; incoming list is Followers by design |
| Public viewer | Selected Moment continuation, photo/text layouts, navigation callbacks, appreciation draft/send/busy/retry, remote withdrawal hiding content/editor; real HTTP media/cookie withdrawal and private appreciation | Synthetic video HTTP derivatives verified; native network video playback performance not benchmarked |
| Privacy | No private identity search/disclosure; private Faves stay private; withdrawal denies sequence/detail/photo/video incl prior cookie; no automatic republish | Already-delivered bytes cannot be recalled; active client polls every 30 seconds plus request latency |
| Performance | Bounded database queries, 8MiB/32-entry encoded-image cache, coalesced poster requests and 3 concurrent fetches; emulator scroll | No production-sized directory search, low-memory decode, WAN or release-frame profiling |
| Accessibility/layout | 320dp width, 1.6×/1.8× text, dark mode, landscape semantic interaction, full-size touch controls, scrolling | VoiceOver/TalkBack traversal and physical devices not tested; UIKit keyboard verified in the isolated native app; no physical-device or signed-release check |

## Screenshots and visual findings

All images use **synthetic fixture content** and an existing bundled stock photo.
They show production composables, not live user profiles. Android device images
include actual system keyboard and safe areas; earlier iOS images use the native Skiko
Compose renderer at specified logical sizes without UIKit status bars. The added
`ios-native-*` screenshots are actual UIKit/XCUITest captures, with real system
keyboard and safe areas.

- [Android Discover](screenshots/android-device-discover.png)
- [Android search with real keyboard](screenshots/android-device-keyboard.png)
- [Android Faves after overlap fix](screenshots/android-device-faves.png)
- [Android saved library](screenshots/android-device-library.png)
- [Android dark, 320dp, 1.8× text](screenshots/android-device-dark-small-large.png)
- [iOS Discover, 390×844](screenshots/ios-global.png)
- [iOS Faves, dark](screenshots/ios-faves-dark.png)
- [iOS 320×640, 1.6× text](screenshots/ios-global-small-large-text.png)
- [iOS empty Faves](screenshots/ios-faves-empty.png)
- [iOS loading](screenshots/ios-loading.png) and [retry](screenshots/ios-error.png)
- [Android appreciation with actual 1.8× system text and keyboard](screenshots/android-public-moment-large-dark-keyboard.png)
- [Android appreciation retry above the keyboard](screenshots/android-public-moment-keyboard-retry.png)
- [iOS public photo](screenshots/public-moment-ios-photo.png)
- [iOS small, large-text appreciation retry](screenshots/public-moment-ios-appreciation-retry.png)
- [Native iOS search with UIKit keyboard](screenshots/ios-native-search-keyboard.png)
- [Native iOS landscape keyboard](screenshots/ios-native-search-landscape-keyboard.png)
- [Native iOS large-text appreciation and keyboard](screenshots/ios-native-appreciation-large-dark-keyboard.png)
- [Native iOS failed-send draft and visible retry keyboard](screenshots/ios-native-appreciation-large-dark-retry.png)
- [Native iOS successful send](screenshots/ios-native-appreciation-sent.png)

The final native XCUITest suite passed **3/3**, with no failures or skips. See the
[sanitized native summary](ios-native-summary.json). The underlying local result
bundle is `build/reports/global-faves/ios-native/20261126-071134/GlobalFaves.xcresult`.
The normal production framework was rebuilt without the injected QA controller.

For the instrumented appreciation fixture, main content is constrained to 320dp;
the modal correctly uses the emulator’s full 393dp window (1080×2340 physical pixels).
The fixture area outside that constrained main content is not production app UI.

Visual QA corrected overlapping paper-preview/actions, excess vertical spacing and
crowded header labels. Reauthorization preserves saved-list row positions while
hiding old identities and disabling their actions. Server-derived age labels use
existing timestamps; missing or malformed timestamps are omitted. Small-screen
large text wraps the top navigation and names;
content stays scrollable. Focused previews and filmstrips load actual authorized
Moment media; fixtures repeat one stock asset solely for deterministic tests.
A real Android IME test at system font scale 1.8× exposed the appreciation
sheet remaining partially expanded with controls below the keyboard. The editor
and note/follow sheets now open expanded; content remains scrollable. This follows
the [Material sheet state API](https://developer.android.com/develop/ui/compose/components/bottom-sheets-partial).
The unchanged button-versus-keyboard bounds assertion passes after the fix, including
retry after an error. The same expanded-sheet fix also resolved the iOS small-screen
appreciation interaction failure. Two initial library regression-test failures were
missing resource initialization in the test harness, which was corrected separately.

Native UIKit QA subsequently exposed a separate appreciation-input defect: the
String-only field lost caret/composition state, corrupting a bulk-typed sentence
before Send (even a paced space could move before its preceding letter). The
editor now reuses SriSu's `DraftOutlinedTextField`, which keeps local IME selection
and composition while synchronizing the ViewModel draft. The unchanged complete
sentence assertion then passed, followed by failed-send draft retention and
successful retry. The full Android unit suite and real Android IME regression
were rerun after this production fix and passed. Native keyboard assertions also
check screen intersection, because a hidden UIKit keyboard can remain in the
accessibility tree; existence alone is insufficient evidence of a visible IME.

Final review added three regression tests which failed before repair. Moving
between Moments now resets drafts, note replies and sent feedback consistently,
including across pagination. Public-access polling starts before cancellable
media loading: quickly skipping a still-loading photo no longer disables later
withdrawal checks. The full Android suite then passed (759 passed, 8 opt-in skips),
and iOS compilation passed; log `/tmp/srisu-explore-final-regressions.log`.
Native keyboard checks above preceded these shared navigation/lifecycle fixes;
they did not change the input component or screen layout.

Backend review also reproduced a PostgreSQL follow/disconnect deadlock. The
repair takes the actor lock first and uses nonblocking couple locks, mapping only
lock contention to the existing retryable 409 response. Source/target disconnect,
profile-interest writes, same-version follows and Go private were verified in
153 PostgreSQL tests with no skips. This adds no migration beyond `0026`.

Intentional differences from Figma: private saves are not presented as followers;
“Faved you” becomes a separate opted-in **Followers** list. Existing root tabs are
preserved. Paper views in the feed are previews; tapping opens the protected viewer,
so video playback and view counting do not start from prefetching. Public today
means active public Moments in their existing rolling 24-hour lifetime. Generic
Material icons, existing fonts and derived spacing replace unavailable source
assets/properties. Exact pixel fidelity is not claimed.

## Repeating native keyboard verification

The standalone QA controller is outside production source sets. With a booted
arm64 iOS simulator and a supported JDK, run from the frontend root:

```sh
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -I tools/qa/explore.init.gradle --no-configuration-cache
python3 tools/verify_explore_ios.py --device <booted-simulator-UDID>
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

The first command explicitly injects the synthetic controller for this QA build.
The runner creates a temporary Xcode project, runs real XCUITest keyboard gestures
against production composables, exports screenshots/results under
`build/reports/global-faves/ios-native/`, and removes its separate QA app and runner.
It does not load an account or install over the actual SriSu bundle. The last
command restores the normal framework without the QA entry point. Live networking
is verified separately through the production Ktor repositories/ViewModels and
the disposable Django server; the native fixture simulates send success/failure.

The native fixture uses actual UIKit Dynamic Type for the large-text editor.
The simulator can retain an offscreen keyboard before software typing; tests
therefore require a visibly intersecting keyboard after both initial and retry
editing, and inspect screenshots. They do not infer visibility from AX existence.
Touch-only software-keyboard activation on a physical phone is not claimed.

## Deployment and compatibility

Deploy the backend first and apply additive
`social.0026_public_couple_follows`. It adds separate public-follow and mutation
version tables; it does not convert private Faves or publish existing identities.
Feed search, additional previews and saved totals preserve old fields. The client
uses the backend-owned core contract snapshot, which must be synchronized and
validated after all endpoint changes are final.

Go private intentionally strengthens existing revoke behavior: it also changes
existing public Moment visibility to private, advances their revisions and removes
associated public follows. Existing authorized media endpoints enforce subsequent
reads. Bytes already delivered to another device cannot be recalled; do not claim
instantaneous erasure from screenshots, exports or already-open views.

No migration was applied to a user database and no deployment was performed by
these isolated tests. Record final migration-drift checks and rollout instructions
after the implementation is complete.

## Open release gaps

There is still no supported user-facing block/unblock or report/moderation workflow.
Existing bilateral block records are honored by permission checks, but the retired
single-connection mutation routes are not a usable replacement. Public appreciation
allows limited contact with a couple; its rate limit is not a reporting or blocking
system. Broad public release must not be described as complete until that gap is
resolved. No nonfunctional Report/Block control is presented as implemented.

Search verifies exact publication fingerprints in bounded batches after SQL
prefiltering; a no-match query may still scan the eligible published directory.
It is not indexed full-text search. Protected photos remain existing full-image
renditions, so real-device network/decoding performance still needs measurement.

## Final handoff fields

- Feature branch in both original checkouts: `codex/global-faves-figma`. Final revisions are recorded in the paired PRs.
- Push and paired PR creation into `dev-core-architecture` are authorized. Backend PR: [#50](https://github.com/SrizanKhadka/SriSu/pull/50).
  Check the associated PRs for CI at their exact head commits. Do not merge either PR.
- No deployment or user-database migration performed. Disposable test databases
  and synthetic credentials are separate from the user's app data.
- Final checks: full Android 756 passed / 8 skipped; backend 493 passed / 41 skipped;
  80 iOS simulator and 4 Android instrumented tests passed;
  128 PostgreSQL tests passed; SQLite 120 passed with 13 PostgreSQL-only skips.
- Theme, paired core contracts, migration drift and whitespace checks passed.
- Live HTTP: 74 requests across 10 scenario groups passed, including video
  authorization/withdrawal. Production Ktor repository/ViewModel integration passed.
- The 17 unrelated backend files still match their original SHA-256 hashes.
- Dedicated Android emulator system font scale restored to its original 1.0.
- Native tests and live transport checks use synthetic fixtures/accounts. Actual
  UIKit search and appreciation keyboard flows passed, including visible keyboard
  bounds and full draft retention on retry. Simulator hardware-keyboard preference
  was restored to its original unset state; normal framework excludes QA hooks.
- See the evidence, intentional differences and open release gaps above.
