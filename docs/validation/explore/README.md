# Explore and personal Faves — 26 October 2026

Implemented across the current checkouts without switching, committing, pushing or
deploying. Existing uncommitted full-app QA changes and unrelated files were retained.

| Source | Checkout / branch | Starting commit |
| --- | --- | --- |
| Frontend | `SriZan12/SriSu`, `dev-core-architecture` | `b6b1576b9ed26b270e9843f3ea4309a4ee3a93cb` |
| Backend | `SrizanKhadka/SriSu`, `codex/couple-chat-rebuild` | `6e0935c1c4f24267b9a956d259db5d113e9bb903` |

`python3 tools/workspace.py status --remote` verified the separate integration branches
at task start: frontend `dev-new-theme` at `3fa6822ea05578d64cca8891c5e3fdb0a831897a`,
backend `dev` at `ecc8592307114e7d154bda9612e6826e2ca8ba90`.

## Audit and implemented corrections

The existing backend already had the correct individual-user/couple Fave relation,
uniqueness, eligibility predicates, saved-list pagination and feed sessions. Reused those
instead of adding another favourites domain. The old discovery screen reset its list,
had no saved-couple management, and used independent profile/card Fave state. Global's
worldwide candidate cap could exclude local supply before ranking.

- Added Home → Explore → Chat → Profile through existing BaseNavigation. Global/Faves
  retain separate list positions and request state. Identity opens the existing Couple
  Profile; the media preview opens the existing chronological/first-unseen Moments viewer.
- Added private, paginated Manage Faves, including couples without active Moments and
  removal of unavailable entries. Empty Faves never substitutes Global content.
- Added one account-scoped `FaveStore` for cards/profile, ordered conditional writes,
  authoritative confirmation, failure/retry and stale-response protection. No offline
  success is invented. Partners retain independent preferences.
- Kept the established configurable 40/25/20/15 interest/locality/own-Fave/freshness
  weights. Missing interest/locality evidence is excluded and remaining weights normalized;
  a known mismatch remains zero. These are hypotheses, not validated ranking outcomes.
- Retrieve and compose city, country, then broader tiers before the 500-couple Global cap.
  Exploration uses the existing every-tenth-position opportunity within each tier.
  Explicit broader scope removes the geographic tier. Faves is uncapped by Global and
  sorts unseen first, then freshness/stable ID, regardless of geography.
- Bind feed sessions to account, section, scope, ranking version and preference revision.
  Revalidate eligibility/expiry on delivery and viewer return. Never rerank a visible
  Global list simply because its bookmark changes.
- Include names/avatars only when both current members' identity consent is valid.
  No private member serializer, public Fave list/count, notification or permission expansion.

Backend details remain in [the existing recommendation guide](../../../../SriSu-backend/docs/couple_recommendations.md).
Client integration is documented in [Moments](../../flows/moments.md) and
[navigation](../../flows/navigation.md).

## Rendered and native evidence

The same 12 synthetic states were rendered through Android Robolectric native graphics
and iOS simulator Skiko: Global, Faves, empty, no-active, Manage, loading, error, saving,
retry, caught-up, dark, and 320-point / 1.6× text layouts. These are real Compose renders,
not design mockups. They do not establish Android device/runtime behavior.

| State | Android render | iOS render / actual iPhone 17 |
| --- | --- | --- |
| Global + four destinations | [Android](android-global.png) | [iOS render](ios-global.png), [live app](iphone17-global.png) |
| Active Faves | [Android](android-faves.png) | [iOS render](ios-faves.png), [live app](iphone17-faves.png) |
| Empty Faves | [Android](android-faves-empty.png) | [iOS render](ios-faves-empty.png), [live app](iphone17-faves-empty.png) |
| Saved, no active Moments | [Android](android-no-active.png) | [iOS render](ios-no-active.png), [live app](iphone17-no-active.png) |
| Manage Faves | [Android](android-manage.png) | [iOS render](ios-manage.png), [live app](iphone17-manage-no-active.png) |
| Saving / retry | [Saving](android-saving.png), [retry](android-retry.png) | [Saving](ios-saving.png), [retry](ios-retry.png) |
| Loading / error / caught-up | [Loading](android-loading.png), [error](android-error.png), [caught-up](android-caught-up.png) | [Loading](ios-loading.png), [error](ios-error.png), [caught-up](ios-caught-up.png) |
| Dark / small with larger text | [Dark](android-faves-dark.png), [larger text](android-global-small-large-text.png) | [Dark](ios-faves-dark.png), [larger text](ios-global-small-large-text.png) |

