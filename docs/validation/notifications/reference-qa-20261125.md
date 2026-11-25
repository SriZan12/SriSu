# Notifications: logic, functional and UI QA — 25 November 2026

Scope: the Notifications inbox and its new presentation/media contract. This is a
follow-up to [the reference UI implementation](reference-ui-20261125.md), not a
claim that every unrelated SriSu screen was retested. At this QA checkpoint both
existing project folders were on `codex/video-moments`; nothing had been committed,
pushed, merged or deployed. Frontend base `6c85951`, backend base `136f618`. Live integration
refs were checked again: frontend `eaf54ee`, backend `ec992f2`, both
`dev-core-architecture` (the older branches in AGENTS.md remain historical).

Publication uses `codex/notifications-ui` in those same project folders, targeting
`dev-core-architecture` in both repositories. PRs record the paired implementation
commits and CI results. Local verification below precedes that publication step.

## Findings fixed

Four new regression tests failed against the initial implementation and passed
after the fixes in `NotificationInboxViewModel.kt`:

1. A 401/403 during Open or Mark read could leave private rows visible. Both now
   clear rows, cached media, read boundaries and pending navigation, and show a
   retryable access error.
2. A stale row callback could still navigate after a filter/refresh removed the
   row. Open now requires that ID and event kind to remain in the current list.
3. An expired read boundary offered the same ineffective retry. The rejected
   token is cleared and the user is directed to refresh before marking again.
4. Mark read in Unread view could show “caught up” while the server reported new
   arrivals outside the read snapshot. It now fetches those unread arrivals.

Visual review also found avoidable narrowing at 200% text with a thumbnail and
long actor names. Large-text rows now put the thumbnail below the text and align
the avatar with the first line. Standard-size reference geometry is unchanged.
A layout regression asserts the thumbnail is below the message at 200% text.

## Logic and authorization checks

- Loading does not mark anything read; duplicate action taps are suppressed.
- Pagination deduplicates rows and rejects stale responses after filtering.
- Background/account changes and late open/image results cannot restore private
  content or cause navigation.
- Offline failures retain existing rows for retry; denied access clears them.
- Expired/deleted resources lose actor, thumbnail and rich-text metadata and
  cannot open an unavailable destination.
- Date grouping uses the device's timezone and calendar-week boundaries; future
  clock differences never produce negative relative times.
- Read-all uses the server snapshot, leaves later arrivals unread, and is
  idempotent. Cursors remain account/filter bound.
- Missing, corrupt and unsupported media return safe not-found responses.
  Media rechecks unrelated users, blocks, membership changes and revoked sessions.
- Invitation avatars belong to the actual sender, not the currently linked
  partner. Spark copy respects answer/reveal state.
- Existing navigation-owner tests verify feature detail → existing inbox and
  status → existing Home without restoring the inbox in a loop.

## Functional testing over real HTTP

Added opt-in `NotificationInboxTransportIntegrationTest`, run with:

```sh
python3 tools/core_integration.py --backend ../SriSu-backend \
  --test com.srisu.srisu.features.notifications.NotificationInboxTransportIntegrationTest
```

This passed against a disposable Django database/server using the actual Ktor
client and three synthetic accounts. It published private Moments, decoded the
new inbox fields, downloaded/decoded the bounded JPEG thumbnail, rejected a
stranger's media/open requests, checked that image reads do not mark activity
read, retried a read snapshot, verified later arrivals stay unread, opened the
exact Moment ID, deleted it, and checked both destination and preview revocation.
Temporary credentials and the server were removed by the runner.

One local sample measured a 23 ms inbox response and 15 ms thumbnail response
(910 bytes). These are loopback smoke measurements with one synthetic image,
not a remote-network, load, or device-performance benchmark.

## Final checks

| Check | Result |
| --- | --- |
| Full Android/shared unit and UI suite | 692 tests, 686 passed, 6 opt-in skips, no failures/errors |
| Real HTTP Notifications integration | 1 passed separately; this is one of the default opt-in skips above |
| iOS Notifications render/interaction tests | 8 passed on iPhone 17 Pro simulator, iOS 26.5 |
| Android native gesture/interaction tests | 2 passed on `SriSuHomeQA` emulator |
| Backend isolated SQLite suite | 474 tests, 439 passed, 35 PostgreSQL-only skips |
| Backend disposable PostgreSQL suite | 474 passed, no skips |
| Android app/test APK builds; Android/iOS compilation | Passed |
| Theme policy, contract snapshot, Django system checks, migration drift | Passed; no new migration |

Frontend command:

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:iosSimulatorArm64Test \
  --tests 'com.srisu.srisu.features.notifications.NotificationInboxIosTest' \
  :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest
```

Backend commands used the documented isolated runners:

```sh
.venv/bin/python tools/workspace.py test
.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin
.venv/bin/python tools/workspace.py check
.venv/bin/python tools/workspace.py migrations
```

## UI review

Reviewed light/dark, 390×844 standard and 320×640 small layouts, 640×320 landscape,
180–200% text, long names plus thumbnails, loading, empty and retryable error states.
Android UI testing also uses the real ViewModel to verify offline → retry → rows,
unread → read styling, Mark read disabling and filtering to the empty state.

Native Android tests swipe up/down through a 30-row list, then exercise the final
row, Settings, Back and returning to the top. They run on normal 1080×2340/440 dpi
and small 640×1280/320 dpi configurations, the latter with dark mode and 180% text.
Display overrides are restored afterward. These tests validate interaction and
reachability; no release frame-time or physical-device smoothness claim is made.

Local ignored screenshots:

- [iOS standard](../../../composeApp/build/reports/notifications/qa-ios-standard.png)
- [iOS small](../../../composeApp/build/reports/notifications/qa-ios-small.png)
- [iOS dark](../../../composeApp/build/reports/notifications/qa-ios-dark.png)
- [iOS landscape](../../../composeApp/build/reports/notifications/qa-ios-landscape.png)
- [iOS large text](../../../composeApp/build/reports/notifications/qa-ios-large-text.png)
- [iOS empty](../../../composeApp/build/reports/notifications/qa-ios-empty.png)
- [iOS error](../../../composeApp/build/reports/notifications/qa-ios-error.png)
- [iOS loading](../../../composeApp/build/reports/notifications/qa-ios-loading.png)
- [Android long text with thumbnail](../../../composeApp/build/reports/notifications/inbox-qa-long-text.png)
- [Android native standard](../../../composeApp/build/reports/notifications/qa-android-standard.png)
- [Android native small/dark/large text](../../../composeApp/build/reports/notifications/qa-android-small-dark.png)

## Remaining limits

No physical Android/iPhone run, full real-account app journey, remote-backend
deployment, VoiceOver/TalkBack session, or APNs/FCM delivery was performed. The
inbox contains no text input, so keyboard behavior is not applicable. Figma quota
still prevents verifying a live matching node; the supplied export remains the
appearance reference. Synthetic screenshot imagery is not production data.

The QA changes add no endpoint or migration. Backend additions in this follow-up
are tests only. The previously implemented media/formatting support still needs
the matching backend web release before the new UI can display those visuals.
Pre-existing Xcode project changes, local configuration and backend environment
were checked against their saved hashes and preserved; unrelated media were not
modified or staged.
