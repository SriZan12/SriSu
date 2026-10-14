# Notifications and crash diagnostics V1

Implementation and deployment record, updated 2026-10-14. The user approved the [architecture plan](../architecture/notifications-crashlytics-plan.md).
Both repositories use `codex/notifications-crashlytics-v1`, branched from fresh
`dev-core-architecture`: frontend `9f1a7708f1aa9785fb6e046227e134f23e1ec9f1`, backend
`2c4f312b3cb0327d7d298615028370514db2feb7`. Existing primary checkouts and their
uncommitted work are preserved. This document describes local implementation;
cloud configuration, deployment and device delivery have separate evidence below.

## Feature boundaries

Django owns the transactional event/outbox, installation/session ownership,
preferences, retries, current authorization and retention. The new
`contracts/notifications-1/` is copied from the paired backend; additive route and
`notification-plan-1` chat capability changes also have pinned contract snapshots.
Backend details and deployment commands: `docs/notifications.md` in the paired
backend repository. No chat transport, auth system, encryption or database was replaced.

| Trigger | Current destination | Alert policy |
| --- | --- | --- |
| Committed encrypted message of any supported content type | Exact conversation/message | Active encrypted-device session only; one-hour TTL; coalesce two seconds. |
| New partner invitation | Partner requests | Pending invitations only; 24-hour TTL. |
| Pending invitation accepted | Current couple space | Once to original sender; six hours. |
| Plan created / meaningful response changed | Current Plan | Other member / proposer; six hours or start time, whichever is earlier. |
| Second original-device Spark answer | Revealed message | First waiting original device; six hours. |
| Changed sharing proposal | Sharing review | Other member; valid current content fingerprint/consent only; 24 hours. |

No alerts for reactions, receipts, typing, presence, routine Moments, incomplete
Challenges, generic engagement or historical backlog. No notification center,
previews, remote reply/accept buttons, scheduled reminders or custom quiet hours.
The first explicitly correlated Plan card avoids a second Plan-creation alert;
a later deliberate share is allowed to alert. Legacy clients omit the field and
older servers are protected by capability negotiation.

## Client flow

Shared `features/notifications/` contains strict DTOs, authenticated Ktor calls,
session-scoped reconciliation, settings and the foreground/tap host. Native
`AndroidNotifications` and `SriSuNotifications` own OS permission, FCM registration,
secure installation proof/version storage and Crashlytics. Koin owns the repository
within the application lifetime. No platform SDK types enter common code.

After signed-in foreground entry, reconcile the current `auth-1` session and native
FCM registration. Reconcile again on callbacks/permission changes and every 30
seconds while foreground. Transport failures remain retryable; logout does not
follow from notification failures. A retired same-version binding can advance once
and retry. Account changes clear local notifications, pending taps, diagnostics
consent and native FCM registration. Stale HTTP work retains its original account
stamp across refresh and cannot write under another account.

Notification payloads contain only `schema`, random `delivery_id`, expiry and fixed
copy: **SriSu / You have an update. Open SriSu to view it.** No names, messages,
ciphertext, relationship IDs, keys, attachment URLs, tokens or OTPs. Native callbacks
validate bounded IDs/expiry. Taps wait behind authenticated navigation and resolve
through Django before opening an allowlisted typed destination. Deleted, expired,
revoked or unrelated destinations show a neutral unavailable message. Network
failures offer Retry. Destination resolution never accepts a route/URL from push.

Foreground alerts use the existing snackbar. They are suppressed while the relevant
conversation or couple section is already open. Open resolves authorization again.
Background alerts use OS notification presentation, without a duplicate local alert.
Chat groups replace the conversation alert with opaque grouping IDs; Android has
ordinary/quiet channels, and the backend caps audible sends at one per 30 seconds.
DND/Focus, OS permission, force-stop/force-quit and connectivity can delay or prevent
presentation. Provider acceptance is not proof of delivery, and logout cannot recall
already accepted generic OS alerts. Encrypted state still comes from normal chat sync.

Profile → **Notifications & diagnostics** exposes device permission, server push/chat/
shared-activity switches, chat mute for 1 hour/8 hours/indefinitely, unmute, visible
save failures and retry. Saved values change only after server confirmation. OS
permission and account preferences remain separate. The conversation offers a
once-dismissable permission explanation. A missing local Firebase config shows an
explicit unavailable-build state; debug builds can still compile.

