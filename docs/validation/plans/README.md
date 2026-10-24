# Plans verification — 2026-10-24

Implementation and QA evidence for both repositories. See
[flow, source commits, Figma reference and rollout](../../flows/plans.md).
Migration `social.0020_coupleplanmodel_cancelled_at_and_more` is applied locally.
The implementation and QA runs did not switch branches or deploy to production.
Publication on the existing feature branches was subsequently authorized by the user.

## Executed checks

| Check | Result |
| --- | --- |
| `python3 tools/workspace.py verify all` | Passed theme/frontend/backend baseline; final client reruns below cover subsequent native-editor fix |
| `python3 tools/theme/check_theme.py` | Passed: 96 documented colors; 24 checked role pairs ≥4.5:1, minimum 4.75:1 |
| Android `testDebugUnitTest`, `compileDebugKotlinAndroid` | 351 tests, zero failures, 1 existing opt-in transport skip; compile passed |
| iOS simulator compilation and signed Debug Xcode build | Passed; app installed on existing signed-in iPhone 17, iOS 26.5 |
| Native iOS Plans tests after compatibility fix | 25 passed: 13 state tests, 11 UI/render tests, 1 opt-in live two-partner test |
| Final date/time formatting correction | Full Android suite passed again; all 24 native Plans state/UI tests passed again (live test already passed with final dependency pin) |
| Backend isolated SQLite | 286 tests, zero failures, 22 PostgreSQL-only skips |
| Backend disposable PostgreSQL | All 286 passed, zero skips; includes four new Plans race/transaction tests |
| Backend migration consistency | No changes detected |
| API contract schema/route tests, mirror comparison, both `git diff --check` | Passed |

The Android opt-in skip is the pre-existing CoreLocalTransportIntegrationTest,
not the Plans native integration check. The latter actually executed and emitted
its `PLANS_LIVE` completion marker. Native build warnings already present in the
project remain; a successful build alone was not treated as visual verification.

## Live two-partner integration

`PlanLiveFlowTest` exercised production KMP repositories over Darwin HTTP against
local Django/PostgreSQL, using three synthetic accounts and two real native
Matrix clients against Synapse. Verified:

- Both partners see invitations and agreed agenda state; an unrelated user gets 404.
- Initial acceptance, decline, counterproposal and reversed response ownership.
- Proposed title/time/place keep original agreed details and reminders until acceptance.
- Rejecting or withdrawing a change preserves agreement; accepting reschedules it.
- Checklist assignment, partner check/uncheck, and removal through actual APIs.
- Partner cancellation appears in both histories and removes scheduled-reminder state.
- Existing-shaped plan retains its ID and earlier reply note; either partner can mark
  it done, undo and mark done again. No automatic completion.
- Protected draft survives ViewModel recreation; deliberately losing the response
  after a real server commit then recreating/retrying reuses the same request.
- Matrix sends `m.room.encrypted`; the second native client decrypts the matching
  plan reference. Repeating the stable transaction returns the same event.
- Reminder reconciliation uses real server snapshots and a recording platform driver;
  this verifies requested schedules, not iOS notification delivery timing.

The first setup run correctly rejected incomplete synthetic profiles. Those test
profiles were completed. A later rerun needed fresh normal authentication because
its temporary session fixture had expired/rotated; it then passed. Neither required
loosening production authorization or changing real accounts.

Synthetic plans were removed, three test accounts deactivated, Django sessions
revoked and their Matrix room revoked. Native Matrix devices logged out and temporary
stores were erased; credential fixtures were removed. The user's account was not
used to send invitations. The local server remains running.

