# Couple-profile nickname editing and alerts — 25 November 2026

Implemented locally in both original checkouts on `codex/couple-profile-nickname-alerts`.
Frontend base `07b8ce9f38d951a0c4de91321a1dc5e8580bf611`; backend base
`561a894fa5a94f4c532bab914fb9e6a71f881841`. At the time of QA, there had been no
push, PR, merge or production deployment.
The requested nickname control is a derived addition to the approved couple profile;
no new Figma frame was supplied and no live Figma parity is claimed.

## Changes

- Our profile → Partner nickname, below names/date and before statistics. Hidden
  from visitor, public preview and answer screens; current Settings couple must match.
- Settings and profile share the same editor and Settings ViewModel save flow.
  Retry preserves the draft; duplicate saves are guarded; replacement relationships
  close the editor. The fixed title avoids a large heading for long partner names.
- Explicit disclosure before saving. Updated clients send `notify_partner:true`;
  old clients omit it and retain their previous private-only behavior.
- Transactional `nickname.changed` event, private inbox/preview with the chosen
  nickname, recipient preferences and authorized couple-profile navigation.
  Unchanged saves, clearing and rejected writes create no alert. Superseded values
  and ended/replaced relationships invalidate old alerts. No nickname snapshot is
  placed in generic push metadata or the event outbox.

## Checks actually run

| Check | Result |
| --- | --- |
| Shared Android and iOS compilation | Passed |
| Focused Android JVM regression suite | 49 tests, 0 failures/errors, 1 opt-in HTTP test skipped in that run |
| Real Kotlin → disposable Django HTTP | Passed separately: save/normalize, persistence, recipient inbox copy, authorized couple destination, stranger denial, stale revision, clear and disconnect |
| Native Android, standard window | 7 passed, including real keyboard from both Settings and profile |
| Native Android, 320 × 640 dp, system font scale 1.8 | 5 passed |
| Native Android, 640 × 320 dp landscape | 5 passed |
| Native iOS simulator | 18 passed across profile nickname, Settings and request-contract suites; 4 profile cases rerun successfully after the title refinement |
| Backend focused SQLite | 95 tests, 0 failures, 4 PostgreSQL-only skips |
| Disposable PostgreSQL | 33 passed, 0 skips, including concurrent save/event behavior |
| Migration consistency | No changes; no migration added |
| Theme, contract mirror, whitespace | Passed |
| Local-work preservation | 394 existing backend local file checksums unchanged; frontend was clean at task start |

Android used the dedicated `SriSuHomeQA` Android 15 emulator (`emulator-5560`),
not the connected physical phone. Display size, density and font settings were
restored after testing. iOS used the iPhone 17 Pro / iOS 26.5 simulator. Native UI
fixtures are synthetic; the separate loopback run uses real client repositories
and an isolated synthetic database. No real account or live push was used.

The new Robolectric dialog tests initially entered a measurement/idling loop,
also with cursor blinking disabled. The identical editor passed on native Android.
Those interaction tests now run under instrumentation; owner/visitor/preview/answer
visibility assertions remain in Robolectric. No production layout workaround was
added for a desktop test harness. An initial isolated rerun selected unsupported
JDK 26; final builds used the Android Studio JDK.

## Visual review

Reviewed the owner profile entry, real Android keyboard capture and iOS standard,
small/large-text and landscape captures. Text wraps, the dialog body scrolls, and
Save/Cancel remain reachable. Screenshots are test fixtures, not real user data:

- [Owner profile](android-owner-nickname-entry.png)
- [Android keyboard](android-keyboard-dark.png)
- [iOS standard editor](ios-standard-dark-editor.png)
- [iOS small editor with larger text](ios-small-large-dark-editor.png)
- [iOS landscape editor](ios-landscape-dark-editor.png)

## Coverage limits and rollout

Deploy the backend first: the previous server rejects the new `notify_partner`
request field. No new migration is required beyond the previously merged nickname
migration. The notification respects Requests/activity/push/privacy preferences.
Live FCM/APNs delivery was not exercised. The existing backend does not register
native iOS remote-push delivery for this event; iOS receives the shared in-app
inbox entry. iOS native Compose input was exercised, but UIKit keyboard interaction
was not separately verified. Already displayed OS alerts cannot be retracted.
