# Integrated app QA — 24 October 2026

## Scope and provenance

Frontend `SriZan12/SriSu`, branch `dev-core-architecture`, base
`b6b1576b9ed26b270e9843f3ea4309a4ee3a93cb`. Backend `SrizanKhadka/SriSu`,
branch `codex/couple-chat-rebuild`, base
`6e0935c1c4f24267b9a956d259db5d113e9bb903`. Both live remote heads were checked;
no branch switch, commit, push, dependency upgrade or backend source edit.
Existing work and the original phone's saved Plan draft were preserved.

Two interactive iPhone 17 simulators, iOS 26.5, used the local Django/Synapse
services and the existing paired test accounts. A separate synthetic couple and
outsider were used for native-client and authenticated HTTP checks. No production
fixtures or alternate authentication paths were added to the app.

Existing Figma references and feature contracts were reused (Home `1:3`, system
`0:1`, Sparks `184:1634`, Plans `185:781` in `LztysD1YvINX7RwZpnhhTt`). No visual
redesign or claim of fresh Figma extraction. The fixes address reproduced behavior.

## Findings and changes

1. **Home's pending Plan count opened the wrong tab.** With pending invitations
   and no upcoming plan, tapping the count showed an empty Upcoming page. It now
   carries a defaulted navigation flag and loads Invitations on the first fetch.
   Normal agenda/detail entry and later manual tab selection are unchanged.
   The plural copy now reads “invitations or changes.” Verified after installing
   the updated signed app on the original iPhone 17; its saved draft remained.
   [Before](plans-invitation-wrong-tab.png) · [After](plans-invitation-fixed.png).
2. **First-character caret jump in dialog editors.** Native keyboard typing of
   `test` produced `estt`, including direct coordinate taps (not just accessibility
   key activation). Observed in Plan title/place and private appreciation replies.
   The affected fields now retain a local `TextFieldValue` for selection/composition
   while forwarding text to the existing draft state. Model replacements still
   update the field. After installing the final build, native typing produced
   `test` in the appreciation reply and `test plan` / `test cafe` in the Plan
   editor, in the right order. Next/Done and saving the draft also worked.
   [Before](reply-caret-before.png) · [Reply after](reply-caret-fixed.png) ·
   [Plan after](plans-caret-fixed.png).
3. **Intermittent secure-chat verification timeout remains.** Opening chat after
   feature navigation twice required Retry/Reconnect during this session. The
   existing timeout state appeared within its deadline, kept sending disabled,
   and recovered immediately on retry without draft loss. This was also seen
   after installing the final build. The cause was not isolated; no speculative
   Matrix/security change was made. This is an unresolved reliability issue,
   not an accessibility crash.

## Device and live-service coverage

| Feature | Observed on interactive phones | Additional live/native coverage |
|---|---|---|
| Moments | Selected two photos in native Photos, uploaded, reordered, published privately, opened both photos on partner phone; heart and seen receipt persisted; encrypted Moment reply arrived with correct reference. Public synthetic Moment received a private appreciation and creator reply; other partner could read both and send a further reply after the caret fix. | Normal authenticated HTTP: both creators, two-photo publication/upload replay, conflicting replay, owner-only staging, private-media denial to outsider, view/reaction idempotence, caption revision conflict, public visitor appreciation, both partner replies and outsider write denial; deletion/replay checks. |
| Sparks | Four deck entries present; new Future Spark; draft survived leaving/reopening; first submit showed waiting, partner saw submission status without answer; second submit revealed both answers on both phones. A secure-session timeout recovered via Reconnect without losing the draft. | Two new native devices restored four encrypted answers after old stores were erased; wrong key and cross-account recovery denied; mutual reveal retained. |
| Challenges | Invite, mutual acceptance, independent pause/resume, completion, undo and confirmation by both partners; completed state on both; Share a Moment opened the existing composer. | History, lost-response retry after ViewModel recreation, encrypted card decryption and unrelated-user denial. |
| Plans | New invitation from partner; original draft preserved; acceptance synced; proposed date kept the original agreement until accepted; reminder selection scheduled and stayed selected after reschedule; assigned checklist completed on the other phone and synced; EventKit editor populated and save confirmed; cancellation visible on both and retained in Past. | Acceptance/decline/counterproposal/withdrawal/cancellation/completion/undo; checklist ownership; persisted intent retry; reminder reconciliation with a fake device scheduler; actual encrypted card decryption; outsider denial. |
| Chat and cross-feature navigation | New messages decrypted in both directions, quoted reply and reaction synced, read status updated; Spark/Plan cards and Moment reply opened the relevant feature. Composer remained available with keyboard. A synthetic photo sent through native Photos decrypted and opened on the other phone. Denying microphone access displayed actionable feedback without leaving chat. | Existing state, encryption, media, lifecycle and rendering regression suites. Fresh-phone old chat history without its keys displayed unavailable content honestly. |