The UI uses existing SriSu theme tokens. Live Figma inspection hit the Starter-plan
tool limit; no pixel-fidelity claim is made. Relevant references remain file
`LztysD1YvINX7RwZpnhhTt`: system `0:1`, partner `14:220`, `5:974`, `5:1118`, message
`184:2`, Spark `184:1634`, planner `185:781`. Settings, permission copy and dark mode
are derived UI, with no invented notification-settings Figma node.

## Firebase setup

Existing project **srisu-ef431**; existing-app inventory was empty before registration.

| Platform | Package / bundle | Registered Firebase app |
| --- | --- | --- |
| Android | `com.srisu.srisu` | `1:946622504157:android:607ad3216d2918d5898f34` |
| iOS | `com.srisu.srisu.SriSu` | `1:946622504157:ios:4db448f4b9452aaa898f34` |

Downloaded configuration exists locally and is excluded from Git. On another
machine, authenticate the Firebase CLI, then download directly without printing:

```sh
npx -y firebase-tools@latest apps:sdkconfig ANDROID 1:946622504157:android:607ad3216d2918d5898f34 --project srisu-ef431 --out composeApp/google-services.json
npx -y firebase-tools@latest apps:sdkconfig IOS 1:946622504157:ios:4db448f4b9452aaa898f34 --project srisu-ef431 --out iosApp/iosApp/GoogleService-Info.plist
```

Android uses Firebase BoM 35.0.0, Messaging, Crashlytics and Crashlytics NDK, Google
Services plugin 4.5.0 and Crashlytics plugin 3.0.8. Release builds require real config,
a non-development environment and an HTTPS API URL. R8 is currently disabled; when
minification is enabled the Crashlytics plugin supplies the mapping upload task.
Do not claim symbolicated libsignal frames without matching vendor native symbols.

Firebase iOS **13.0.1** is pinned via SPM (13.x discontinued CocoaPods). Only Core,
Messaging and Crashlytics are linked to the app target. Existing LibSignalClient
0.105.0 remains under its existing Podfile lock. The Podfile carries the existing
workspace's native build-order/SYMROOT fixes; this task reused the locked cached
Pods rather than upgrading libsignal or running CocoaPods. `tools/firebase-xcode/`
uses the Swift Xcode object model to maintain the Firebase configuration; no Ruby
project manipulation. Checked-in SPM resolution enables repeatable dependency fetches.

The actual bundle ID and Apple team **46U64W55Y8** come from Xcode build settings.
The app has push capability, development/production APS entitlements, manual APNs
forwarding (swizzling off) and default-disabled messaging auto-init. Apple Developer
must have a push-enabled matching App ID and valid profile. In Firebase Project
Settings → Cloud Messaging → Apple app, inspect existing APNs credentials first and
upload an authorized APNs key/certificate through the trusted UI if absent. Do not
rotate/revoke a shared key or commit `.p8`, `.p12`, or provisioning files. Apple terms,
account enrollment and key authorization require the account holder.

On 2026-10-13, Apple sign-in succeeded, but opening Certificates, Identifiers &
Profiles returned **Access Unavailable**: access requires enrollment in a developer
program or membership of an enrolled organization's team. The account page showed
the invitation to join the Apple Developer Program and no program resources.
Consequently, the configured Xcode team ID is not proof of active program access;
App ID push capability, signing profiles and APNs keys could not be inspected or
configured. Use an account with the appropriate enrolled-team access to continue.
No enrollment purchase, agreement acceptance or Apple credential changes were made.
See Apple's [account access documentation](https://developer.apple.com/help/account/basics/account-landing-page/).

The user deferred Apple enrollment/APNs on 2026-10-13. Continue Android and backend
deployment/testing independently. The current Docker host is another Windows laptop
using Docker Desktop/WSL; its access, rollout and credential requirements are in the
paired backend's `docs/notifications.md`, under "Windows Docker Desktop host".

Google Cloud sender is configured as
`srisu-notification-sender@srisu-ef431.iam.gserviceaccount.com` with custom role
`projects/srisu-ef431/roles/srisuNotificationSender`, containing only
`cloudmessaging.messages.create`. FCM was already enabled; IAM Credentials API was
enabled for short-lived impersonation. The signed-in developer received Token Creator
only on this sender account. No Owner/Editor grant, billing change or downloadable
service-account key was made. An FCM **validation-only** request was accepted using
this identity; it sent no notification.

