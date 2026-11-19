# Android startup registration and final APK — 30 October 2026

The registration fix was verified on the same USB-connected Realme RMX3511,
Android 13 / API 33, using the Windows Docker backend. Apple remains deferred.

## Versions and change

- Frontend implementation: `398a2aadb82658e21c68e0d7106b239e4313496e`, based on
  `dev-core-architecture` merge `6deb1d4`.
- Backend: `5fb1237721ea40fdaf4efcd39fc7278b45148cea`, unchanged.
- Windows-built debug APK SHA-256:
  `061df4c92339136f6a8526674e088e7aba3098451439fd45398bad1aab562a11`.
  Installed with `adb install -r`, preserving the account and app data.

The old installed build reproduced the startup defect: Home displayed “Getting
this phone’s Firebase ID…”, while native state had no FID, `registering=true`, an
active account binding and no reported failure. Enabling Firebase auto-init can
synchronously check a cached FID; the previous implementation invoked that path
on the main thread and could leave its in-progress flag set after an exception.
This diagnosis is supported by the SDK code and observed state, rather than a
captured exception from the user's phone.

Firebase initialization/registration entry points now use one worker executor.
Completions return to the main thread, synchronous failures finish the attempt,
and a generation guard ignores obsolete work/completions after logout or a newer
attempt. Logout uses the same queue for auto-init disable/unregistration. No
backend API, migration, Firebase project configuration or screen design changed.

## Checks completed

The earlier validation failure occurred before tests started: Java could not spawn
the Gradle test executor. A fresh JVM (`--no-daemon`) resolved that infrastructure
failure.

- Mac: **24 focused notification tests passed**, zero failures/errors/skips;
  Android application and instrumented-test Kotlin compilation passed.
- Windows: the same **24 tests passed**, zero failures/errors/skips; debug APK
  assembly and instrumented-test Kotlin compilation passed using cached dependencies.
- Eight new tests cover off-main SDK work, main-thread completion, synchronous and
  asynchronous failures/retry, logout before/during queued registration and stale
  completions after logout/new registration.
- Git whitespace validation passed. iOS compilation/APNs was not rerun for this
  Android-only change.

The Windows services were stopped when work resumed, and the web, Redis and main
database containers were absent. The existing Compose deployment was restored
using image `srisu-backend-app:core-5fb1237` and the verified existing external
database/Matrix volumes. No image rebuild, schema change, database reset or restore
was performed. Django system checks passed; LAN interests returned HTTP 200 and
unauthenticated notification preferences returned HTTP 401. All seven deployment
containers were running; database, Redis, Synapse and notification-worker health
checks were healthy.

## Physical-device observations

| Check | Result |
| --- | --- |
| Normal startup after APK replacement | Home displayed “This phone is ready for a Firebase test notification.” Native state had an FID, `registering=false`, `unregistering=false`, an active binding and no registration failure. |
| Explicit cold restart | Force-stopping and manually reopening SriSu again reached the ready label without clearing data or forcing a registration from a helper. This is a startup test, not delivery while force-stopped. |
| Observer startup | A separate temporary instrumentation package also observed normal startup registration. Its earlier direct SDK `register()` call was removed, so it could not mask this defect. |
| Backend binding | Read-only inspection found exactly one matching current, active, permitted Android/FID registration, with an active user/session and push/activity enabled. Real app registration requests returned HTTP 200. |
| Quiet background alert | Arrived on `srisu_updates_quiet`, importance 2, channel sound null, generic SriSu copy and private visibility. Its real notification PendingIntent opened Home. |
| Audible background alert | Arrived on `srisu_updates`, importance 3, channel sound enabled, generic SriSu copy and private visibility. Its real notification PendingIntent opened Home. |
| Unknown-delivery alert | Arrived; its real PendingIntent opened SriSu and showed “This update is no longer available.” |

The three arrival/tap checks ran around 17:35 Nepal time. Sound settings remained
normal ringer, DND off and notification volume 1/16. Sound-channel configuration
was inspected directly through Android's notification APIs. There was no new
microphone observation or speaker confirmation in this run; the user explicitly
confirmed hearing the audible case in the [29 October run](android-phone-20261029.md).
The temporary text parser's `mSound` diagnostic was excluded from these conclusions
because it mishandled whitespace before `null`; the native channel checks were
unaffected.

Only this phone's native registered ID was used, kept out of logs/source control
and passed to the existing sender through private storage/stdin. QA alerts were
cleared, helper files removed, the temporary instrumentation package uninstalled,
and the normal app reopened. Account data, unrelated notifications and network/
sound settings were preserved.

These direct sends bypass domain producers and the server sound budget. They do
not establish successful navigation for every real Plan, Spark, invitation or
sharing event. The previous broader transport tests remain in the 29 October
record; they were not all repeated on this APK. Matrix chat push remains
unimplemented; local reminders and Apple/APNs were not tested here.
