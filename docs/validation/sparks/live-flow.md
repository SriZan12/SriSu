# Sparks live flow — 2026-10-23

Follow-up to the initial component checks. The signed-in iPhone 17 / iOS 26.5
application used the real local Django and Synapse services. The counterpart was
a second native iOS Trixnity/vodozemac client on the dedicated test simulator,
authenticated through the normal development mock-OTP endpoints. No room keys
were injected or copied between clients. This is live two-client encryption
coverage, **not a claim of two interactively tested phone UIs**.

Frontend remains on `dev-core-architecture`, base
`ee9742becd54bd1c30ad76e04e482085662d497b`; backend remains on
`codex/couple-chat-rebuild`, base `ace26f623db19858bce62392c9a905cb81d8ffb1`.
No branch change, commit, push, remote deployment, or backend source change was
needed in this follow-up. The existing local services were used.

## Live observations

| Scenario | Observed result |
| --- | --- |
| Existing submitted answer | iPhone restored its own answer and waiting state. Existing answer content was not changed. |
| Waiting partner | Actual authenticated GET returned zero answer envelopes before that partner submitted. No blurred/hidden partner plaintext was present. |
| Four decks | Deep, Memories, Playful and Future selected their actual questions. Previous/next/shuffle worked. |
| Draft navigation | Typed labelled QA content, left Sparks, reopened: exact draft and character count remained. |
| Draft process restart | Closed SriSu through the app switcher, relaunched and reopened: draft remained; sign-in remained valid. |
| Software keyboard | Answer field and submit action remained visible/reachable above the native keyboard. |
| Actual send failure | Temporarily stopped only the local Django web service. Submit showed the connection error, retained the draft/envelope and offered Retry submission. Server was restarted immediately afterward. |
| Failed-send process restart | Closed and relaunched SriSu again: pending submission and text remained. Retry succeeded and showed the waiting state. |
| First partner submits first | Second client received no answer envelope until it submitted. Both clients then decrypted the new pair successfully; iPhone displayed both labelled answers. |
| Other partner submits first | iPhone displayed the submission status and Submit & reveal, without the other answer. Both clients decrypted both answers after iPhone submitted. |
| Identical answer retry | Replayed the exact envelope after reveal. Revision stayed 3 and the database still had exactly two answer rows. |
| Partner client restart | Restarted the native SDK client with its persisted device/crypto store and a fresh normal sign-in. Both QA answers still decrypted. |
| Chat entry and return | Continue in chat returned to the existing conversation. The correct Spark opened from its encrypted card; chat's attachment-sheet Sparks entry worked. |
| Server storage | All three exercised sessions had two Megolm envelopes, without answer/text/body/key fields. All three question-card events were `m.room.encrypted`. A scoped Synapse audit found zero events containing the synthetic QA answer markers. |

No private answer text, tokens, OTPs, room keys or screenshots of existing chat
contents are stored in this report. Temporary credential/control files were
removed and the temporary Django sessions revoked after testing.

## Defects corrected

1. **Completed history was stale.** Immediately choosing All decks omitted the
   just-completed Spark because the ViewModel retained the older Home snapshot.
   Returning to the catalogue now refreshes that snapshot. A state regression
   test requires the newly finished question to appear.
2. **Key failure claimed the answers were open.** The heading now distinguishes
   both answers being submitted from both being successfully decrypted on this
   device. The missing-key explanation states the new-device limitation and
   retains an explicit retry. Android/iOS semantic assertions cover this state.
3. **Initial connection looked like a failed connection.** Loading now says
   Connecting to secure chat. Reconnection is offered for an actual interrupted
   connection, with its message and retained draft. Retry state is set before
   invoking the Matrix operation, so a synchronous ready transition cannot be
   overwritten. State tests cover loading, offline and recovery.
4. **The local install command disabled simulator entitlements.** A build made
   with `CODE_SIGNING_ALLOWED=NO` appeared signed out; the server accepted a fresh
   verification but local sign-in could not complete. Rebuilding the same sources
   with simulator signing enabled restored the pre-existing session automatically.
   Both original and QA Spark answers still decrypted. No Keychain reset, crypto
   reset, auth-source change or security bypass was used. Use the signed command
   below for native secure-storage testing.

## Remaining limitation

The counterpart device was registered after the user's original answer. It could
not recover that older answer's key, although the original iPhone could open both
answers. Fresh exchanges with both devices present worked in both orders, and
their keys survived a client restart. This is the existing chat device-key
recovery boundary: automatic recovery of pre-device history is **not fixed** by
changing a status message. The SDK's trust checks were not weakened and no keys
were moved to Django. Recovery requires an existing device/key recovery flow;
this task does not add a new backup or device-verification architecture.

Two completed QA Sparks and a clearly labelled QA partner answer on the original
Spark remain in the local test couple. Existing submitted text was preserved.
No irreversible UI deletion or relationship mutation was performed.

Native Android interaction, two complete interactive phone UIs, new-device key
backup/verification, and live destructive skip/unlink races are not covered here.
Existing isolated backend tests cover skip/submit concurrency and authorization.

## Reusable opt-in verification

`SparkLivePartnerTest` is confined to `iosTest`. It requires the explicit
`SRISU_SPARK_LIVE_FIXTURE` environment variable pointing to a private JSON file;
normal test runs do not start a live session. `SIMCTL_CHILD_` passes this variable
to the simulator process. Configuration contains a locally authorized account's
normal auth tokens, room/couple/Spark identifiers and loopback API origin. Never
check that configuration into source control or print it. The runner accepts
inspect, labelled-QA submit, identical retry, decrypt and stop commands in an
adjacent private control file; it writes counts/statuses, never decoded answers.
Use only a dedicated test simulator and the authorized local development server.
Revoke temporary Django sessions and remove private files after the run.

The native driver's initial run and restart run both completed successfully.
Per-command results establish the outcomes in the table; an inactive opt-in test
must not be counted as live integration coverage. Component screenshots remain
synthetic test renders, including the updated [iOS key failure](ios-keys.png).

After the fixes, the focused Android Sparks suite passed **20 tests** and the
iOS Sparks state/render/native-cipher suite passed **19 tests**, with no failures
or skips. The latter explicitly excludes the opt-in live driver. Shared Android
and iOS compilation passed. Theme validation passed (24 semantic contrast pairs
at least 4.5:1, minimum 4.75:1), and both repositories passed `git diff --check`.
The full backend suite was not repeated because this follow-up changes no server
code; its live authorization/reveal/storage checks are reported above.

The final signed Xcode build passed, was installed on iPhone 17, and retained the
existing account. The latest completed Spark appeared in history after All decks;
opening the original Spark still decrypted both answers. The app was left signed
in on Home, and the local server is running.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/srisu-chat-recovery-ios ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build
```

Do not substitute `CODE_SIGNING_ALLOWED=NO` for this runtime check: the app uses
Keychain-backed session/draft storage and needs simulated app entitlements.

### Subsequent recovery fix

The new-device limitation above was addressed on 2026-10-24 with user-key-encrypted answer backups. See [recovery validation](recovery.md). Answers must be backed up from a device that can still open them; missing historical keys cannot be recreated.