A local impersonation ADC file is stored outside Git at
`~/.config/srisu/notification-sender-adc.json`, mode 0600. It contains a local developer
refresh credential as its source: impersonation limits the resulting sender token,
not what a person with the source credential could do. Do not copy this file to a
shared/production host. Use workload identity or that host's authorized local
development ADC instead. The Docker template mounts credentials read-only only into
the notification worker. A reproducible local login is:

```sh
gcloud auth application-default login --impersonate-service-account=srisu-notification-sender@srisu-ef431.iam.gserviceaccount.com
```

## Crashlytics and release validation

Diagnostics are **off by default**, independent from push permission and server
preferences. Consent persists on that device; logout/account switch disables it,
clears the user identifier and requests deletion of unsent reports. The shared
reporter accepts only fixed diagnostic codes and limits repetition. It never accepts
arbitrary Throwable/NSError messages, logs, account identifiers or network bodies.
Expected validation/auth/network errors are not reported. No Firebase Analytics,
Auth, Firestore, Storage or Functions product was enabled by this integration.
SDK transitive packages do not imply product use.

Native fatal crash reporting covers JVM/Android, NDK crashes supported by the SDK,
and native iOS termination. The Kotlin shared abstraction reports sanitized nonfatals.
Do not promise lossless Kotlin/Native exception causes, Kotlin source symbolication
or handled coroutine exception reporting. We preserve the app's existing fatal
termination behavior instead of adding an unsafe global exception handler. Native
fatal SDK reports can include runtime stack/exception details: keep secrets out of
exception messages throughout application code and audit representative controlled
reports before release. Opt-out cannot retract reports already uploaded; OS crash
storage is independent from Firebase consent.

The only custom key is build `environment`. Development currently uses the supplied
project; environment tagging is not separate-project isolation. Supply distinct
approved Firebase configs for true staging/production isolation and update explicit
release config validation when that project decision is made.

`tools/firebase-symbols.sh` validates the iOS Release project's plist and uploads the
**linked application's dSYM**, including linked static Kotlin code. Xcode uses dSYM
format and an explicit release script. When using custom `-clonedSourcePackagesDirPath`,
set `SRISU_FIREBASE_IOS_SDK_PATH` to its `firebase-ios-sdk` checkout for symbol upload.
Retain archive/dSYM and Android native symbols with the exact release. Simulator
build success does not validate distribution signing, APNs or release dSYM upload.

Controlled probes exist only in Debug. On a synthetic test device, enable diagnostics
in settings first. Android:

```sh
adb shell am start -n com.srisu.srisu/.notifications.CrashlyticsProbeActivity
adb shell am start -n com.srisu.srisu/.notifications.CrashlyticsProbeActivity --ez fatal true
```

On iOS, open `srisu://diagnostics-test/nonfatal` or `srisu://diagnostics-test/fatal`
with the app detached from the debugger. Relaunch after the fatal crash to upload.
Verify both app dashboards, app version/environment, expected symbols and the absence
of private fields. Repeat with diagnostics off and confirm no new test report.
The Android probe Activity is absent from Release and iOS probe code is `#if DEBUG`.

## Verification record and remaining acceptance checks

Unit/server tests use synthetic accounts and provider replacements. The separate
live Android test below uses real FCM with synthetic accounts in its own database.
The matching backend is deployed. Remaining device/release acceptance coverage:

- Physical-device permission denial and later settings changes, terminated app,
  Android force-stop and iOS force-quit/Focus behavior. Android permission grant,
  foreground/background delivery and no duplicate foreground OS alert passed on
  the API 35 emulator.
- Same/different conversation suppression, tap before login, exact message/Plan anchor,
  deleted content, expired invitation alert and ended/replaced relationship. The
  invitation PendingIntent, MainActivity launch, authorized typed destination,
  unrelated-user denial and rejected-invitation stale tap passed on Android.
- FID/token rotation, provider Unregistered, expired session, offline logout,
  reinstall/Keychain retention and active encrypted-device replacement on devices.
  Registered FID delivery, online logout and account-switch fencing passed on Android.
- Two installations receiving general activity, only active encrypted device receiving
  chat, mute/push/category preferences, retries, coalescing, worker restart and no backlog.
