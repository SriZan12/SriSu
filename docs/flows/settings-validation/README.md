# Settings verification — 2026-11-25

Synthetic fixtures throughout. Current branches: `codex/settings-ui`, both original
project folders. No push, PR, merge or deployment. Baseline hashes and design sources
are recorded in [the flow](../settings.md).

## Issues reproduced and fixed in the QA pass

- A queued notification toggle could execute under a newly signed-in account.
  Capturing the session before dispatch/locking prevents this. Regression failed
  before the fix (one unexpected write) and passes with zero writes afterward.
- An initial temporary storage failure completed the appearance stream, so later
  saves did not update the app. Observation now retries IO failures with bounded
  backoff; tests use the distinct native DataStore and Okio exception types.
- Partner projection trusted inconsistent legacy memberships. Two privacy
  regressions initially returned 200; both now return 409 without partner data.
- Disconnect confirmation could remain open during refresh or after its revision
  changed. It now disables confirmation during refresh and closes on changed data;
  native iOS UI regression verifies that no disconnect is dispatched.
- Added both Settings routes to the backend-owned inventory and pinned KMP contract.
  Updated the opt-in navigation journey's Settings entry/return assertions.

## Logic and backend

- Full maintained isolated SQLite suite: 484 tests, no failures, 36 PostgreSQL-only skips.
- Full disposable PostgreSQL suite: 483 tests, passed, no skips. After the query-bound
  test and route-inventory update, all 18 selected Settings/API-route tests passed on
  PostgreSQL. Two simultaneous confirmations cause one transition/revocation schedule.
- Settings projection uses two bounded database queries, excluding authentication.
- `tools/workspace.py check` and `migrations`: passed, no model migration.
- Real Ktor -> temporary loopback Django integration passed (`SettingsTransportIntegrationTest`):
  linked projection, cross-account denial, disconnect, partner-observed state, duplicate retry.
  Temporary database/accounts/server/credentials removed by the existing runner.
- `:composeApp:testDebugUnitTest`: 701 tests, 694 passed, 7 opt-in integration skips.
  Settings HTTP integration was run separately above. Includes appearance persistence,
  loading vs unlinked, duplicate/stale confirmation, uncertain-response reconciliation,
  account change during request, and existing navigation/profile/notification regressions.
- After the final native IO exception adjustment, focused Settings/notification state
  and navigation tests and all 16 iOS Settings tests passed again.
- `python3 tools/workspace.py verify all` passed. After synchronizing the Settings
  route inventory, the full client suite and selected backend route tests passed again.
- Production `profileGraph` integration test at 320 dp/200% text covers Account,
  Language, unlinked Shared preferences, persisted Appearance choices, cache-clear
  cancellation/completion, Accessibility, Support, Privacy, shared notifications,
  disabled dependent switches, Back and retained Settings scroll position. Uses
  synthetic HTTP responses; it is not a live-account app journey.

## Android

`SriSuHomeQA` emulator, Android 15, 1080 x 2340, density 440. Native Compose
`SettingsDeviceTest`: 4 passed (connected, unconnected, 180% text/dark, 320 dp/200% text), covering
expansion, routes, scroll gestures, destructive-confirmation cancel/confirm, sign-out cancel.
Native screenshots were captured via the UI test on the emulator; the physical phone
was not used. The native test also checks light/dark status and navigation icon contrast.

## iOS

iPhone 17 Pro simulator, iOS 26.5. `iosSimulatorArm64Test` selected Settings suite:
16 passed = 6 common state tests + 10 native Compose UI tests. Rendered at 390 x 844,
320 x 640, 640 x 320 landscape, 180%/200% text, dark, unconnected, loading, error, and a
long header at large text. Tests exercise scroll-to-actions and confirmation buttons.
These are native iOS Compose test surfaces, not a signed end-to-end UIKit app session.
An additional full ComposeUIViewController appearance test was attempted, but the
standalone Kotlin simulator executable has no Metal device (`MetalRedrawer requires
MTLDevice`). That unsupported harness was removed; UIKit appearance switching is
explicitly unverified. Production plist checks were not disabled or changed.

## Visual review and limits

Screenshots reviewed against the attached references: neutral rows, 16 dp corners,
consistent gutters and line icons, charcoal Partner heading, lavender expanded group.
Header grows for larger text. Synthetic partner uses the intentional avatar fallback.
Language truthfully lists English only. Additional Appearance/sign-out/privacy controls
extend the source; native status bars, phone frame and reference texture are not copied.

Theme audit and git whitespace checks passed. Android/iOS compilation passed after the
last platform appearance adjustment. No new text fields: Settings itself does not invoke
a keyboard. Existing Profile render checks passed; this task did not repeat a live
photo-picker/keyboard session, real device permissions, push delivery, VoiceOver/TalkBack
speech, or live Matrix revocation. Smooth scroll gestures were exercised; no claim of
measured frame-rate performance or physical-device parity is made.

Verified support/legal links are still pending user input. Account deletion/export/session
management remain separate capabilities, not nonfunctional entries in this screen.
