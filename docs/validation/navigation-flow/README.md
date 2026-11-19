# Navigation and journey audit — 18 November 2026

## Sources and preservation

Frontend `SriZan12/SriSu`, branch `codex/navigation-flow-repair`, based on the
fetched `dev-core-architecture` commit `6ed3468ff51e63509d67c241089c4e2ecd1ad39d`.
Backend `SrizanKhadka/SriSu`, audit worktree `codex/navigation-flow-audit`, based
on fetched `dev-core-architecture` `1594c3bf1391bef131c3b353154583fe938c4952`.
The documented historical integration refs (`dev-new-theme` and `dev`) differ
from these current working baselines. Publication as a PR into
`dev-core-architecture` was authorized on 18 November 2026; merging remains
outside this task.

During the isolated audit, the original checkouts were untouched. Existing Home layout, ChatPrivacy,
iOS project edits, and three Home design documents were carried into the new
frontend worktree before repair. Those changes are not navigation work.

This is a behavior audit using current source and contracts. It preserves the
approved Home, Questions, Pure Chat, Quick Share, and Palette appearances.
No new Figma comparison or visual redesign is claimed. Design file remains
`LztysD1YvINX7RwZpnhhTt` (Questions `1:6`, foundations `0:1`); Home uses
the four approved attached references and the existing implementation.

## Findings and repairs

| Journey | Finding | Repair / expected behavior |
| --- | --- | --- |
| Profile → Explore → preferences → Back | Explore lacked visible Back; preferences reused the tab root and changed shell chrome | Explore has Back; a distinct profile-detail route returns to Explore; Profile tab remains a tab |
| Partner requests → partner preview | Read-only preview had no visible return action | Profile header returns to its requesting flow; missing ephemeral preview still uses the unavailable fallback |
| Chat entry, slow/error response | Resolving/unavailable/error screen had no visible exit | Back remains available while resolving and after failures; retry stays local |
| Challenge → Chat → Back | Replacing the Challenge destroyed its return context | Push Chat and retain Challenge; Back restores it |
| Chat → Questions → nested page → Chat | Nested routes lost chat origin; Back only popped one child | Propagate origin and return to the existing conversation, clearing intermediate question entries |
| Plan/Challenge catalogue → detail → Back | Local detail changed without a navigation entry; Back exited the entire feature | Catalogue-origin detail returns to its list and reading position; loaded pages survive the return; direct links return to their originating screen |
| Question encryption recovery → system Back | System Back exited the question instead of recovery | Close recovery first; retain question context |
| Initial partner acceptance → membership refresh | First linked membership reset the navigation stack, potentially dismissing acceptance/chat | Preserve the first grant within the same account; immediately recheck membership; revoke/replace/member changes still clear all private stacks |
| Notification settings | Top Back action lacked safe-area protection | Consume safe drawing insets |
| Edit Profile load/save | Failed fetch fell through to editable cached/blank data; loading completion relied on city lookup; duplicate saves possible | Gate editing on a successful read, offer retry; one save in flight; explicit completion independent of city network |
| Profile save after token refresh | Cached editor credentials could overwrite newly rotated tokens and reset navigation | Atomically merge profile metadata into current credentials; reject late results from another account/session |
| Profile location editing | Country names are normalized for city lookup, so a case-sensitive stale-result check rejected valid results | Compare the selected country without case sensitivity; retain cancellation and discard results for a different country |
| Unsaved Profile → system Back | Leaving the tab root could discard edits even though detail Back was guarded | Confirm discard at both exits; Keep editing retains the form; normal tab switching continues to retain its ViewModel |
| Sign-out / incomplete setup | Existing logout behavior had no user-facing route; first setup step's Back did nothing | Confirmed Sign out from Profile; first setup step offers return to verification via sign-out |

## Journey checklist

Audit both forward and return paths, including loading/failure and session or
membership changes. Existing services remain authoritative; there are no new
endpoints, server fields, permissions, or migrations in this repair.

- Authentication: introduction/guest → phone → OTP → profile steps → Home;
  edit number, resend/expiry, retry, restart, wrong-account exit and sign-out.
