# Authenticated Moments flow — 2026-10-23

Actual SriSu app on **iPhone 17, iOS 26.5**, connected to the local Django/Synapse
development environment. Tests used synthetic colored images and clearly marked
QA Moments. These checks are distinct from the earlier native-picker harness and
Compose fixture renders. No real chat message was sent.

Frontend: `dev-core-architecture`, base `98f17e769bf4d0a43b444919f6f81aac91b76b3b`.
Backend: `codex/couple-chat-rebuild`, base `2a7d1f7d502bbaf3fb11b2330835a0d64bbe3ea5`.
Both working trees contain existing changes. Live integration refs were checked:
frontend `dev-new-theme` at `3fa6822ea05578d64cca8891c5e3fdb0a831897a`, backend `dev`
at `ecc8592307114e7d154bda9612e6826e2ca8ba90`. No branch switch, commit or push.
Design reference remains Figma `LztysD1YvINX7RwZpnhhTt`, Home page `1:3`.
Connector quota prevents exact layer/asset verification; no new fidelity claim.

## Coverage

| Area | Actual iPhone 17 result | Other evidence / boundary |
|---|---|---|
| Gallery and five-photo limit | Selected three, then two; ordered thumbnails matched. At five, add controls disappear. Removed one, cancelled the picker without losing content, then restored the fifth. No false 10 MiB error. | Native adapter harness previously checked overflow and one/three remaining slots. Android native picker UI not exercised in this run. |
| Reorder / caption / keyboard | Moved selected photo later and earlier, retained caption, verified Continue and photo controls above software keyboard. | Small-screen / 1.6× text fixture renders also pass. |
| Draft recovery | Kept a caption draft on exit and restored it on reopening. | State tests cover upload failure, uncertain responses, preserved publication identity, metadata restoration, stale account callbacks. Unuploaded bytes intentionally do not survive leaving the screen. |
| Audience and publishing | Private is the default. Published five-photo private and two-photo public Moments; public warning explicitly explained audience and private appreciations. | Real KMP HTTP integration covers staging, upload, publish and replay. |
| Viewer | Correct order, next/previous, automatic progression, pause/resume, readable caption and truthful unseen status. | Long text / small-screen viewer fixture also renders. |
| Creator controls | Caption edit persisted; delete cancellation retained content. Both approved QA deletions showed “Moment deleted.” | Server tests cover creator-only mutation, revision conflicts and no expiry extension. |
| Expiry / resume | Backgrounded on synthetic public Moment, expired only that fixture on server, resumed: photo cleared and expiry explanation appeared. Global then showed its empty state. | State tests check exact deadline and stale pixel removal. |
| Private appreciation sender | Sent note through app; confirmation and cleared input. “Your appreciations” showed the note and a synthetic recipient reply. | Other side of the exchange was created through existing backend API with a synthetic actor. |
| Private appreciation recipient | Own public Moment initially had no appreciations. Synthetic incoming note appeared; “Reply privately” sent a reply through the app and displayed it in the thread. | Unrelated actor denied access to the private Moment, photo and another sender’s note (404). Force-authenticated fixture calls test authorization, not authentication transport. |
| Global / Faves | Public fixture appeared in Global; added to Faves, appeared there, removed, then empty-Faves explanation appeared. | Fave change undone before fixture cleanup. |
| Home / Chat / Profile | Correct selected pill; Profile→Chat→Back returned to Profile. Home→Explore→Back returned to Home. Bottom bar absent in chat, composer, viewer and discovery. | Navigation ownership tests pass; iOS edge-back gesture was not exhaustively tested. |
| Moment→Chat | First opening stalled at secure verification; separate Home→Chat opened correctly. After rebuilding, Moment→Chat reached the encrypted conversation with enabled composer and correct reply context. Cancel removed context; Back returned to the Moment. | Identified a timeout handling defect and added regression coverage; see below. No encrypted reference message was sent to the real partner. |
| Partner heart and receipts | Creator correctly had no self-heart control and showed the real unseen state. | Partner-authored heart toggling / receipts tested by backend and client fixtures, not a second authenticated native device. |
| Loading / failure / security | Empty appreciation list, empty discovery/Faves, expired and deleted states observed. | Backend and client tests cover malformed/oversized files, upload/storage failures, retry/idempotency, pagination identity, duplicate/concurrent writes, session/account boundaries, cross-couple/private media denial and expiry. Not every fault was injected through native UI. |
| Camera | Not verified on hardware. | iOS simulator cannot establish camera capture quality or permission recovery. |

