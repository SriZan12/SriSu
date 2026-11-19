# Authentication validation — 2026-09-28

Authentication, the supplied introductory screens and restricted guest entry are
implemented. Local verification is recorded below; native end-to-end auth and
release deployment remain separate. See [the handoff](authentication.md) for
repositories, starting commits, compatibility and migration order.

## Environment and isolation

Frontend and backend stayed on `dev-core-architecture`. Tests used synthetic
identities, mocked Twilio delivery, disposable databases/media and loopback services.
No paid SMS, production API, real account or shared database was exercised.

Kotlin builds used `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'`.
Native builds used `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.
Python was the existing backend `.venv` (3.13.5). PostgreSQL 17.11 and its Homebrew
dependencies were installed for row-lock validation; no Homebrew service was started.
Homebrew's default empty cluster was not used. The test runner stopped/deleted its
separate private temporary cluster, and the paired transport runner removed its
server and temporary credentials.

## Baseline

* Theme validation passed.
* KMP unit tests and Android/iOS compilation passed before implementation.
* Backend baseline: 95 selected tests, 90 passed, five PostgreSQL tests skipped.
* Initial `verify all` stopped on stale generated contract fixtures. Existing user
  changes had renamed fixture constants. The generator was updated to preserve
  those names; no existing work was discarded.
* Eleven frontend files started dirty; backend started clean. Fingerprint comparison
  confirmed six frontend files unchanged before the introduction work. Public guest
  transport subsequently required one additional line in ProfileApiService. The
  task also retains the fixture-name migration needed by the expanded contract suite. Their original changes remain preserved in the resulting working tree.

## Commands and final results

Commands below run from the frontend unless a backend working directory is stated.

| Command | Result |
| --- | --- |
| `python3 tools/workspace.py status --remote` | Passed; distinct remotes/integration branches recorded in handoff |
| `python3 tools/workspace.py verify all` | Passed: contract/theme checks, client tests, Android/iOS compile, Django system checks and SQLite suites |
| `python3 tools/theme/check_theme.py` | Passed: 96 documented colors; 24 contrast pairs; minimum 4.75:1 |
| `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:assembleDebug :composeApp:lintDebug` | Passed; lint 98 warnings, zero errors; final unit count verified in the staged export below |
| Staged-tree export: `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` | Passed: 62 discovered, 61 passed, one conditional loopback test skipped; Android/iOS compile passed without the unrelated local edits |
| `python3 tools/core_integration.py` | Passed separately: the skipped test used real loopback Django HTTP/WebSocket, Room catalogue persistence, message exchange and logout cleanup |
| Backend: `.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin` | **122 passed, zero skips** on PostgreSQL 17.11 |
| `python3 tools/workspace.py verify backend` | 122 selected; 112 passed, ten PostgreSQL-only tests skipped on SQLite |
| Backend: `.venv/bin/python tools/workspace.py migrations` | Passed: no model/migration drift |
| `plutil -lint iosApp/iosApp/Info.plist iosApp/iosApp/Info.Debug.plist` | Both passed |
| `git diff --check` in each repository | Passed |
| Compare `contracts/core-1/{manifest,schema,fixtures}.json` across repositories | Identical; schema and 12 shared fixtures validated |

Native build command:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,id=BC14261D-8642-47FC-94B1-A3E8FA4C77ED' \
  -derivedDataPath /tmp/srisu-auth-ios-build CODE_SIGNING_ALLOWED=NO build
```

Result: **BUILD SUCCEEDED**. Installed and launched on iPhone 17 Pro / iOS 26.5.
Initial phone light/dark checks caught and fixed an apostrophe-escaping issue.
The final introduction build was installed on a separate disposable simulator,
`6CFDEB1F-7638-429C-888D-1FABE38D8DDD` (same device/runtime). Onboarding and Space
were visually compared with the supplied exports. Space also rendered at
`accessibility-medium` text size in derived dark mode, with wrapping cards and
visible controls. Guest Home was captured separately.

Native captures used explicit introduction preference fixtures in that disposable
simulator's app data, followed by cold process launches. This proves rendering
and saved-state routing, not interactive taps. Computer-use access to Xcode 27's
Device Hub timed out, so no automated native tap-through claim is made. Root
navigation transitions, duplicate taps, relaunch preferences and guest cancellation
are covered in KMP coordinator tests.

[Onboarding](authentication-validation/ios-onboarding-light.png) ·
[Space](authentication-validation/ios-space-light.png) ·
[Space, dark and larger text](authentication-validation/ios-space-dark-large-text.png) ·
[Guest Home](authentication-validation/ios-guest-home-light.png) ·
[Earlier phone](authentication-validation/ios-phone-light.png)

Local diagnostic logs (not committed): `/tmp/srisu-intro-verify-all.log`,
`/tmp/srisu-intro-final-check.log`, `/tmp/srisu-intro-paired.log`,
`/tmp/srisu-auth-postgres-final.log`, `/tmp/srisu-intro-backend.log`,
`/tmp/srisu-intro-ios-final.log`, `/tmp/srisu-auth-staged-check.log`. Publication-content
validation passed from an export of the staged tree, preserving unrelated local
edits. Nine files retain prior local-only changes (development URL, formatting/
duration syntax and FindPartner work). The existing contract constant/test renames
are included because the extended generated fixture suite depends on them.

## Behavior coverage