To rerun: provision a synthetic complete-profile couple and unrelated account in the
local mock-OTP environment, plus an agreed synthetic past plan with an earlier reply
note. A private temporary JSON file supplies `base_url` (restricted to
`http://127.0.0.1:8000/`), `couple_id`, `room_id`, `past_plan_id` and `people`
(`id`, `tokens.access`, `tokens.refresh`). Supply its path through both
`SRISU_PLAN_LIVE` and `SIMCTL_CHILD_SRISU_PLAN_LIVE` to `iosSimulatorArm64Test
--device <QA simulator> --tests 'com.srisu.srisu.features.plans.*'`. Use fresh sessions;
never commit this fixture. Revoke accounts/devices and delete it afterward.

## Running iPhone and visual checks

Verified Home → Coming up → Plans, Upcoming/Invitations/Past empty states,
Plan something, idea selection, saved-draft reopening across relaunch, future date
selection and time-dialog confirmation. The installed build preserved sign-in.
An unsent “A little walk” draft remains from this check; no invitation was sent.

Production components were rendered with synthetic test-only data on Android
Robolectric native graphics and the iOS Compose/Skia runtime: agenda, invitation,
agreement/checklist/reminders, change proposal, completion, past, failure/retry,
empty, dark and 320×640 dp at 1.6× text. Stable-key scrolling and actions remain
reachable; Android interaction tests tap acceptance at larger text and preserve
checklist typing during a remote addition.

| Finding during review | Correction and verification |
| --- | --- |
| iPhone date picker failed at composition | Native regression reproduced IrLinkageError for removed kotlinx.datetime.Instant. Dependency insight showed Lognity overriding the declared compatibility build with 0.8.0. Pin 0.8.0-0.6.x-compat; both native editor controls, Android suite, live encrypted Matrix flow and running iPhone date selection passed. |
| Reschedule datetime JSON could use a space instead of ISO T | Explicit ISO serialization; backend assertion and real client proposal/acceptance passed. |
| Routine sync dismissed delivered reminders | Preserve unchanged native requests/delivered identities; Android platform regression and client reconciliation tests passed. |
| Partner checklist addition erased current typing | Clear only after this client's acknowledged add request; Android interaction regression passed. |
| New edit could overwrite an unfinished draft | Resume retained draft before another edit; state regression passed. |
| Quiet agenda refresh left stale partner actions | Reconcile loaded pages, keeping stable IDs and current viewport; state tests passed. |
| Upcoming date could show an unaccepted suggestion | Summary retains agreed date; detail clearly separates proposed fields. Reviewed native change capture. |
| Legacy notes and unknown zones needed preservation | Render earlier reply, keep blank legacy zone, additive migration preserves every prior field; schema and native live checks passed. |
| Date-only selection inherited seconds/fractions | Normalize to hour/minute and display HH:mm; editor controls rerun on both targets. |

Screenshots: [running agenda](iphone17-agenda.png),
[editor](iphone17-editor.png), [fixed date picker](iphone17-date-picker.png),
[agreed plan](ios-agreed.png), [change proposal](ios-change.png),
[dark agenda](ios-agenda-dark.png), [large text](ios-invited-small-large-text.png),
[failure](ios-failed.png). Matching Android captures are alongside them.

## Initial implementation limits (see the follow-up QA below)

- Figma connector quota blocked fresh design-context/screenshot exports. Used the
  live browser flow inspected during discussion and established tokens, with agenda,
  proposals and error states documented as extensions.
- Two authenticated native clients were tested, not two interactive phones. No
  physical Android device or accessibility screen-reader session was run.
- iPhone software-keyboard layout/typing was not verified: this Device Hub session
  focused fields but did not expose/forward the software keyboard. Compose input,
  scrolling and IME padding are implemented; keyboard-on-device QA remains.
- iOS notification permission/delivery, Android reboot/battery timing, and calendar
  save/cancel UI were not interactively exercised. Platform code compiles; Android
  scheduler/receiver cleanup and delivered-notification retention tests passed.
- Local reminders learn partner changes only when the app syncs. They are generic;
  tapping fetches latest state. Calendar copies do not automatically follow changes.


## iPhone 17 follow-up QA — 2026-10-24