- iOS controlled fatal/nonfatal reports and signed-release mapping/dSYM/native-symbol
  validation. Android debug fatal/nonfatal cloud reports and their custom fields were
  inspected; diagnostics default-off and clearing on logout passed on the emulator.
  Physical-device opt-out/upload races remain a release acceptance check.

On **2026-10-14**, backend implementation
`784c5aface38e862bb5dae9e7379c515c761a73f` was deployed to the Windows Docker Desktop
host at `192.168.1.69:8000` through verified SSH access. The separate deployment
checkout preserves the original checkout, environment, PostgreSQL volume and media.
A verified host-local database backup preceded the two additive notification
migrations. Web and the notification worker are running with sending enabled; the
worker is healthy. Django checks, migration completeness, LAN HTTP 200 for interests
and HTTP 401 for unauthenticated notification preferences passed. Windows-local
sender impersonation also passed an FCM validation-only request. See the paired
backend's `docs/notifications.md` for paths, image identity and rollback information.

Android device tests use the same backend image with a separate disposable PostgreSQL
database and synthetic accounts on Windows loopback port 8029, forwarded into the
task's Google Play API 35 emulator. The deployed application's database is not used
for synthetic writes. Apple/APNs configuration and physical-device reporting remain
unverified and Apple enrollment is deferred by the user.

### Windows Android delivery and Crashlytics evidence — 2026-10-14

`NotificationDeviceIntegrationTest.registeredFidDeliversPrivatelyAndTapReauthorizes`
passed (**one live test, 36.457 seconds**) on the isolated Google Play API 35 x86_64
emulator `srisu_notifications_v1`. It exercised the real native Firebase SDK, shared
Ktor/repository code, Django producer/outbox and worker, and Google delivery:

- Permission-gated registered FCM FID; diagnostics initially off.
- Foreground signal with no duplicate OS alert; owner-only resolution, unrelated-user
  HTTP 404 and stale invitation rejection.
- Push disabled through the real preference API suppresses delivery; enabling it
  restores background delivery with the fixed private title/body and expected channel.
- The real notification PendingIntent opens MainActivity and resolves to the typed
  partner-request destination. This is not a pixel/UI-layout assertion.
- Account switching advances registration ownership/version; online logout retires
  the installation and clears local alerts and diagnostics consent.

Aggregate test-worker status showed **two accepted deliveries and zero active
installations after logout**; native assertions establish presentation separately
from that provider acceptance. No synthetic writes used the deployed database.
The test discovered a missing `mute: "unchanged"` field on ordinary preference writes.
Frontend fix `58d6322` always serializes it and adds both wire-format and real-HTTP
regressions, preserving an existing chat mute when another switch changes.

After explicit diagnostic opt-in, a debug-only controlled fatal crash and sanitized
`TEST_NONFATAL` report were received in Firebase for Android version **1.0 (1)**.
The actual cloud samples were inspected through Firebase's authenticated Crashlytics
MCP tools: both have only custom key `environment=development`, with no app-supplied
user ID, logs or breadcrumbs. The SDK's own installation/device metadata remains
present; these observations do not imply metadata-free crash reporting. The fatal
sample resolves to `CrashlyticsProbeActivity.kt:8`; nonfatal frames are deliberately
sanitized. These are Debug JVM reports, not NDK or signed-release symbol verification.

The opt-in instrumentation test requires a short-lived, app-private
`files/notification-device-fixture.json` from an isolated backend. It skips if absent
and rejects other scope/base URL/user-ID ranges. Never store credentials in arguments,
logs, screenshots or source control. The host runner at
`D:\SriSu-tools\deployment\device-run.py` resets only database
`srisu_notifications_e2e`, grants notification permission on the task emulator and
uses `adb reverse tcp:8029 tcp:8029`. Build its APK with
`-Psrisu.apiBaseUrl=http://127.0.0.1:8029/`. The fixture is removed by test cleanup.
The temporary test Compose project and emulator are removed/stopped after verification;
the deployment remains running.

The normal development APK uses `http://192.168.1.69:8000/`, and is copied to
`D:\SriSu-tools\SriSu-notifications-debug.apk` on Windows. It is separate from the
loopback-test APK. Keep the phone and Windows host on the same LAN for manual use.

