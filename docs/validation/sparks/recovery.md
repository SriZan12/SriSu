# Spark phone-change recovery — 2026-10-24

Frontend: `dev-core-architecture`, base `ee9742becd54bd1c30ad76e04e482085662d497b`.
Backend: `codex/couple-chat-rebuild`, base `ace26f623db19858bce62392c9a905cb81d8ffb1`.
These are the validation base commits; see Git history for publication commits.
Existing unrelated edits were preserved.

## Result and security boundary

Both partners recovered both answers on replacement native iOS SDK clients against
local Django/Synapse. Old clients were logged out (which erases the SDK crypto
stores) before replacements were created. Each replacement had a distinct device
ID/store; assertions confirmed the old inbound Megolm sessions were absent. Only
the user-held recovery key crossed that boundary. Four restored answers matched
the two synthetic originals. Wrong-key and cross-account account-data requests
failed. The first answer remained absent from the partner API before mutual reveal.

The app archives **authenticated answer copies**, not unverifiable imported device
keys. It uses Trixnity 5.9.2's Matrix secret-storage encryption, backed by a random
256-bit key. Original chat encryption/trust checks are unchanged. Copies are
cryptographically bound to the original envelope hash and audience/question; the
normal authorized reveal response is required before displaying a recovered copy.
Django still enforces release timing. No claim of cryptographic fair exchange.

See [flow and deployment details](../../flows/sparks.md#phone-change-recovery-2026-10-24)
and the [Matrix secret-storage specification](https://spec.matrix.org/v1.16/client-server-api/#secret-storage).
This application-specific archive has automated security regression coverage; it
is not claimed to have received an independent cryptographic audit.

## Checks actually performed

| Check | Result |
| --- | --- |
| Full Android JVM suite `:composeApp:testDebugUnitTest` | 298 tests, zero failures, one existing opt-in skip |
| Targeted iOS simulator state/crypto/render suites | 30 tests, zero failures/skips |
| Opt-in `SparkRecoveryLiveTest` with two synthetic accounts on local Django/Synapse | Passed; two replacement devices, four restored answers |
| SDK encryption regressions on Android + native iOS | Wrong key/account, envelope reorder/binding, changed question/epoch/room, corrupt MAC, exact retry/lost acknowledgement and interrupted setup passed |
| Backend isolated suite | 258 tests, zero failures; 15 PostgreSQL-only skips |
| Backend system/migration checks | No issues / no model changes needing migration |
| Shared theme checker | Passed; 24 role pairs, minimum contrast 4.75:1 |
| Signed Debug iOS simulator Xcode build | Passed; installed on iPhone 17, iOS 26.5 |
| Local Synapse database check | Eight QA recovery account-data entries; zero containing the synthetic answer marker in plaintext |
| Diff whitespace / contract equality | Passed |

The real transport test lives in `composeApp/src/iosTest/.../SparkRecoveryLiveTest.kt`.
It is opt-in via `SIMCTL_CHILD_SRISU_SPARK_RECOVERY_LIVE`, with a private, fresh
synthetic-couple fixture and normal development OTP authentication. It does not
run live work in ordinary tests. No secrets or answer text enter test output.
The test refuses an existing backup rather than resetting it. Temporary Django
sessions were revoked after the check; secret fixture files were removed.

## UI review

On the signed-in **iPhone 17**: Home → existing Spark → key icon opens recovery.
The existing answer still decrypted and sign-in survived installation/restart.
Setup clearly asks for an externally saved key; key entry is masked. The software
keyboard leaves the Recover action reachable. Initial testing found wrong-key
feedback below the keyboard; the correction puts it beside the field, marks the
field invalid, and clears focus on submission. Final on-device error rendering
was visually checked. Invalid test input was cleared, and the app was left on
recovery setup. No recovery key was created for the user's account.

- [Actual iPhone setup](iphone17-recovery-setup.png)
- [Actual iPhone corrected error](iphone17-recovery-error.png)
- [Native iOS recovery layout](ios-recovery-unlock.png)
- [Android recovery layout](android-recovery-unlock.png)
- [iOS small display / large text](ios-recovery-error-small.png)
- [Android small display / large text](android-recovery-error-small.png)

Automated renders use synthetic fixtures at 390×844 and 320×640, including 1.6×
text. Larger content scrolls. Recovery is a derived extension of the existing
Satoshi/theme components. Live Figma node `184:1634` metadata access was attempted
but blocked by the connector's Starter-plan quota; no exact new Figma frame is
claimed.

## Limits

- A backup must be created while a device can still open the answers. Neither a
  new phone nor an OTP can reconstruct keys/answers that were never backed up.
- Each partner saves their own recovery key. The app does not silently reset an
  existing recovery setup or provide server-readable escrow.
- Automatic backup runs only while the existing secure conversation is active in
  the foreground; use **Back up all answers** before replacing a phone. Partial
  results/network failures are explicit and retryable.
- This recovers Spark answers, not unsent drafts or all chat history.
- Recovery was tested with native SDK clients, plus interactive iPhone UI checks;
  two interactive phone UIs, Android device networking, physical-device secure
  storage, and production deployment are not claimed.
- The additive history route needs backend deployment before the client. Recovery
  introduces no new database migration or dependency upgrade. Remote deployment
  was not performed.