Device: existing iPhone 17 simulator, iOS 26.5. Sign-in and local editor draft
survived replacement installs. The local Django/PostgreSQL/Synapse services were
used; no production service was changed. At QA time, frontend changes were
uncommitted on `dev-core-architecture` at base `93fff640`; backend changes were
uncommitted on `codex/couple-chat-rebuild` at base `04f5652f`. No dependencies
changed in this QA pass.
Figma reference remains Planner `185:781`; no fresh Figma export was required.

### Findings fixed and checked on the running iPhone

- **Checklist keyboard obstruction:** even at the list's scroll limit, Add item
  was partially behind the keyboard. Added the missing IME inset to the Plans
  scaffold. Retested typing, scrolling to Add item, saving, clearing only the
  acknowledged text, and checking the saved item.
- **Editor exit and keyboard navigation:** the sheet header scrolled away and
  there was no Done action. A pinned header, Next to place, and Done now keep
  editing reachable. The first correction exposed a sheet/keyboard gap showing
  the agenda underneath. Replaced the sheet with a full-height dialog using the
  same tokens and form; the final running capture has no exposed agenda strip.
  Verified title/place input, Next, Done, local draft reopening after reinstall,
  and numeric hour/minute entry changing the draft to 10:30.
- **Calendar outcome feedback:** Save and Cancel previously reported the same
  generic handoff text below the visible controls. Native completion now reports
  saved versus not saved, with a visible snackbar. Android still reports only
  that its external editor opened, because a save is not confirmed there.
  Cancellation feedback was visually verified; saved-copy persistence was
  verified in Apple's Calendar. Shared state tests cover both returned outcomes.
- **Unavailable-plan draft loss:** deleting the temporary server fixture while
  its detail was open exposed a bug: a 404 erased an unrelated new-plan draft.
  Individual missing-plan reads now clear only their own edit/pending operation,
  retain other drafts, and allow returning to the agenda. Account/permission loss
  still clears private state. The regression covers refresh, stale deep links,
  restart, retry, and permission denial. This final failure-path correction is
  covered by state tests, not a second interactive deletion.
- **Elapsed reminder text:** distinguish a past reminder time from a future
  reminder that has not been scheduled; never claim delivery from elapsed time.

### Actual OS checks

A clearly labelled agreed QA fixture was temporarily seeded in the local test
couple for OS controls. Its agreement was fixture data, not a claim that the
second user accepted through the UI. Real mutual transitions were tested separately
with two authenticated native clients and synthetic accounts.

- First reminder tap displayed iOS authorization; Allow produced Scheduled on
  this phone. A revised fixture time was synced before leaving the app.
- A real local notification appeared at 09:34 while SriSu was backgrounded. It
  contained generic text, not private plan details. Tapping its Notification
  Center entry opened the correct plan with the latest time.
- Disabled notifications in iOS Settings; the app reported disabled permission
  and did not claim scheduling when another reminder was selected. Re-enabled
  notifications afterward and verified scheduling worked again.
- Off removed scheduled state. Re-enabling then cancelling the QA plan removed
  its reminder controls and moved it to cancelled state. The scheduled cancellation
  test's due time was 09:54:34. Notification Center remained empty at 09:58,
  after the due time, with notification permission enabled.
- Calendar editor prefilled the actual title, place, and local start; one-hour
  default duration remained editable. Cancel returned without adding a copy.
  Save persisted exactly one QA event, verified in Calendar as 24 October,
  09:50–10:50, QA test venue. The user explicitly asked to leave that event for
  inspection. Later plan changes intentionally do not rewrite calendar copies.
- Stopped only the local web container for a connection-failure check. The app
  displayed Unable to connect / Try again, kept its saved draft and plan, and
  disabled stale mutations. Restarted the server; reopening Plans recovered
  current data. The user navigated away before the retry button was exercised
  in that particular UI check; lost-response retry was covered by the live test.

### Executed regression checks

