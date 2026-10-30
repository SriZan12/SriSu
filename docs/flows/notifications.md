# Notifications and crash diagnostics V1

The [device-only Firebase test kit](../../tools/notification_tests/README.md) provides
seven repeatable direct-FCM probes, a manual checklist for all six supported domain
alerts, and local Plan-reminder checks. It uses the existing Windows Docker sender
credentials without Firebase Console sign-in. Direct probes are separate from
Analytics A/B campaigns and from real domain events/tap authorization.

See the [29 October physical Android verification](../validation/notifications/android-phone-20261029.md)
for live Windows-to-Realme results: audible confirmation, silent-channel delivery,
actual notification taps, payload guards, foreground observations and offline TTL.
That run also corrected the test kit's missing `fid:` digest namespace. It did not
change the deployed backend or add Matrix chat push; its coverage limits are
recorded separately from the earlier isolated-emulator domain tests below.

## Android startup registration — 30 October 2026

Firebase auto-init and registration entry points now run on a serialized worker
executor. In the pinned Messaging SDK, enabling auto-init can synchronously check
the cached FID; calling that path on the UI thread can fail before registration
starts. Previously, that failure could leave Home stuck at “Getting this phone’s
Firebase ID…” and prevent further attempts in that process. Synchronous SDK failures now complete
as a failed attempt, making the existing retry path available. Completion and
published state return to the main thread, while obsolete callbacks are ignored
after logout or a newer attempt. Logout queues auto-init disable/unregistration
through the same worker and clears the old registering flag.

This is an Android-only change based on frontend `6deb1d4`; backend `5fb1237` and
the `notifications-1` API are unchanged. There is no migration or UI design change.
See the [30 October startup and final-APK verification](../validation/notifications/android-startup-20261030.md):
24 notification tests passed on both Mac and Windows, the normal phone startup
reached the ready state, and live audible/quiet/unknown-delivery alerts arrived and
opened correctly. The Windows backend was restarted with its existing data volumes.

## Copy the Android Firebase test ID — 29 October 2026

Android debug builds now expose **Home → Firebase test notifications → Copy Firebase
ID**, directly below the greeting. The tool was moved from notification settings at
the user's request, starting from frontend merge `1ed0678`. Sign in and allow
notifications first in **Profile → Notifications & diagnostics**.
The button becomes available after the native Firebase registration callback.
Missing configuration, signed-out state, denied permission, registration progress,
failure and retry have explicit UI states. Copy success is shown only when Android
accepts the clipboard write; permission and account state are rechecked on every tap.

