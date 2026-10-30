# Matrix chat push acceptance — 30 October 2026

The selected release line is `dev-core-architecture` in both repositories:
frontend `SriZan12/SriSu` and backend `SrizanKhadka/SriSu`. The implementation starts
from frontend `27b3d39` / backend `5fb1237`. Frontend runtime changes are in
`8190a9d`; subsequent commits refine the opt-in device test, with the passing test
at `a2d6a80`. Backend merge `11891049ef323b6df3c6ee3c8f0688442b18c13f` is deployed
on the Windows Docker laptop. Apple enrollment and APNs remain skipped.

## Checks completed

- Backend: 347 tests passed against disposable PostgreSQL, including concurrent
  AS transaction deduplication, current relationship authorization, stale session
  and binding fences, mapping replacement/revocation, read/redaction suppression,
  mute/kill switches, rollout cutoff, legacy/iOS exclusion and the sound budget.
  The isolated SQLite run also passed (344 tests at that earlier checkpoint,
  with 28 PostgreSQL-only skips). Migration drift and Django checks passed.
- Android: all 26 focused notification unit tests passed. Application and device
  test Kotlin compilation passed; shared iOS simulator Kotlin compilation passed.
  Theme checks passed. The two repositories' notification schemas match exactly.
- Physical phone: Samsung SM-A075F, Android API 36, connected to Windows by USB.
  The handset had no SriSu installation before this task. Only the fresh synthetic
  test installation was used. The previous Realme handset was not connected.
- The real `MatrixPushDeviceIntegrationTest` passed: `OK (1 test)`, 66.536 seconds.
  Two independent protected Matrix stores exchanged encrypted messages through
  the isolated Windows Synapse/Django stack. The receiving phone registered its
  native Firebase ID and new Matrix capability with Django. No Firebase Console
  test notification or client-created notification event substituted for Matrix.
- Foreground delivery created a native signal without an OS alert. An unrelated
  synthetic account could neither bootstrap the room nor resolve the delivery.
- Background delivery showed generic title/body on `srisu_updates`. Android
  reported normal ringer mode, nonzero notification volume, no DND restriction,
  and an audible channel. A quick follow-up used `srisu_updates_quiet` with no sound.
  The assistant cannot hear the physical speaker; these are OS sound-policy checks.
- The real notification PendingIntent opened MainActivity. The protected chat
  showed the decrypted synthetic message from the intended room and an editable
  composer. Android screenshot protection remained enabled. Disabling chat
  notifications then suppressed the next encrypted message's OS alert.
- A Synapse database audit found encrypted events and zero occurrences of the
  synthetic message plaintext. The private AS registration was `0600`, owned by
  Synapse's runtime UID/GID `991:991`.

The device fixture uses a separate database and server identity, three synthetic
accounts, and USB loopback ports 8029/18008. The production application contains no
fixture loader or test event endpoint. No personal conversation was sent or read.
The read-only live LAN checks and final APK handoff are recorded below.

## Test harness corrections

Earlier runs were not counted as passes. Auto-lock caused Android to classify a
nominal foreground test as background and prevented visible tap checks. The user
unlocked the phone, and its original plugged-in awake setting was saved before
being temporarily changed for testing. The acceptance now requires a visible,
unlocked foreground activity. A regular Android activity scenario avoids imposing
Compose's test clock on a separately launched MainActivity. Chat assertions traverse
Compose's virtual accessibility nodes, including message content descriptions, and
check the editable composer rather than assuming Send is visible with an empty draft.
No screenshot-protection bypass or production navigation workaround was added.

## Windows deployment

The running backend is checked out at `D:\Djangos\SriSu-matrix-push-20261030`,
using image `srisu-backend-app:matrix-push-1189104` and Compose project `srisu`.
The override is `D:\SriSu-tools\deployment\compose.matrix-push-20261030.json`.
The existing application database, Matrix database, Synapse identity/signing data
and media mounts were retained. Only additive notification migration 0004 was
applied; the historical destructive chat migration was not rerun.

Fresh application and Matrix dumps were independently restored in disposable
PostgreSQL containers. They and the Synapse data archive are retained privately at
`D:\SriSu-secrets\backups\matrix-push-20261030T123443Z`. The ignored environment
contains the private ingress credential and rollout cutoff. The independent AS
client credential stays in Synapse's private volume. No credentials are committed.

After rollout, Django checks, migration checks, Matrix live readiness and the
notification status command passed. Matrix push, ingress configuration and the
no-backfill cutoff reported enabled/configured; there was no pending live queue.
For enable/disable and rollback steps see backend `docs/notifications.md`.

Figma `LztysD1YvINX7RwZpnhhTt`, page `0:1`, was quota-limited. Existing notification
controls and theme tokens were reused; there is no new visual layout.

The private callback was also pinged from the deployed Synapse container using its
own private registration: HTTP 200, without emitting any message event or exposing
the credential. Final isolated audit: 21 encrypted Matrix events, 20 accepted push
deliveries, one disabled-chat event without a delivery, and three read-suppression
markers. The audit found zero synthetic plaintext occurrences in Synapse storage.
After acceptance, all 30 synthetic device sessions were revoked, the single stored
push target was cleared, private session fixture files were removed, and only the
isolated test containers were stopped. The live seven-container deployment remained up.

## Final Android LAN handoff

Frontend merge `a080d49b46a47d690b4090020a6fbecafdb8ee35` was built on Windows with
`-Psrisu.apiBaseUrl=http://192.168.1.69:8000/`. The normal app APK SHA-256 is
`72d3f65440c30393dc110ad517f2698f5f3637e8df35baa4ef1197ff8f94aa33`.
The final validation-record commit changes documentation only; runtime code is identical.

The final APK was installed on the Samsung after removing only this task's fresh
synthetic app state. The USB test reverses were removed. `DeploymentDeviceSmokeTest`
passed on the phone (`OK (1 test)`, 2.19 seconds), verifying the configured LAN API,
public catalogue response, denied anonymous notification preferences, denied anonymous
Matrix AS ingress, Synapse client versions/policy endpoints, and real MainActivity launch.
These are read-only checks against the running deployment, separate from the encrypted
message/FCM exercise against the isolated Windows databases.

The test APK was uninstalled, the app was opened normally and notification permission
is granted. It is signed out and ready for the user's own login. The phone's original
plugged-in awake setting was restored and verified. The temporary marker authorizing
fresh-install cleanup was removed so the runner cannot later clear a personal login.
Apple remains skipped. No claim is made about hearing this phone's physical speaker,
Apple push delivery, two physical devices, force-stopped app delivery, or release-store signing.
