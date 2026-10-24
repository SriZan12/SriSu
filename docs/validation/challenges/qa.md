# Challenges QA follow-up — 2026-10-24

Scope: current uncommitted Challenges implementation, frontend `dev-core-architecture`
at `f0969a0`, backend `codex/couple-chat-rebuild` at `a542c87`. No branch switch,
commit, push or deployment. Existing unrelated work was preserved.

## Defects reproduced and fixed

| Defect | Correction | Evidence |
| --- | --- | --- |
| A failed initial load discarded the ID opened from chat; retry showed the catalogue | Keep the requested ID until its detail loads successfully, including history opens | New regression failed before the fix and passes after |
| A quiet refresh could reopen a detail after All activities was tapped | Cancel that pending read when browsing/selecting another activity | New delayed-response regression failed before the fix and passes after |
| Withdrawing an unsent invitation could initiate its chat card after withdrawal | Only pending invitations may start card publication; stop pending card work when activity leaves that state | Regression asserts zero publications after cancellation; completed UI has no invitation-send action |
| A recovered connection left the previous connection error visible | Clear the error after a successful complete refresh, retaining uncertain pending-action errors | Offline → quiet recovery regression passes |

Primary changes: `ChallengeViewModel.kt`, `ChallengeScreen.kt`, shared state tests
and Android UI tests. Backend production behavior did not change in this pass;
two added tests exercise partner blocking and membership identity replacement.
The replacement test first used an invalid fixture that violated the existing
unique position constraint; it was corrected to model replacement explicitly.
The constraint was preserved.

## Coverage

Fresh synthetic partners and a separate unrelated account authenticated through
the normal local mock-OTP API. The opt-in native integration test used real Django,
PostgreSQL and Synapse. Both partners accepted, paused/resumed independently,
confirmed/undid completion, completed together, and saw matching history. Reversed
invitation ownership, decline and withdrawal passed. A server-committed action
whose response was deliberately lost recovered through the same saved request
after ViewModel recreation. The recipient decrypted the actual encrypted chat
card; repeated publication reused the same Matrix transaction/event. Unrelated
access was denied.

Android UI checks cover acceptance at 320×640 dp/1.6× text, opening a lower list
item at its heading, and completed activity actions routing the correct couple
to the existing Moment composer and dispatching Continue in chat. No Moment was
published during these checks. Native renders include invitation, active, paused,
completed, failure/retry, history, dark mode and large text.

## Final results

| Check | Result |
| --- | --- |
| `python3 tools/workspace.py verify all` | Passed theme validation, contracts, frontend tests/compilations and isolated backend checks |
| Full Android/shared tests | 322 passed, one existing opt-in local-transport skip (323 total) |
| Android + iOS simulator Kotlin compilation | Passed |
| Native iOS Challenges tests | All 23 passed: 12 state, 10 render, one actual live two-partner test |
| Isolated PostgreSQL suite | All 271 passed, no skips, including the new blocking/replacement authorization tests |
| Isolated SQLite suite | 253 passed, 18 documented PostgreSQL-only skips (271 total) |
| Signed Debug Xcode simulator build | Passed; updated app installed on iPhone 17 |
| `git diff --check`, both repositories | Passed |

The QA accounts were deactivated, sessions revoked and temporary credential files
removed. Native Matrix test clients logged out and erased their temporary stores.
The extra test simulator was removed and the reused QA simulator shut down.
The user's iPhone 17 sign-in and local server remain available. No code was pushed.
The installed build was visibly checked through Home → Challenges → activity
detail → All activities → Our history → Home, without sending an invitation.

## Limits

Two interactive simulator sign-ins could not be completed: Device Hub exposed
clickable controls but did not forward typed, pasted or hardware-key input into
the QA simulator's country search. No app login bypass or injected session was
used. Two partners were therefore exercised through native clients; the existing
signed-in iPhone was used for visible navigation. No two-phone interactive result
is claimed. Physical Android devices and VoiceOver/TalkBack were not tested.

Figma remains subject to the previously documented extraction quota; this pass
keeps the existing design system and changes no visual concept. See the initial
[design references and screenshots](README.md). Fresh component captures:
[completed](qa/ios-completed.png), [large text](qa/ios-invited-small-large-text.png),
[retry](qa/ios-failed.png).
The installed iPhone 17 [catalogue capture](qa/iphone17-catalogue.png) confirms the
settled screen with its app bar and all four collection controls.