| Behavior | Evidence |
| --- | --- |
| Server-managed OTP, protected storage, expiry/guess limits, resend/spend budget | Backend mocked-provider tests with controlled time; no real SMS |
| Repeated send/verify, identity uniqueness, username claims | PostgreSQL concurrency tests with separate connections |
| Device refresh, lost response, replay revocation, legacy upgrade/cutoff, logout | API tests and PostgreSQL rotation/upgrade races; KMP concurrent-refresh and late-logout tests |
| Invalid OTP does not clear an authenticated session | Public auth transport marker plus backend field-error contract; verification is never a token 401 |
| Incomplete profile restrictions and cross-user writes/media | REST and socket authorization tests, name/skip flow, nested-photo path/URL/ownership rejection |
| Startup next step, deduplication, temporary outage and retry | KMP coordinator tests against server-progress fixtures |
| Late send/verification/bootstrap, edit-number cancellation | KMP controlled concurrent operations and identity/flow-generation guards |
| Unsafe write replay prevention | KMP authenticated-401 read/write tests; writes require explicit retry after renewal |
| Socket access expiry and revocation | KMP handshake-refresh and expired-connection retry tests; non-expiry denial clears the account; backend watchdog checks revoked sessions |
| Photo size/dimensions and upload validation | KMP header-bound tests, backend invalid/valid decoding and upload tests |
| First launch, Space, login bypass, guest resume, back/cancel, preference failure | KMP introduction coordinator tests; native saved-entry captures |
| Guest catalogue, offline fallback, no session creation or private access | KMP public transport/cache tests; backend anonymous catalogue/read/write permission test |
| Existing Chat/Social boundaries | Existing selected suites and paired transport regression passed; no feature refactor |

## Failures encountered and resolved

* Initial paired runner selected an incompatible shell JDK; rerunning with the
  project JDK resolved configuration. It then exposed eager invalidation of valid
  access-only legacy sessions. The client now preserves them until expiry/server
  rejection, with a regression test; the paired runner passed.
* Removing the obsolete photo success-navigation callback exposed one Android
  preview call site. It was updated; subsequent builds passed.
* The new PostgreSQL runner initially lacked synthetic environment values; it now
  uses the workspace test settings before base configuration is loaded.
* PostgreSQL exposed Channels connection cleanup inside the new recovery test
  class's outer `TestCase` transaction. Changing it to `TransactionTestCase` exercises
  real commits and connection lifecycles; no production guard/test was disabled.

## Remaining limits

* Figma metadata/design context and read-only page inspection: Starter-plan MCP
  tool-call quota. Supplied page `1:2` child-frame metadata and prototype wiring
  remain unavailable. User-provided screen exports and the approved flow are used;
  derived icons, dark colors and guest UI are documented in the handoff.
* Android device/emulator runtime: no device or emulator available. Compilation,
  APK assembly, Robolectric/unit tests and lint passed.
* Full iOS/Android OTP, picker, permission-denial, keyboard/autofill, VoiceOver,
  full large-text range, process-death, background/resume and secure-store failure matrix:
  not run on native devices. Simulator launch/rendering and one larger-text Space
  capture are narrower coverage.
* Provider delivery and deployed-backend testing: intentionally not run; deployment
  credentials, SMS spending policy and legacy-client cutoff remain rollout gates.
* Offline logout server revocation is best effort; no durable revocation queue.
  Local logout clears authority immediately. Refresh recovery beyond its 60-second
  retry window requires a new verification proof.

No production deployment, SMS or shared data migration was performed. Changes are
published on the existing feature branches after local checks. Deployment still
requires the documented backend-first rollout and explicit legacy-client cutoff.

Publication uses the connected GitHub account because local HTTPS credentials were
absent and the existing SSH account lacked repository access. GitHub-created trees
are compared byte-for-byte by Git tree SHA with the tested local content. Feature
branch pointers are aligned only after that equality check; original local commits
are retained under `refs/codex/auth-local-before-publication`. No working-tree edits
are reset or discarded, and no force-push is used.

## Gender step — 2026-09-29

* `python3 tools/workspace.py verify all` passed: contract/fixture consistency,
  theme validation, Android unit tests and Kotlin compilation, iOS simulator Kotlin
  compilation, Django checks and the isolated backend suite (126 passed, ten
  PostgreSQL-only skips). The real transport test is skipped in the ordinary unit
  run and exercised separately below.
* Six new `GenderOnboardingTest` cases cover name → gender → photo → Home,
  missing selection, duplicate writes, loading/back guards, saved gender restore,
  unchanged saves after Back, selection retention, validation/permission/server
  failures with retry, expired sessions and logout racing a late response.
  Existing bootstrap tests also retain coverage for completed accounts without gender.
* `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
  python3 tools/core_integration.py` passed against the unchanged backend commit
  `c622e7fdb85806d2e5d279aad7a537b0d2cc8a73`, using a disposable database and synthetic
  accounts. Real Ktor PATCH saves `FEMALE` and `MALE`, GET restores each, and `NONE`
  is rejected with 400. Existing HTTP/socket/database/logout checks still pass.
* The first run caught eager Kotlin object initialization in the new step list;
  lazy initialization fixes it. Two old bootstrap expectations were updated for
  the inserted gender destination. The subsequent full run passed.
* Native interactive verification remains unverified. An iPhone 17 Pro/iOS 26.5
  simulator is booted, but computer-use access to Xcode's Device Hub timed out.
  A physical Android device is connected; no install/session replacement was
  performed on it. No claim of native tap-through or visual verification is made.
* The three pre-existing local frontend edits (Gradle, HTTP logging, phone UI) are
  preserved and excluded from publication. Backend production source is unchanged.