This app uses Firebase's registered **Installation ID (FID)** flow, rather than the
legacy token API. The copied value is the current native FCM registration target,
not the app's internal installation UUID. Paste it into Firebase Console for project
`srisu-ef431`: Messaging → create a notification campaign → Send test message →
registration ID/token field → Test. Keep SriSu in the background to test the system
notification, and enable the phone's notification sound. Firebase's
[test-message instructions](https://firebase.google.com/docs/cloud-messaging/flutter/get-started#send_a_test_notification_message)
document support for a registered Firebase Installation ID or FCM registration token.
A generic console notification tests delivery; it does not contain a server delivery
ID for opening a specific SriSu activity.

The identifier stays in the Android provider until explicitly copied. It is never
rendered in Compose text/semantics or added to logs, shared state, diagnostics or
source control. The clipboard entry is marked sensitive to hide Android's preview.
The UI and copy action are gated by the installed app's debuggable flag; iOS exposes
no tool. Firebase credentials and API-origin configuration remain local and ignored.

This frontend change starts from merge `0a309bc` on `dev-core-architecture`; the
paired backend remains `5fb1237`. No API, migration or backend deployment is required.
The control reuses the existing notification components and theme tokens. Its Home
placement is a user-requested developer addition to page `1:3`; iOS and release
builds render no control or extra section gap. Live Figma
file metadata was available for `LztysD1YvINX7RwZpnhhTt`, but reading design-system
page `0:1` hit the Starter-plan tool limit. This is derived developer UI with no
dedicated settings node or new pixel-fidelity claim.

Validation for this change: 16 focused notification tests passed, including clipboard
replacement with a rotated synthetic ID, sensitive-preview metadata, denied copy
after sign-out/permission revocation, release gating, registration states and UI
retry/copy feedback. Theme and whitespace checks passed. The Android debug app,
instrumentation APK and shared iOS simulator source compile. The existing opt-in
device integration test now also checks the native copied ID without logging it;
that extended device test was compiled but not rerun for this change. Earlier live
delivery evidence below applies to the earlier build. Apple/APNs remains deferred.

Home relocation validation: all 23 existing Home and notification tests passed.
The Home render checks now scroll the vertical list to offscreen Plan/Planner
controls, including large text, rather than assuming they start in the viewport.
The Home preview was inspected with the tool below the greeting. Theme/whitespace
checks, Android debug assembly and shared iOS simulator compilation passed; no new
physical-device or live-delivery test was run for this layout change.

## Consolidated core integration — 29 October 2026

This branch combines current `dev-core-architecture` (`2c7384b`), including the
merged home UI, voice waveform and Pure chat UI, with notification implementation
`0898bef`. The paired backend consolidation includes Matrix/shared activities
`34d47b4`, notifications `065340f`, and the preserved local Explore/Faves and
development-account work (`0ea972d`). Target branch: `dev-core-architecture` in
both repositories; consolidation branch: `codex/consolidate-core-20261029`.

Matrix remains the only live chat transport. The notification implementation's
retired libsignal client, native dependency and old chat endpoint are not restored.
Activity notifications now use current couple membership and the current Plan and
Spark services. Invitations, acceptance, sharing proposals, Plan creation/responses
and mutual Spark reveal retain generic lock-screen copy and server authorization.
Plan and Spark taps open the current typed feature destinations. Spark reveal
alerts eligible current-account installations; it no longer assumes the retired
original-libsignal-device model. The existing Matrix epoch check protects Spark taps.

**Matrix chat push is not implemented.** The backend advertises activity/Spark
capabilities instead of the legacy chat/Plan-card capability. Settings explain this
and hide ineffective chat push/mute controls. No Matrix pusher, gateway or plaintext
message inspection was introduced by this merge. Historical chat deliveries resolve
as unavailable. Old activity deliveries without a current membership snapshot are
also unavailable; the merge does not backfill or replay them.

The Android permission callbacks preserve both push and Plan reminders. On iOS,
one notification-center delegate distinguishes local `plan-` reminders from remote
push, so foreground presentation and authenticated reminder taps remain supported.
Firebase uses the integration's existing pinned SDK versions; CocoaPods/libsignal
are not reintroduced. Debug and release plists both disable automatic collection
and registration before Firebase initializes; crash diagnostics remain opt-in. Local builds still get their
API origin/environment from the latest configuration precedence, without a hardcoded
Windows host overriding it.

The dated October 13–14 verification and Windows deployment sections below are
**historical evidence**, including the previous chat model and old APK. They are not
claims that this consolidated version is deployed or that Matrix chat push works.
See the paired backend consolidation notes before any deployment: Matrix's existing
cutover is destructive and still requires its separate backup/readiness procedure.
No application database or running Docker deployment is changed by this merge.

### Consolidation verification — 29 October 2026

- `SRISU_BACKEND_DIR=<consolidated-backend> python3 tools/workspace.py verify all`
  passed with the documented Android SDK/JDK environment: theme checks, contracts,
  Android unit tests, Android compilation, shared iOS compilation and the isolated
  backend baseline. Android ran 470 tests with no failures and one opt-in HTTP skip.
- `python3 tools/core_integration.py --backend <consolidated-backend>` then passed
  real KMP-to-Django HTTP integration using temporary synthetic accounts/server.
- Final backend PostgreSQL 17 verification passed 328 tests with no skips; migration
  drift, dependency consistency and synthetic Compose validation also passed.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO ARCHS=arm64
  build` passed with the pinned SPM dependencies. The setup helper is idempotent,
  preserves the shared xcconfig and installs one symbol-upload phase. The SPM lock
  lives in the project's active workspace.
- No new physical-device, FCM delivery, APNs or Crashlytics cloud validation was run.
  Apple enrollment remains deferred. This verification did not deploy either repo.

Backend consolidation commit: `0742498` (Matrix/shared activities, preserved local
Explore/Faves work and compatible activity notifications). Final integration merge
IDs are available in the paired `dev-core-architecture` histories.

### Windows deployment preparation — 29 October 2026

Building the merged app's Android instrumentation APK exposed a missing
`androidx.compose.ui:ui-test-junit4` dependency in `androidInstrumentedTest`.
Restored the same pinned 1.9.0 dependency already used by the unit-test source set.
The workspace verifier and GitHub Android job now also compile instrumentation
sources so this failure is covered by routine checks.

`:composeApp:assembleDebug :composeApp:assembleDebugAndroidTest` passed with
`-Psrisu.apiBaseUrl=http://127.0.0.1:8029/`, the isolated device-test origin. These
artifacts are for synthetic device tests; this is not yet Windows deployment or
live Android delivery evidence. Apple enrollment and APNs remain skipped.

### Merged Windows deployment and Android acceptance — 29 October 2026

The subsequent deployment completed the Matrix cutover and all merged migrations
on Windows Docker Desktop at `192.168.1.69`. Backend runtime `80a1330`, with the
Windows shell-line-ending correction merged as `06b004f`, serves Django on port
8000 and private Synapse on 8008. The paired app runtime is `5ad8a97`; the deployment
branch also adds the opt-in device tests described here. Both repositories publish
these changes through `dev-core-architecture`. Backend `docs/notifications.md`
contains the host paths, backup/restore evidence and restart command.

On the dedicated Google Play API 35 emulator, the actual
`NotificationDeviceIntegrationTest` passed in 49.975 seconds against the merged
backend image in a separate synthetic PostgreSQL/Synapse Compose project. It
verified FID registration, foreground delivery without an OS alert, background
generic notification, actual PendingIntent/MainActivity launch, authorized routing,
unrelated-user and stale-invitation denial, preference suppression, account switch
and logout. The test backend reported two accepted deliveries and zero active
installations after logout. The deployed worker's independent FCM validation-only
request also passed; its ADC remains only on Windows.

`MatrixDeviceIntegrationTest` passed in 47.286 seconds using two protected native
Android stores, the real Trixnity repository and the isolated Django/Synapse stack:
both participants sent and decrypted messages, typing started/stopped through
Matrix sync, and an unrelated account could not bootstrap the room. A separate raw
Matrix history check confirmed encrypted events and absence of the synthetic
plaintext. Live HTTP checks covered Plans create/retry/accept/stale-write denial,
Sparks catalogue/create/plaintext rejection/skip, unrelated-user denial,
Challenges/Home, couple profile and feed. These are distinct from UI acceptance
of every activity screen and from physical-device coverage.

The normal LAN debug APK targets `http://192.168.1.69:8000/` and is retained at
`D:\SriSu-tools\SriSu-core-debug.apk` on Windows. Its SHA-256 is
`7446d3166f89fd3690ac07738cd9bb08dd22a49989de4d1e3ddd73d08ec766c3`.
`DeploymentDeviceSmokeTest` passed in 11.09 seconds on that APK: its configured
origin matched the deployment, interests returned 200, unauthenticated notification
settings returned 401, Matrix versions/policy returned 200, and MainActivity
launched. This probe was read-only against the deployed application database.
The connected physical phone's app data was not changed. Apple enrollment/APNs,
release signing and a new Crashlytics console verification remain deferred.
Matrix message push remains unavailable; activity notifications are verified.

Implementation and deployment record, updated 2026-10-14. The user approved the [architecture plan](../architecture/notifications-crashlytics-plan.md).
Both repositories use `codex/notifications-crashlytics-v1`, branched from fresh
`dev-core-architecture`: frontend `9f1a7708f1aa9785fb6e046227e134f23e1ec9f1`, backend
`2c4f312b3cb0327d7d298615028370514db2feb7`. Existing primary checkouts and their
uncommitted work are preserved. This document describes local implementation;
cloud configuration, deployment and device delivery have separate evidence below.

## Feature boundaries

Django owns the transactional event/outbox, installation/session ownership,
preferences, retries, current authorization and retention. The new
`contracts/notifications-1/` and additive `core-1` routes are pinned from the paired
backend. Current capabilities are `activity-notifications-1` and
`spark-notifications-1`; the old chat capability is not advertised.
Backend details and deployment commands: `docs/notifications.md` in the paired
backend repository. Notifications reuse the current authentication and domain APIs.

| Trigger | Current destination | Alert policy |
| --- | --- | --- |
| Matrix chat message | Unavailable | Requires a separate Matrix push integration. |
| New partner invitation | Partner requests | Pending invitations only; 24-hour TTL. |
| Pending invitation accepted | Current couple space | Once to original sender; six hours. |
| Plan created / meaningful response changed | Current Plan | Other member / proposer; six hours or start time, whichever is earlier. |
| Second Spark answer | Current revealed Spark | First waiting participant’s existing active installations; six hours; current Matrix epoch required. |
| Changed sharing proposal | Sharing review | Other member; valid current content fingerprint/consent only; 24 hours. |

No alerts for reactions, receipts, typing, presence, routine Moments, incomplete
Challenges, generic engagement or historical backlog. No notification center,
previews, remote reply/accept buttons, scheduled reminders or custom quiet hours.
Plan domain writes produce activity alerts. Matrix card sharing does not produce
an additional alert. Retired Plan-card correlation is not used.

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
Plan, Spark or couple section is already open. Open resolves authorization again.
Background alerts use OS notification presentation, without a duplicate local alert.
Activity groups use opaque grouping IDs; Android has
ordinary/quiet channels, and the backend caps audible sends at one per 30 seconds.
DND/Focus, OS permission, force-stop/force-quit and connectivity can delay or prevent
presentation. Provider acceptance is not proof of delivery, and logout cannot recall
already accepted generic OS alerts. Encrypted chat state still comes from Matrix sync.

Profile → **Notifications & diagnostics** exposes device permission, server push and
shared-activity switches, diagnostics consent, visible save failures and retry.
Chat controls remain hidden with an explicit unavailable message until the server
advertises support. Saved values change only after server confirmation. OS permission
and account preferences remain separate. Couple Profile and Partner Requests offer
a once-dismissable permission explanation. A missing local Firebase config shows an
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