- Partner linking: unpaired Home/Chat/Questions → search → invitation; incoming
  requests → preview → accept/decline; pending cancellation and first acceptance.
- Profile: tab edit, catalogue selection/cancel, save failure/retry, Explore
  preferences return, notifications and logout. Couple Profile owner/visitor,
  section editors, sharing and unavailable authorization remain separate flows.
- Main features: Home summaries, Questions search/topic/history/answer/recovery,
  full-screen Chat, media, contextual activity links, Moment composer/viewer,
  Plans agenda/detail/editor, Challenges catalogue/detail and return to chat.
- External routes: cold/warm deep links and notifications are account scoped;
  destination fetches reauthorize IDs. Invalid/unavailable targets offer Back.
- State: tab restoration, rapid taps, late callbacks, keyboard/back ordering,
  local drafts, safe areas, scroll reachability, session expiry, membership loss.

## Verification

All accounts, messages and writes used for device checks belong to the isolated
`srisu-home-qa` Docker project: local Django on port 8940 and Matrix on 8948.
No production backend, real partner account, SMS provider or push provider was used.
The Android test fixture refuses a non-QA account or a different backend URL.
Fixture credentials remain in ignored local storage and are not part of this report.

| Check | Actual result |
| --- | --- |
| `SRISU_BACKEND_DIR=../SriSu-backend-navigation-flow python3 tools/workspace.py verify all` | Passed during repair: paired contract snapshots, theme, Android unit/build checks, iOS compilation, backend checks and selected isolated backend suite. Final client changes were checked again separately below. |
| Backend isolated suite | 359 tests, 0 failures/errors, 30 skips. Locked dependencies were installed into a dedicated environment. This is not unrestricted legacy test discovery or full PostgreSQL concurrency coverage. |
| `python3 tools/core_integration.py --backend ../SriSu-backend-navigation-flow` | Passed real KMP–Django loopback HTTP/WebSocket integration; temporary server and credentials removed. |
| Final Android unit/component suite | 548 tests, 0 failures/errors, 1 opt-in skip. The skipped transport integration passed separately above. Includes navigation ownership, scope revocation, profile read/save/retry/token rotation/city lookup, state machines, responsive rendering, and list-position restoration. |
| Selected `iosSimulatorArm64Test` | 64 tests, 0 failures/errors/skips: navigation, authentication state, profile-session merge, Plan/Challenge state, empty Plans and 320-point/1.6x text rendering. Native Compose tests are not installed-app backend tests. |
| Native app builds | Android `assembleDebug` / `assembleDebugAndroidTest` and the final ad-hoc signed iOS simulator `xcodebuild` passed. The final installed Android profile test includes the tab-root Back guard; iOS manual checks used the preceding signed build, with the final guard compiled afterward. |
| Installed Android `NavigationJourneyDeviceTest` — Profile | Passed: tab-root editing → keyboard Back → discard confirmation → Keep editing → Discard → Home → unchanged saved Profile; Explore → preferences → Back; notifications settings return; real profile PUT success; sign-out cancellation and confirmed sign-out. |
| Installed Android — unpaired | Passed: Chat opens partner search; Questions' connect action reaches partner search and Back returns to Questions. |
| Installed Android — acceptance | Passed: incoming request → partner preview → Back → Accept → connected confirmation → Chat; Chat remains open through the actual 30-second membership refresh; Back reaches the newly paired Home. |
| Installed Android — Challenges | Passed: Home → catalogue → lower activity → detail → system Back restores the visible list item → header Back returns to Home. |
| Installed Android `HomeAppDeviceTest` | Passed: repeated Home swipes, Plan editor draft/keyboard/close/reopen, Chat draft typing/keyboard/Back/restoration against local Django and Matrix. |
| Installed Android `QuestionsAppDeviceTest` | Passed: question draft/keyboard/Back/reopen, no-result search and answer-history return. Its tab selector now distinguishes the Questions tab from the user's existing Home statistic. |
| Installed iOS manual checks | Passed the journeys described below on iPhone 17 simulator/iOS 26.5, using the signed app and isolated backend. |
| Theme audit | Passed: 96 documented colors retained, 24 M3 role pairs satisfy contrast checks; no newly introduced raw UI styling outside the theme policy. |
| Preservation | SHA-256 comparison confirmed all seven pre-existing edited files match the original baseline in both original checkout and new worktree. Backend tracked source is unchanged. |

