# Sparks validation — 2026-10-23

**Follow-up:** [Live two-client flow and fixes](live-flow.md) now verifies the
signed-in iPhone, native counterpart encryption, mutual reveal, drafts, retries
and real keyboard behavior. The initial sign-in limitations recorded below are
historical; the live report records the remaining new-device key-recovery gap.
It also corrects the historical signing-disabled Xcode command below: native
Keychain checks must use the signed simulator command in the follow-up report.

Implemented on existing feature branches in both repositories. No commit, push,
branch switch or remote deployment. Previous unrelated patches, planning notes,
`.DS_Store` and uploaded Moment media remain untouched.

## Checks actually performed

| Check | Actual result |
| --- | --- |
| Theme validator | Passed: 96 Figma colors retained, 24 role pairs at least 4.5:1 (minimum 4.75:1), no raw UI color/type/corner violations |
| Full Android unit/render suite | 284 tests, zero failures, one existing opt-in live HTTP test skipped |
| Android shared compilation | Passed |
| iOS simulator shared compilation | Passed |
| iOS Sparks suite | 16 tests, zero failures/skips: 9 state/reference/identity tests, 6 renders, 1 real SDK native-cipher test |
| Final layout correction | Focused Android 9-render and iOS 6-render suites passed; screenshots refreshed and reveal layout visually rechecked |
| Full Xcode Debug simulator app build | Passed; app installed and launch inspected on iPhone 17 / iOS 26.5 |
| Backend Django checks and migration consistency | Passed, no pending model changes |
| Isolated SQLite allowlist | 257 tests, zero failures; 15 PostgreSQL-only tests skipped |
| Isolated PostgreSQL suite | 256 tests, zero failures/skips; includes all 3 new Spark concurrency tests. The subsequently added response-schema assertion also passed in the 257-test SQLite run |
| Mirrored API contract/catalogue | Frontend/backend copies byte-identical |
| Local Docker migration | `social.0017_sparksession_sparkanswer_and_more` applied successfully |
| Running local API | Restarted the existing Daphne web service to load the new routes; unauthenticated `/api/social/sparks/home/` returns the expected 401 |
| Diff whitespace | Both repositories passed |

The existing skipped Android test is `CoreLocalTransportIntegrationTest.pairedHttpDatabaseAndLogout`;
its opt-in local HTTP environment was not started. No claim of live HTTP/native
end-to-end validation is inferred from these unit tests.

Commands used (JDK: Android Studio bundled runtime):

```sh
python3 tools/theme/check_theme.py
./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:iosSimulatorArm64Test --device 0055683D-7DBA-44C7-855D-4AA64123C522 --tests 'com.srisu.srisu.features.sparks.*'
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/srisu-chat-recovery-ios ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# Backend working directory:
.venv/bin/python tools/workspace.py check
.venv/bin/python tools/workspace.py migrations
.venv/bin/python tools/workspace.py test
.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin
docker compose exec -T web python manage.py migrate social 0017 --noinput
```

## Visual evidence and review

These are production Compose components with synthetic identities/answers in test
source sets only. Standard size: 390 × 844 at 2× density. Small display: 320 × 640
at 1.6× text. Android uses Robolectric API 35 native graphics; iOS uses the actual
Compose/Skia simulator runtime on the dedicated test simulator above. OS keyboard,
interactive back gestures and real partner network traffic are not in the fixtures.

| State | Android | iOS |
| --- | --- | --- |
| Four decks / question browsing | [Screenshot](android-decks.png) | [Screenshot](ios-decks.png) |
| Answer / partner submitted | [Screenshot](android-answer.png) | [Screenshot](ios-answer.png) |
| Waiting / own answer | [Screenshot](android-waiting.png) | [Screenshot](ios-waiting.png) |
| Both answers revealed | [Screenshot](android-reveal.png) | [Screenshot](ios-reveal.png) |
| Dark mode | [Screenshot](android-decks-dark.png) | [Screenshot](ios-decks-dark.png) |
| Small display / larger text | [Screenshot](android-answer-small-large-text.png) | [Screenshot](ios-answer-small-large-text.png) |
| Pending send / retained draft | [Screenshot](android-failed.png) | — |
| Missing encryption keys | [Screenshot](android-keys.png) | [Screenshot](ios-keys.png) |
| Skipped | [Screenshot](android-skipped.png) | — |

Review corrections: the initially clipped fourth deck became a responsive four-/
two-column selector. The large browsing card becomes a compact question on answer
and reveal screens. Primary action stays outside the scrollable content and uses
IME padding. Both answer cards remain labelled; long content scrolls; the deck
return action sits beside the reveal heading. An interrupted create acknowledgement
now clears its durable intent after reconciliation. Uncertain answer retries reuse
the ciphertext, while definitive validation errors allow correction. Matrix access
revocation cancels decryption and removes previously displayed answers. Duplicate
chat cards have one stable logical list identity across device retries/pagination.

There was no existing implemented Sparks screen to capture as a baseline. The Figma
reference was browser-inspected in the preceding design discussion; current MCP
extraction/artwork export remained quota-limited. The new glyph is an intentional
substitute for the unavailable illustration, not a claimed pixel-perfect asset.

## Encryption-test scope and remaining gaps

The native test uses the pinned Trixnity Megolm encryption service and real
vodozemac cipher, with separate in-memory sender/receiver stores. It verifies
round-trip, exact retry, envelope shape/no plaintext, tamper rejection, question
binding, replay to another session, sender-key matching, and missing-key failure.
Authenticated key delivery is injected in that test; **live Olm/Synapse exchange
between two actual devices is not verified**. Backend tests independently verify
the ciphertext release gate, authorization and concurrent state transitions.

The installed iPhone 17 app opened at sign-in after installation. No personal
answer or QA Spark was created in the user's couple. Live Home/chat entry paths,
keyboard-open composition, app relaunch with real keys, new-device key recovery,
and iOS interactive gestures remain unverified against two signed-in accounts.
No prompt was sent to the partner and no private chat contents were captured.

The final Xcode build, including the reveal-layout correction, was installed and
launched on iPhone 17; it still shows sign-in. The local services remain running
for testing after sign-in. See
[the flow](../../flows/sparks.md) and
[the backend threat model](../../../../SriSu-backend/docs/sparks.md) for server-trusted
release timing, rollout order and key-recovery limits.

Phone-change recovery was subsequently implemented and tested on 2026-10-24. See [recovery validation](recovery.md); the earlier new-device limitation now applies only to answers never backed up.