Actual iPhone 17, iOS 26.5, local Docker backend: opened Global; saved a synthetic couple;
opened Faves; opened both existing Moments; returned with seen state updated; opened
Couple Profile; removed there; returned to empty Faves; added again; expired only the
two synthetic Moments; confirmed the couple remained in Manage Faves; removed from
Manage and observed empty state. Database assertions confirmed the other partner did
not inherit the preference. Both-partner concurrent behavior was additionally tested on
PostgreSQL; this was not a two-phone manual test.

Focused correction pass:

1. Explore wrapped in the narrow/large-text bottom bar. Responsive padding now keeps its
   label on one line; visually checked in both render targets.
2. Removing cards during return-time authorization reset reading position. Keyed geometry
   now remains while protected pixels, actions and semantics are hidden; regression test
   asserts position and independent tab offsets.
3. That retained layout exposed an image-effect restart issue on the actual iPhone after
   closing the viewer. Restarting the guarded image subtree when authorization changes
   fixes it. [Latest live return screenshot](iphone17-return-fixed.png) shows the preview
   restored. A regression test asserts no unauthorized image request and renewed loading
   on return. Profile → back was also checked on the latest signed build.

[Existing Moments viewer](iphone17-existing-viewer.png) and
[synchronized Couple Profile](iphone17-profile-synced.png) were reused unchanged.
Only the task's two synthetic local accounts, couple, two Moments, files and preferences
were cleaned up afterward. Earlier user/QA data, drafts and calendar event were retained.
Home and Profile tabs opened successfully. Chat initially could not reach the old LAN
address advertised by the local backend. Updated only the ignored development `.env`
`MATRIX_HOMESERVER_URL` from `192.168.100.168:8008` to the Mac's current
`192.168.1.65:8008` and recreated the local web container. Retry then reached
"End-to-end encrypted" with its composer available. Older messages without keys still
displayed the existing unavailable-on-this-device state. No new chat message or
history recovery was tested in this Explore pass. Returning from Chat revalidated
Explore and removed the cleaned-up couple from the feed.

## Commands and actual results

Frontend commands used the Android Studio JBR. For local iOS/HTTP validation the process-only
override `GRADLE_OPTS=-Dorg.gradle.project.srisu.apiBaseUrl=http://127.0.0.1:8000/` took
precedence over an obsolete LAN URL in the user's Gradle properties. Those global
properties were not edited.

| Check | Result |
| --- | --- |
| `python3 tools/theme/check_theme.py` | Pass: 96 colors retained; 24 contrast pairs, minimum 4.75:1 |
| `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:iosSimulatorArm64Test --device 0055683D-7DBA-44C7-855D-4AA64123C522` | Final run passed. Android: 379 reported, 378 passed, 1 opt-in HTTP test skipped. iOS: 292 reported, no failures; four existing integration gates return without their opt-in environment, leaving 288 regular executed cases |
| `JAVA_HOME=… python3 tools/core_integration.py` | Pass: actual KMP ↔ isolated Django HTTP, including public/hidden identity, protected photo, conditional Fave conflict, profile/feed agreement, visible revalidation, expired/deleted-content exclusion and retained saved library. Temporary server/credentials removed |
| Backend `.venv/bin/python tools/workspace.py test` | 293 reported, 270 executed, 23 SQLite row-lock skips; pass |
| Backend `.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin` | 293 passed, no skips, isolated PostgreSQL; includes simultaneous opposite conditional writes |
| `python3 tools/workspace.py verify backend` | Pass, including system/migration/contract checks and selected isolated suite |
| Contract sync and route checks | Pass; digest `853926f4113d24d5b7cfb635828c120d31ba1441fb08afd9a483850b8b9cb873` |
| Signed `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/srisu-chat-recovery-ios ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build` | Build succeeded; installed latest app on iPhone 17; existing login preserved |
| `git diff --check` in both repositories | Pass |

