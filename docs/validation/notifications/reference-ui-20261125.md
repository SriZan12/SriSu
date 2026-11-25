# Notifications reference UI — 25 November 2026

Follow-up [logic, functional and UI QA](reference-qa-20261125.md) adds real HTTP
and PostgreSQL coverage and records subsequent fixes. Results below describe the
initial implementation checkpoint.

## Sources and working copies

- Frontend: `SriZan12/SriSu`, `/Users/srijan/studioprojects/SriSu`, branch
  `codex/video-moments`, base `6c85951a6c065af33c3ea24eecabf89887cfbc67`.
- Backend: `SrizanKhadka/SriSu`, `/Users/srijan/studioprojects/SriSu-backend`, branch
  `codex/video-moments`, base `136f61808a8a8f3e9aa1060fc17cd223c8488088`.
- Both remote integration branches were fetched at task start:
  `dev-core-architecture`, frontend `eaf54eef7294385f33f1b353420b09adfe798acc`,
  backend `ec992f2b1a158fc3d49b0b487226ab1c100c09bc`.
- Appearance authority: user-supplied `/Users/srijan/Downloads/Section (Spark).png`.
  Figma file `LztysD1YvINX7RwZpnhhTt` top-level metadata was accessible, but further
  inspection hit the Figma MCP Starter-plan quota. No matching notification node
  or live design tokens were verified. The supplied export was used directly.
- At this implementation checkpoint these were uncommitted working-copy changes.
  Nothing had been pushed, merged or deployed. Publication subsequently uses
  `codex/notifications-ui` in both existing folders. The pre-existing Xcode project changes, local configuration, backend
  environment file, and unrelated media remain in place.

## Implementation

`NotificationInboxScreen.kt` now uses a centered header, Mark read action,
Today/This week/Earlier headings, flat rows, overlapping avatar badges, compact
timestamps, optional Moment thumbnails, and lavender unread rows with orange dots.
`theme/NotificationInboxStyle.kt` scopes the typography and unread tint to this
screen. Existing typed destinations, read actions, pagination, filtering, settings,
privacy protection, and refresh behavior are retained.

Reference measurements, scaled to a 390 dp phone, informed 20 dp horizontal gutters,
44 dp avatars and thumbnails, 24 dp activity badges, 12 dp horizontal gaps, and 16 dp
vertical row padding. Body text uses 14/20 sp, timestamps 12/18 sp, title 20/27 sp,
and section labels 12/18 sp. Light unread rows approximate the sampled `#F8F5FB`;
colors otherwise reuse SriSu's theme. This is a measured interpretation of the
export, not a claim of pixel equality to an inaccessible Figma node.

The backend adds optional `actor_name`, `avatar`, `thumbnail`, and plain-text
`segments` to inbox rows. Authenticated media routes return bounded JPEG previews
after checking the current recipient and resource access. No private image URL is
exposed. Images use `private, no-store` responses and a separate media rate limit.
The app limits concurrent image requests to three, bounds responses to 256 KiB,
keeps at most 32 images in memory, and discards them on leaving or changing sessions.
Unavailable rows clear their media and presentation metadata immediately. Existing
push text and original inbox title/body fields remain compatible with older clients.

## Responsive behavior and intentional differences

- Heights follow content; longer names and larger text wrap without fixed row heights.
  At narrow widths or larger text sizes, Mark read moves to a second header line.
- Content is capped at 600 dp in landscape/wider windows. Native system insets remain
  in charge of status and navigation bars. No mock phone frame is rendered.
- Dark mode derives from SriSu's existing theme; the reference only provides light mode.
- All/Unread, Refresh, and Settings remain available in a quiet footer. Pull to refresh
  is available above the list. The export does not specify these secondary controls.
- Date grouping uses the user's local date and a Monday-start calendar week. Existing
  30-day inbox retention is unchanged; historical April samples are not fabricated.
- Mutual-interest notifications, anniversary/milestone notifications, device-local
  reminder rows, and challenge proof thumbnails are not existing inbox capabilities.
  The UI renders supported events instead of inventing backend data or event types.
- Test screenshots use synthetic names/text, an existing bundled demonstration
  avatar, and a generated landscape thumbnail. Production images come from authorized
  current data, not these fixtures.
- No new continuous animation or text entry was added. Keyboard verification is not
  applicable to this list screen.

## Verification completed

| Check | Result |
| --- | --- |
| Android/shared unit and UI suite | 685 tests: 680 passed, 5 opt-in skips, zero failures/errors |
| iOS notification render/interaction suite | 8 passed on iPhone 17 Pro simulator, iOS 26.5 |
| Android native Compose interaction checks | 2 passed on `SriSuHomeQA` emulator |
| Backend isolated workspace suite | 471 tests: 436 passed, 35 PostgreSQL-only skips, zero failures |
| Android app/test APK assembly; Android and iOS Kotlin compilation | Passed |
| Theme policy, contract snapshot/generator checks, whitespace checks | Passed |
| Backend migration drift | No changes detected |

The final frontend invocation was:

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:iosSimulatorArm64Test \
  --tests 'com.srisu.srisu.features.notifications.NotificationInboxIosTest' \
  :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest
```

The backend command was `.venv/bin/python tools/workspace.py test`, using the
repository's isolated test configuration and allowlist, not unrestricted discovery.

iOS cases cover 390×844 and 320×640 logical sizes, 180% text, dark mode, 640×320
landscape, and loading/empty/error states. Android native checks cover the normal
1080×2340/440 dpi display and a 640×1280/320 dpi small-screen override with 180% text
and dark mode. They exercise Mark read, row callbacks, a 30-row list, settings, and
back navigation. Emulator display overrides were reset after the checks.

Tests also cover calendar boundaries, legacy response fallback, authentication and
media bounds, denied/expired resources, pending/revealed Spark wording, and image
loads that finish after leaving the screen. Two existing iOS live-flow test callers
were updated to reserve Matrix cards before sending, matching the current API;
those unrelated live suites were compiled but not executed.

## Local screenshots

Generated files are ignored build artifacts, not committed user data:

- [iOS standard](../../../composeApp/build/reports/notifications/ios-standard.png)
- [iOS small](../../../composeApp/build/reports/notifications/ios-small.png)
- [iOS large text](../../../composeApp/build/reports/notifications/ios-large-text.png)
- [iOS dark](../../../composeApp/build/reports/notifications/ios-dark.png)
- [iOS landscape](../../../composeApp/build/reports/notifications/ios-landscape.png)
- [iOS empty](../../../composeApp/build/reports/notifications/ios-empty.png)
- [iOS loading](../../../composeApp/build/reports/notifications/ios-loading.png)
- [iOS error](../../../composeApp/build/reports/notifications/ios-error.png)
- [Android native standard](../../../composeApp/build/reports/notifications/android-native-standard.png)
- [Android native small/dark/large text](../../../composeApp/build/reports/notifications/android-native-small-dark.png)

## Limits and deployment

Simulator/emulator and isolated backend checks do not establish real-account
end-to-end behavior. No physical iPhone, remote-backend rollout, APNs/FCM delivery,
or frame-time benchmark was performed for this task. Scrolling and navigation
callbacks were exercised natively; smoothness under arbitrary device/network load
is not claimed. PostgreSQL-only cases were skipped by the isolated SQLite run.

There is no database migration. When publishing later, deploy/restart the backend
web service before the new client to enable avatars, thumbnails, and rich copy.
Against an older backend, the client retains title/body text and fallback icons.
No worker, notification category, or push-delivery change is required by this UI.
