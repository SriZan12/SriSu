# Challenges verification — 2026-10-24

See the subsequent [QA pass and fixes](qa.md) for the latest results. The record
below describes the initial implementation check.

Changes are local and uncommitted in both repositories. See the
[flow/design decisions](../../flows/challenges.md) for base commits, Figma references,
authorization, schema and deployment order. Local migrations 0018 and 0019 are applied.

## Executed checks

| Check | Actual result |
| --- | --- |
| `python3 tools/theme/check_theme.py` | Passed; all 96 documented colors retained; 24 role pairs meet 4.5:1, minimum 4.75:1 |
| `python3 tools/workspace.py verify all` | Passed frontend/theme/backend baseline before the final scroll correction; final client suite rerun below |
| `:composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` | Passed: 317 tests, 1 existing opt-in skip; Android and iOS target compilations passed |
| Backend `tools/workspace.py check`, `migrations`, `test` | Passed; 269 tests, 18 documented PostgreSQL-only skips; no missing migrations |
| Backend isolated `tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin` | All 269 passed, no skips; includes three Challenge race tests |
| Native `iosSimulatorArm64Test`, `com.srisu.srisu.features.challenges.*` | 19 passed: 8 shared state tests, 10 native renders, 1 opt-in live integration test |
| Signed Debug Xcode simulator build | Passed with simulated entitlements; installed on iPhone 17, iOS 26.5 |
| Contract mirror comparison, `git diff --check` in both repos | Passed |

The Android suite's existing opt-in `CoreLocalTransportIntegrationTest.pairedHttpDatabaseAndLogout`
is skipped without its independent fixture. This is separate from the Challenge
live native test, which actually ran and emitted its completion marker.

## Two-partner live integration

`ChallengeLiveFlowTest` used three fresh synthetic accounts through normal local
OTP authentication, real Django/PostgreSQL and Synapse, and two separate native
Matrix clients on the iOS simulator. It verified:

- Both partners see the same invitation and catalogue (four collections/16 activities).
- Inviting records only the creator's acceptance. Recipient acceptance activates it.
- A real server commit followed by a deliberately lost response retains the exact
  intent; recreating the ViewModel and retrying recovers it without a second action.
- Each partner pauses; one resume cannot remove the other's pause. Both resume.
- Own completion can be undone; one confirmation stays active, both complete.
- Completion removes the active card and appears in both partners' history.
- Reversed invitation ownership, recipient decline and creator withdrawal appear in history.
- An unrelated account receives 404 for the private activity.
- The real production chat-card adapter sends `m.room.encrypted`; a second native
  device decrypts the correct reference. Repeating the same Matrix transaction
  returns the same event. Card acknowledgement uses the real backend route.

This was two authenticated native clients, **not two interactive phone UIs**.
The fixture contained no real user content. Matrix test devices logged out and
their temporary stores were erased. The three QA accounts were deactivated,
Django sessions revoked, and local credential fixtures removed. No live QA activity remains.

To rerun, provision a fresh synthetic couple in a local mock-OTP environment,
authenticate its members normally, and write a private JSON file containing
`base_url`, `couple_id`, `room_id`, and `people` (`id`, `tokens.access`, `tokens.refresh`).
The test restricts the base URL to `http://127.0.0.1:8000/`. Supply that file path as
both `SRISU_CHALLENGE_LIVE` and `SIMCTL_CHILD_SRISU_CHALLENGE_LIVE` when invoking
`iosSimulatorArm64Test --device <QA simulator UUID> --tests 'com.srisu.srisu.features.challenges.*'`.
Keep credentials outside the repository and remove/revoke them after testing.

## Interactive iPhone 17 check

The signed app preserved the existing sign-in. Verified Home → Your challenge →
Browse, all four collection selectors, combined Works apart/15-minute filters,
activity detail with real partner identity, empty history, and return to Home.
Chat → attachment sheet → Try a Challenge together opened the same feature;
Back returned to the existing conversation, then Home. No test invitation was
sent from the user's account. The local server remains running.

## Visual review

Synthetic screen captures use the production components at 390×844 dp, light/dark,
and 320×640 dp with 1.6× text. Android uses Robolectric native graphics; iOS uses
the native Compose/Skia runtime. These fixtures are confined to tests.

| Finding | Correction and verification |
| --- | --- |
| Main action was buried below instructions | Smaller detail heading; shared progress then actions then instructions. Reviewed in native active/paused/completed captures. |
| Selecting a lower catalogue item could inherit its scroll offset | Reset position on detail/history navigation; retain position during progress updates. Focused Android UI regression covers opening the final activity. |
| Large text pushes controls below the initial viewport | Content wraps and scrolls; Android interaction test reaches and taps acceptance at 320×640, 1.6× text. No fixed-height text clipping. |
| Background refresh could discard a tap or reset busy state | User mutation cancels a pending read; job generation guards cleanup. Regression verifies one acceptance and busy state. |
| Retrying an old pause could override a later resume | Durable receipts and revision checks; API, PostgreSQL concurrency and native lost-response tests passed. |

Images: [running iPhone catalogue](iphone17-catalogue.png),
[active](ios-active.png), [paused](ios-paused.png), [completed](ios-completed.png),
[failure/retry](ios-failed.png), [history](ios-history.png),
[dark](ios-catalogue-dark.png), [large text](ios-invited-small-large-text.png).
Matching `android-*.png` captures are in this directory. Some standalone light-mode
captures omit a transient app-bar draw; the running iPhone catalogue and settled
detail were separately inspected with visible Back/title/Refresh controls.

## Limits

Figma's design-context/screenshot connector hit its Starter quota. Implementation
uses the previously inspected live browser references and established tokens;
the new detailed flow is a documented design extension. Exact inaccessible asset
fidelity is not claimed. No physical Android device, two-phone interactive run,
VoiceOver/TalkBack session or production deployment was performed. Optional
Share a Moment reuses the existing composer; this task did not republish media
or repeat Moments' previously documented picker/upload tests.