After the preference fix, Android unit tests reported **166 tests, zero failures,
one opt-in integration skip**; the newly extended notification-only suite then
reported **10 passing tests**. Real KMP/Django HTTP/WebSocket integration, Android
APK assembly, iOS shared-code compilation and theme checks passed. The native live
test above then passed with the corrected build. Earlier iOS simulator/native build
evidence below remains valid for its recorded revision; Apple/APNs remains deferred.

## Commands completed on the implementation branch

- `python3 tools/workspace.py verify all` with `SRISU_BACKEND_DIR` pointing at the
  matching backend worktree: passed theme, frontend unit/compile checks, backend
  check/migration check and isolated SQLite allowlist (239 tests, 19 skipped because
  SQLite lacks the relevant PostgreSQL behavior).
- Backend `.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin`:
  **245 passed, no skips**, including duplicate producer, competing worker, deletion races and both FID/token provider targets.
- `:composeApp:testDebugUnitTest`: **166 tests, zero failures, one skipped**. The skip
  is the explicit loopback integration test, run separately below. Settings tests cover
  OS denial, failed save preserving confirmed values, reload, diagnostics default-off,
  dark mode and enlarged text. [Light render](notifications-validation/settings-light.png)
  and [dark enlarged-text render](notifications-validation/settings-dark-large.png)
  are Robolectric screenshots, not physical-device screenshots.
- `python3 tools/core_integration.py --backend <matching-backend>`: **passed** real
  KMP HTTP/WebSocket calls against a disposable Django server, including notification
  registration, server preference save and unavailable-delivery authorization.
- `:composeApp:compileDebugKotlinAndroid`, `:composeApp:compileKotlinIosSimulatorArm64`
  and `:composeApp:assembleDebug`: **passed**. APK is in the usual ignored build output.
- `:composeApp:iosSimulatorArm64Test`: **115 passed, no skips** on the available
  iOS simulator runtime. These are shared-library tests, not live APNs tests.
- `xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp -configuration Debug
  -destination 'generic/platform=iOS Simulator' ... CODE_SIGNING_ALLOWED=NO build`:
  **passed** with the registered plist, native Firebase products and libsignal linked.
- Core contract snapshot/fixtures, `git diff --check`, plist/entitlement/project parsing,
  symbol-script shell syntax, Django migration drift and `pip check`: **passed**.
  Credential/signing files remain ignored. Core contract digest:
  `c1c7b1213c1f1ffaae8d4aba9a9c90677232b0df0d6329800aa3efb4c29d24bf`.
- Google Cloud FCM validation-only send with the dedicated impersonated identity:
  **accepted**. No device was targeted and no delivery is inferred.

No physical iPhone test, real APNs round trip, signed release archive or release
symbol upload was completed. Android emulator delivery and cloud Crashlytics were
verified on 2026-10-14 as recorded above. After the account holder completed Apple sign-in,
the developer resources page explicitly denied program access (see setup evidence
above). Apple/APNs configuration remains blocked by that access requirement.

## Paired implementation commits

- Frontend implementation: `af6033a0a0fd37a960ef22762b15cac62eb8acf6`; Android/shared
  preference serialization fix and regressions: `58d6322`.
- Backend implementation plus final worker regressions: `3245510566e0872e4ec3da0c9a7e7476db66cfe6`
  (main implementation `85c0e3bff79b3cd2d6977eb7bee6316fe62221b6`).
- Backend Windows deployment guidance and Docker credential exclusions: `69cdb2c`;
  existing Windows schema compatibility and three regressions: `aceecda`; isolated
  Docker build tooling and deployed implementation: `784c5af`.
- This handoff documentation follows those implementation commits. Both branches are
  `codex/notifications-crashlytics-v1`; publication is separate from deployment.
  The Windows Docker rollout is complete; physical Android acceptance checks remain.
- Published branch locations: [frontend](https://github.com/SriZan12/SriSu/tree/codex/notifications-crashlytics-v1)
  and [backend](https://github.com/SrizanKhadka/SriSu/tree/codex/notifications-crashlytics-v1).
- Frontend worktree: `/Users/srijan/.codex/worktrees/notifications-crashlytics-v1/SriSu`.
- Backend worktree: `/Users/srijan/studioprojects/SriSu-backend-notifications`.

The original checkout paths retain their previous branches and uncommitted files.
Use the worktree paths above for this implementation and set `SRISU_BACKEND_DIR` when
using the cross-repository workspace checks.