Tests cover personal ownership/privacy, duplicate writes, conditional no-op barriers,
lost acknowledgement/retry, old account responses, competing Faves loads, profile entry
during a pending write, cursor/session isolation, local candidate caps, cold starts,
geographic moves, consent withdrawal, expiry and media return behavior. Existing Home,
Chat, Sparks, Challenges, Plans, Moments and navigation regression suites also passed;
that does not substitute for repeating every feature's native flow.

## Compatibility and limits

- Applied additive migration `social.0021_couplefaveversion` to the local Docker database.
  Existing Fave records remain. Release backend migration/endpoints before the new client.
  Older unconditional PUT/DELETE callers remain compatible; DELETE still returns 204.
- Figma file `LztysD1YvINX7RwZpnhhTt`, system `0:1`, Home `1:3`, bottom pill `34:1008`.
  Live Home metadata was accessible; design-context/screenshot calls were blocked by the
  connector's Starter-plan quota. No Explore frame was found in that metadata. This is an
  intentional extension of existing tokens, not a claim of pixel-perfect Figma fidelity.
  `SriSu_Moments_Recommendation_System.pdf` was unavailable in workspace/attachments.
- No Android emulator/manual run, real-device test, comprehensive VoiceOver session or
  new production-scale benchmark. Pagination/concurrency uses deterministic backend and
  state tests; loading/failure visual states use synthetic render fixtures.
- No new block/hide, analytics, follower, messaging, Challenge or video-discovery system.
  Existing eligibility remains authoritative; saved preferences confer no access.
- Local services were started for testing. No production service or Figma file was changed.

## Follow-up completeness review — 26 October 2026

Re-read the complete Explore/Faves client, server, navigation integration and regression
coverage on the same branches/bases. Found and corrected these additional edge cases:

| Finding | Correction and regression coverage |
| --- | --- |
| Returning to a retained tab trusted old authorization | Reauthorize its IDs before showing content; retain its list geometry and scroll |
| An empty feed never looked for new content on return | Fetch a new page when there are no IDs to validate, including the periodic recheck |
| Removing the last Fave on another device could show the wrong empty explanation | When revalidation removes every card, fetch current membership/supply and the authoritative empty reason |
| Retry after a failed Manage Faves continuation did nothing; reopening trusted old rows | Retry and reopening refresh the library; closing cancels outstanding work |
| Saved-library pagination could merge earlier removed rows with a later revision | Compare page revisions and restart assembly on change; hide obsolete rows during the restart |
| An uncooperative cancelled local request could replace newer broader results | Check coroutine cancellation after every suspended Explore request before applying it |
| Saving city/interests could retain recommendations and cursors based on old preferences | Detect saved preference changes on return; bind backend Global sessions to normalized locality/effective interests; leave Faves geography-independent |

Seven new shared state tests and one backend regression cover these paths. The first
focused run reproduced the six initial new state regressions; after correction the
entire suite passed. The existing retained-position/render tests still pass.

Verification in this review:

- `python3 tools/workspace.py status --remote`: same checkouts and live integration refs
  as the provenance table above; all previous work retained.
- `python3 tools/workspace.py verify all`: passed frontend builds/tests, theme checks,
  backend system/migration/contract checks and 294 backend tests (23 SQLite lock skips).
- Final Gradle run with the same commands/device and loopback override as above: Android
  **386 reported, 385 passed, one opt-in transport test skipped**; iOS **299 reported,
  zero failures**, including four unchanged environment-gated cases (295 regular cases).
- Disposable PostgreSQL runner: **294 passed, no skips**.
- Theme check: passed unchanged, minimum contrast 4.75:1. Both `git diff --check` passed.
- No schema/migration or public API shape change in this follow-up. Existing cached
  Global sessions without the preference hash request refresh; no saved records change.

The original HTTP integration and screenshot evidence remains above. This follow-up
does not claim a new Android-device, two-phone or comprehensive VoiceOver session.

The latest signed Xcode simulator build also passed and was installed on iPhone 17
without losing the login. Native follow-up: opened [empty Global](iphone17-review-empty.png),
left for Home, added synthetic public Moments in the local test database, then returned.
[New content appeared without pressing Refresh](iphone17-review-new-content.png).
Switched to Faves, changed those two synthetic Moments to private, then switched back:
[Global reauthorized and removed them](iphone17-review-withdrawn.png). Cleaned up only
the follow-up's two synthetic accounts, couple, Moments and photo files. The final build
and local server remain available; nothing was committed, pushed or deployed.

