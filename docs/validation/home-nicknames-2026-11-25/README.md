# Home refresh and personal nicknames — 25 November 2026

Both original checkouts use `codex/home-refresh-partner-nicknames`.
Frontend base: `3872d11ca4d17a40691040830a28efed918bae74`.
Backend base: `8e5c91645caae34541d5d523afab0dc0b5b77c2f`.
Publication targets `dev-core-architecture` in both repositories. Production
deployment and migration are not performed by the publication workflow.
The prior dark-mode changes and unrelated local work are preserved.

## Implemented behavior

- Home uses swipe-to-refresh at the top of the list, with no header refresh
  button. Pending reads ignore duplicate gestures, loaded content stays visible,
  and stale/error retries remain. A custom accessibility action provides the same
  refresh without requiring a gesture. Refresh also reloads the current account's
  partner nickname.
- Settings → Together → Partner → Nickname saves, changes or clears the personal
  label. It updates reactive name projections across the app without rewriting
  canonical identity or authored text. Drafts survive save/connection failures.
- The backend stores each person's private preference separately, checks current
  membership, fences concurrent changes with revisions and clears aliases on unlink.
  Recipient-specific notification rendering respects existing privacy controls.
- Apply backend migration `social.0025_private_partner_nickname` before deploying
  the client. The editor is unavailable against older servers. All migrations in
  this task were applied only to isolated test databases.

Full behavior, compatibility and synchronization limits are documented in
[Personal partner nicknames](../../flows/partner-nicknames.md).

## Frontend verification

| Check | Result |
| --- | --- |
| Full Android unit suite | 716 discovered, zero failures/errors, 7 existing opt-in integration skips |
| Final scoped name/Settings/Home/theme suite after reconciliation guards | 28 discovered, zero failures/errors, 1 opt-in integration skip |
| Real Kotlin client → isolated Django Settings integration | Passed: save/normalization, persistence, other-partner privacy, stranger denial, stale revision, clear and disconnect |
| Android app/test APK and iOS simulator compilation | Passed |
| Native iOS simulator tests | 24 passed, zero skips: Settings editing, clear, validation, failed-save draft, 320 dp/1.8× text, Home refresh and 320 dp/2× text, identity/emoji checks |
| Native Android Home/Settings tests | 14 of 15 initially passed; remaining keyboard test passed after correcting its inset assertion; all 15 covered after rerun |
| Theme audit | Passed: 96 Figma colors and 24 Material role pairs; minimum paired text contrast 4.75:1 |
| Backend-owned contract synchronization and whitespace | Passed in both repositories |

Android used the dedicated SriSuHomeQA Android 15 emulator (`emulator-5560`),
1080×2340. The actual IME was opened; the input was focused and Save remained above
the keyboard. Save/remove completed. The first test incorrectly required a
nonzero dialog IME inset even though the resizing dialog consumes it. The corrected
test retains native keyboard visibility and visible-window position assertions,
and the captured screen was visually inspected. No product workaround was needed.

iOS used the iPhone 17 Pro / iOS 26.5 simulator. These are native Compose test
surfaces with synthetic state, not a full signed UIKit app/live-account session.
The final shared reconciliation guards were subsequently compiled for iOS and
covered by scoped unit tests. Native iOS keyboard and physical-device behavior
were not verified. No frame-rate measurement or universal device claim is made.

The first HTTP integration build selected unsupported JDK 26 from the shell.
Rerunning with Android Studio's bundled JBR passed. This was a build environment
issue, not a failed API assertion. Test credentials and databases were temporary.

## Backend verification

- Documented isolated suite: 496 tests, zero failures, 39 expected skips.
- PostgreSQL Settings/nickname suite: 20 passed, including competing edits,
  duplicate saves and unlink races.
- Final focused Settings/notification suite: 56 tests, zero failures, 4
  PostgreSQL-only skips in that SQLite run.
- Final admin/legacy projection privacy checks: 2 passed. Generic admin forms
  explicitly exclude private nickname fields.
- Django system checks, migration drift check, contract checks and whitespace
  checks passed. No unrestricted legacy chat test discovery was used.

## Swipe-to-refresh follow-up

The refresh icon was replaced by the requested downward pull at the top of Home.
The standard nested-scroll gesture does not interfere with mid-list scrolling.
The themed indicator overlays the existing list without inserting a progress row.
Initial loading and an active refresh ignore new refresh gestures. Notifications
is the only header icon; its loading placeholder also follows the large-text layout.

Verification after this correction:

- Android native Home suite: **10 passed** on the same dedicated Android 15
  emulator, including actual swipes, duplicate prevention, no-partner/initial
  loading, mid-list scrolling, 200% text, dark/light appearance, navigation and retry.
- iOS native Compose simulator: **5 passed**, including actual swipes at
  320 dp/200% text, mid-list scrolling, loading, unpaired and refreshing states.
- Home ViewModel unit suite: **6 passed**. Android app/test builds and iOS
  simulator compilation/linking passed. Theme and whitespace checks passed.
- The initial gesture tests asserted before Material's release animation invoked
  the callback. Synchronizing with idle and the expected callback fixed the test
  timing; the actual swipes and negative duplicate/scrolling assertions remain.
- Final Android/iOS Home captures above were refreshed and visually reviewed.
  The other screenshots and earlier nickname evidence remain applicable.

This follow-up changes only the frontend; it needs no additional backend change
or migration. Simulator coverage limits above still apply. Command logs:
`/tmp/srisu-home-swipe-final-build.log`, `/tmp/srisu-home-swipe-android-final.log`.

Local command logs are under `/tmp/srisu-home-nicknames-*.log` and
`/tmp/srisu-nickname-*.log`; they are not part of the repository deliverable.

## Screenshots

- [Android Home refreshing](android-home-refreshing.png).
- [Android nickname editor with actual keyboard](android-nickname-keyboard-dark.png).
- [iOS Home refreshing](ios-refreshing-390-1.0-dark.png).
- [iOS nickname cleared, small screen and large text](ios-nickname-cleared-small-dark.png).

All captures use synthetic data. The Android IME uses its own system theme.
Existing Home/Settings compositions and theme tokens were reused. These controls
are intentional additions; there is no new Figma frame or pixel-parity claim.

## Preservation and remaining limits

The 23 pre-existing untracked frontend files were checksum-verified unchanged.
Unrelated signing configuration, local configuration, backend environment files,
personal media, Finder metadata and stashes were checked against the saved
preservation manifest. No physical phone or production account was modified.

Aliases persist on the server; the client projection is in memory. Before its
first successful fetch after restart, canonical names appear. Other devices pick
up changes on foreground/Home refresh, not through a new real-time channel.
Already-delivered OS alerts keep their original copy; iOS generic push fallbacks
remain generic. Canonical identity and authored messages remain unchanged.