The notes list exposes Load more. Individual appreciation replies are displayed
from the embedded response; long reply threads beyond that server response are
not exposed through a separate reply-pagination control. This is a functional
limit, not a claim of full long-thread verification.

## Defect corrected

`TrixnityMatrixChatRepository` treated `TimeoutCancellationException` like room
ownership cancellation. A nested bootstrap/secure-verification timeout could
therefore leave the screen in `Loading` indefinitely. A new regression test
failed with actual `Loading` before the fix, then passed with a retryable error
and successful retry invocation. Open-room and foreground-validation timeout
handlers now surface a retry message. Parent cancellation still propagates;
stale room/account operations cannot publish the error. Secure-room verification
and the sender gate remain required. This reproduces a concrete defect consistent
with the observed stall; it does not prove the original network timeout cause.

Changed code: `features/chat/matrix/TrixnityMatrixChatRepository.kt` and
`features/chat/MatrixPresentationTest.kt`. No backend source changes were needed
for this verification follow-up.

## Checks actually run

- 73 focused Android/JVM tests: 16 Matrix presentation, 4 outbox gate, 7 Matrix
  session, 15 Moments state, 22 Moments renders, 9 navigation ownership. Zero
  failures/errors/skips. Android shared compilation and iOS simulator Kotlin
  compilation passed in the same Gradle invocation.
- Real KMP ↔ disposable Django HTTP integration passed; temporary server,
  database and credentials were removed by the runner.
- Disposable PostgreSQL backend suite: **245 tests, no skips**, passed. Includes
  real row-lock/concurrency tests. This is the full allowlisted suite, not 245
  Moments-only tests.
- Xcode arm64 simulator build passed; installed on iPhone 17 only. Installation
  returned the app to sign-in; the user completed sign-in and the updated
  Moment→Chat route was rechecked successfully. Reinstall session retention is
  not established by this run.
- Theme audit passed: documented colors retained and all 24 M3 text-role pairs
  at least 4.5:1. `git diff --check` passed.

Commands (Gradle uses Android Studio's bundled JDK):

```sh
./gradlew :composeApp:testDebugUnitTest \
  --tests 'com.srisu.srisu.features.chat.Matrix*Test' \
  --tests 'com.srisu.srisu.features.moments.*' \
  --tests 'com.srisu.srisu.navigation.NavigationOwnerTest' \
  :composeApp:compileKotlinIosSimulatorArm64 --console=plain
python3 tools/core_integration.py --backend ../SriSu-backend
# From the backend checkout:
.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin
```

Local logs: `/tmp/srisu-moments-full-flow-fix.log`,
`/tmp/srisu-moments-full-flow-http.log`, `/tmp/srisu-moments-full-flow-postgres.log`,
`/tmp/srisu-moments-full-flow-xcode.log`.

## Captures

- [Keyboard-open multi-photo composer](composer-keyboard.png)
- [Private appreciation sender thread](private-appreciation-thread.png)
- [Private appreciation recipient reply](recipient-private-reply.png)
- [Expired on resume](expired-on-resume.png)
- [Deleted QA Moment](deleted-qa-moment.png)
- [Deleted public QA Moment](deleted-public-qa-moment.png)
- [Actual Home navigation](iphone17-home-navigation.png)

These are actual iPhone 17 app captures. The photos and appreciation text are
synthetic QA content. No existing private chat transcript is captured.

## Cleanup and final state

Both user-approved QA Moments were deleted through the actual UI and their absence
verified on the server. The synthetic recipient couple, its expired Moment,
appreciations/replies and two users were removed with exact-ID/name guards.
The existing non-QA Moment was retained. Synthetic gallery images remain available for
the user's further picker checks. The app is signed in on iPhone 17 at Home;
the local development services remain running.