- Plans Android tests initially: 30 passed (14 state, 14 UI/render, 2 platform tests),
  including pinned close at a small viewport, Next/Done focus behavior, and
  calendar completion messages. The final 30-test rerun passed, including
  the editor at 320×480 dp with 1.6× text.
- Native iOS: 26 passed (14 state, 11 UI/render, one real two-partner live test).
  The `PLANS_LIVE` completion marker confirmed execution, including invitations,
  acceptance/decline, counterproposal, rescheduling/withdrawal, cancellation,
  completion/undo, checklist assignment/check/remove, draft restart,
  lost-response idempotent retry, reminder reconciliation, encrypted Matrix card
  decryption by the recipient, and unrelated-user denial.
- After the final editor-only layout change, all 25 native state/UI tests passed
  again. Live business logic did not change after its passing run.
- After the final unavailable-plan correction, all 32 Android tests and all 27
  native iOS state/UI tests passed (two new failure-path regressions on each).
  The prior real two-partner test remains separate live integration evidence.
- Signed Debug Xcode builds and Android/shared iOS compilation passed; final app
  installed on iPhone 17. Theme check passed (minimum checked contrast 4.75:1).
- No backend source behavior changed during this follow-up; the earlier 286-test
  PostgreSQL result above is prior evidence, not a new execution in this pass.

An unnecessary attempt to renew unused synthetic sessions hit the local OTP rate
limit. The existing private sessions remained valid and the native live test
passed with them. No rate limit or authentication rule was bypassed. All three
new synthetic accounts were deactivated, their sessions and Matrix room revoked,
synthetic plans removed, and temporary credential files erased. The separate
local OS-test plan and its operations were also removed after cancellation QA;
the saved Calendar event was retained as requested. The final signed build was
installed without uninstalling; sign-in and a new unsent “A little walk” QA draft
survived. The iPhone is left on Plans, notifications enabled, local services running.

### Evidence

- [Final installed agenda](qa/final-agenda.png)
- [Checklist obstruction before](qa/checklist-keyboard-before.png) / [after](qa/checklist-keyboard-after.png)
- [Editor keyboard before](qa/keyboard-before.png) / [final editor](qa/editor-keyboard-after.png)
- [Background reminder](qa/reminder-background.png), [correct destination](qa/reminder-destination.png), [permission denied](qa/reminder-denied.png), [no notification after cancellation](qa/reminder-cancelled-no-notification.png)
- [Calendar editor](qa/calendar-editor.png), [saved event](qa/calendar-saved.png), [persisted details](qa/calendar-details.png), [cancel feedback](qa/calendar-cancelled.png)
- [Offline retained draft](qa/offline-retained-draft.png), [cancelled plan](qa/cancelled-plan.png)

### Unresolved framework finding / coverage limits

At 09:40:23, the first launch after a replacement install crashed while the
accessibility inspector queried a Compose element. Diagnostic report:
`~/Library/Logs/DiagnosticReports/SriSu-2026-10-24-094023.ips`.
Sanitized fault path: `AccessibilityElement.get-node → contentOffset → bounds →
CMPAccessibilityElement.accessibilityFrame → AXRuntime`. The fault was
EXC_BAD_ACCESS at address 0x8. Subsequent reopening and final replacement launch
succeeded, with no newer crash report during this QA pass.

Dependency insight confirms effective Compose UI 1.10.2 and Material3 1.8.2 in
this checkout. The stack resembles the upstream
[accessibility element disposal issue](https://github.com/JetBrains/compose-multiplatform/issues/5543),
but this is an inference, not proof that every instance has the same cause.
It is unresolved here; a framework fix requires coordinated dependency validation
across the app. Accessibility was not disabled to hide the failure. This record
is not a clean release sign-off.

Both-partner coverage uses real authenticated native clients, not two humans
operating two phones. Physical-device notification timing, VoiceOver navigation,
Android OS/reboot behavior and production push delivery remain outside this run.
Local reminders still learn partner changes only when the app syncs.