Android uses the dedicated `SriSuNavigationQA` Pixel 4a/API 35 emulator. An
existing emulator's non-QA login was left intact; it was not cleared or replaced.
iOS uses the dedicated `SriSu Couple Chat Evaluation` iPhone 17/iOS 26.5 simulator.
Physical phones were not used. No frame-rate benchmark was performed.

### Installed iOS observations

- Profile → Explore → preferences detail: editable keyboard, Done, Back with
  discard confirmation, discard return, and reopening the unchanged saved name.
- Explore and notification-settings headers return correctly within safe areas.
- Chat → Question → encryption recovery → Back → Question → Back restores Chat
  and its local draft. Chat's composer stays above the software keyboard; header
  Back leaves the conversation and dismisses the keyboard.
- Home → couple profile → section editor → Back → profile → Home; the existing
  ask-in-chat action delivered a synthetic prompt to the isolated Matrix room.
- Home → Moment composer → Close → Home, without uploading media.
- Home → Plans → saved editor draft: keyboard Next reaches the optional place,
  Done dismisses the keyboard, and Save draft and close returns to Plans. Text
  Size was increased for the check and restored to its original value; keyboard
  capture was also restored.

Evidence: [iOS Explore return](ios-explore-return.png),
[iOS chat keyboard](ios-chat-keyboard.png),
[Plan at 320 points / 1.6x text](ios-plan-320-large-text.png),
[Challenge at 320 points / 1.6x text](ios-challenge-320-large-text.png).
The latter two are synthetic native component renders: content continues below
the viewport by scrolling. Android component tests verify action reachability.

### Remaining coverage limits

- The computer-control tool could click iOS accessibility controls, but swipe/drag
  operations failed with `noWindowsAvailable`. Manual iOS scrolling, interactive
  back-swipe cancellation and smoothness therefore remain unverified. Native
  automated rendering/state checks and observed keyboard behavior do not replace
  those gestures or physical-device profiling.
- Full fresh registration through real SMS delivery, real push delivery, verified
  HTTPS link association, physical camera/gallery/media playback and every OS
  permission variant were not exercised in this pass. Existing contract/state
  tests cover applicable error, expiry, retry, access and deep-link policies.
- Initial unsigned iOS simulator output could not complete credential persistence.
  An ad-hoc signed build ran the authenticated journeys successfully; the secure
  store was not weakened. This is not a claim of testing a full fresh sign-in on
  a physical, distribution-signed iPhone.
- Backend authorization tests passed in the selected isolated suite; its 30
  skips remain explicit. No new server API or migration was required. Server
  authorization, Matrix encryption and privacy boundaries remain authoritative.

## Handoff

At the user's request on 18 November 2026, frontend repairs were transferred to
the main `/Users/srijan/studioprojects/SriSu` checkout. It now has
`codex/navigation-flow-repair` checked out. The main
checkout's current Home edits and three untracked Home design documents were
preserved byte-for-byte; older carried Home/ChatPrivacy/iOS-project edits were
not copied over current versions. The old frontend worktree is detached and
retained as a recovery copy. Neither backend checkout was changed by this
transfer, and the backend audit worktree contains no tracked changes. The
navigation/profile repairs, regression tests and verification evidence are the
scope of the PR into `dev-core-architecture`; unrelated local Home changes are
excluded. No production deployment or migration is part of this change.

After verification, the dedicated Android emulator and the QA Docker services
were stopped, the Android device fixture files were removed, and the tested iOS
app was terminated. Database volumes and source worktrees were retained. Device
tests are opt-in and were invoked by individual scenario; partner acceptance
consumes its pending invitation, so repeating it requires a fresh isolated
invitation fixture. No synthetic credential file belongs in version control.