Screenshots: [Challenge completed](challenge-completed.png),
[Spark reveal](spark-mutual-reveal.png), [two-photo Moment](moments-partner-two-photos.png),
[seen/reaction receipt](moments-seen-reaction.png),
[rescheduled reminder](plans-rescheduled-reminder.png),
[cancellation and calendar save](plans-cancelled-calendar-saved.png),
[received chat photo](chat-photo-received.png).

## Checks

- `python3 tools/workspace.py verify all`: passed. SQLite backend suite: 286 cases,
  22 PostgreSQL-specific skips (264 executed).
- Backend isolated PostgreSQL runner: all **286 passed**, no skips; temporary
  database/cluster removed by the runner. No unrestricted legacy discovery.
- Three opt-in native live tests explicitly executed with synthetic credentials:
  `PlanLiveFlowTest`, `ChallengeLiveFlowTest`, `SparkRecoveryLiveTest`: passed.
  This is separate from the default native suite's environment-guarded no-ops.
- After navigation fix: Android suite **355 passed / 1 skipped**;
  native suite **273 reported / 0 failed**, including four opt-in no-ops
  (**269 regular native tests executed**). Android and iOS shared compilation,
  theme checks and signed arm64 simulator build passed.
- Final editor-fix check: **357 Android tests passed / 1 skipped**; native suite
  **273 reported / 0 failed**, with the same four opt-in no-ops (**269 executed**).
  Android compilation, iOS shared compilation and signed Xcode simulator build
  passed. Both updated iPhone 17 apps were installed and the affected fields
  rechecked using native keyboard taps.
- Theme checker passed: 96 colors and 24 contrast pairs, minimum ratio 4.75.
  `git diff --check` passed.
- No new SriSu crash reports appeared during the pass; the two 09:08/09:40
  reports predate it. Repeated accessibility inspection/navigation was exercised,
  but this is not proof of every possible VoiceOver interaction.

## Limits and retained test data

Device Hub repeatedly lost coordinate routing with `noWindowsAvailable` and
occasionally timed out. Restarting Device Hub while retaining the running phones
restored touch; this was distinct from an app crash. The native gallery check was
subsequently completed. VoiceOver was used temporarily to restore accessibility
inspection, then returned to off; this does not establish exhaustive VoiceOver
or navigation-gesture coverage.

Current-pass manual voice recording/playback, video playback, background alarm
arrival and Android device interaction are not claimed. Voice recording reached
the iOS microphone permission prompt; the denied-permission path was verified.
Microphone permission was reset to its initial undecided state after the check;
no microphone access was granted or recording sent. Reminder selection and
native calendar save were checked here; actual reminder delivery had separate
prior evidence in the Plans QA record. Headless rendering and compilation are
not a replacement for these device behaviors.

The user's previously retained Calendar event was not touched. This run saved a
separate calendar copy of the synthetic Plan on the new partner simulator. The
interactive test Spark, completed Challenge, cancelled Plan, chat test messages
and two synthetic Moments are retained for inspection (Moments expire normally).
The Plan's imperfect test title was produced during the caret-bug reproduction;
it is the cancelled `est plant` record. An unrelated existing invitation was left
unchanged. The partner simulator retains the unsent `test plan` / `test cafe`
draft from the corrected keyboard check.

The three dedicated API-test accounts were deactivated, nine Django sessions
revoked, and their synthetic Matrix mapping revoked. Four API-test Plans and
one leftover API-test Moment were removed; private credential files were removed.
The interactive couple, its sessions and the original saved draft were untouched.
