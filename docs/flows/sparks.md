# Sparks: private questions and mutual reveal

Implemented 2026-10-23, with recovery added 2026-10-24, from frontend `ee9742becd54bd1c30ad76e04e482085662d497b`
(`dev-core-architecture`) and backend `ace26f623db19858bce62392c9a905cb81d8ffb1`
(`codex/couple-chat-rebuild`). Work remains on these feature branches; see Git history for publication commits.
Backend contract: `sparks-1`; migration:
`social.0017_sparksession_sparkanswer_and_more` (applied locally).

## Experience

The Questions tab uses Figma page `1:6` (Questions board `251:5421`). Its
current-question card uses the same server-allocated daily session as Home. An
unfinished question carries until both answer or someone explicitly skips it;
there is no queue or streak. A completed/skipped daily slot remains for its
shared server day. The server's stable couple timezone owns that day boundary.
Catalogue browsing does not open private answers or automatically replace the
active question.

The four current topics are Deep (7), Memories (6), Playful (6) and Future (6).
Counts come from returned questions. Topic/search previews open a separate
navigation destination; starting one resumes an existing open session first.
A durable pending create retries its original request even after restart. Search
matches catalogue question text/topic names locally and never searches answer
plaintext. Recently answered filters the bounded `home.recent` projection; it
is explicitly recent activity, not complete lifetime history or topic progress.

The root tab retains app navigation. Topic, search, history and answer screens
have their own Back destination and no bottom tabs. Existing chat cards still
open specific sessions. Continue in chat targets the existing card event when
available and keeps the question on the back stack; entering from Chat returns
to that same conversation. No answer text is placed in routes.

No partner, pending incoming/outgoing invitation, invitation-check failure,
secure-room preparation, loading, offline, and empty history/search are distinct.
Connect/View invitations uses the existing connection flow; no duplicate send
is performed by the Questions screen.

Either participant may answer first. Drafts use existing protected platform
storage, scoped to account/session, and are editable until submission. Text is
limited to 2,000 characters. The exact encrypted submission is persisted before
network transmission; uncertain retries reuse it. Definitive validation failure
unlocks the retained draft for correction. A recovered create acknowledgement
clears its pending request ID. No production fixture data is used.

Waiting displays submission status and the author's own answer when available.
It never blurs or downloads the partner's unrevealed answer. After both submit,
the server releases both ciphertexts and the client displays both decoded answers
together, labelled with real names/avatars and “You.” Missing keys show secure
sync/retry. Reveal uses conversation-style bubbles without mandatory animation. Back restores
the previous destination; resuming the catalogue refreshes recent activity. Secure-connection loading and interruption have distinct feedback.
The screen calls answers open only after successful local decryption; a missing-key
notice offers recovery with a user-held key and explains that answers never backed up still require an existing device.

Either person may skip an unrevealed Spark after an explicit in-app confirmation.
That removes both pending envelopes, keeps a skipped history entry and permits
another question. Submitted answers cannot be edited. Completed questions remain
reachable from their original chat card; the catalogue also shows recent history.
No fake read receipts, forced recipient-first order, streaks, AI questions, new
notification transport, public sharing or compatibility scores were added.

## Security and lifecycle

See [the backend security and contract record](../../../SriSu-backend/docs/sparks.md)
for the complete threat model and release gate. E2EE uses the existing Matrix SDK;
Django stores only ciphertext. **Release timing is still enforced by Django**, not
by a new cryptographic fair-exchange scheme. Keys and answers never appear in
routes or HTTP logs. Question/card metadata is not confidential to Django.

The Matrix repository validates the current relationship, room and membership
epoch before encrypting/decrypting. Only its current owner may close the shared
room. Account changes cancel outstanding work and clear UI data. Matrix access
revocation clears already displayed answers immediately. API 401/403/404 responses
clear private state, and late decryption cannot restore it. Polling occurs only
while the destination is resumed (8s answer session, 30s Questions catalogue; Home retains its own cadence) and is cancelled on pause.
UI composables perform no network requests or encryption themselves; state lives
in feature ViewModels and transport in the existing Ktor/Matrix repositories.

## Figma and intentional adaptations

Source: [Questions page 1:6](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-6),
clean board `251:5421`: tab `251:5437`, unpaired `251:6092`, history `251:6184`,
search `251:6250`, topic `251:6299`, answer `251:6396`, waiting/ready `251:6463`,
reveal `251:6530`, finished `251:6634`. The current design-context and screenshot
calls were attempted; Figma's plan quota still blocks exports. The implementation
uses the inspected frames and approved Stage 1 structure. Exact asset/token
parity remains unverified.

Questions uses scoped white surfaces, compact Satoshi text, lilac question
panels, warm rows, charcoal actions and pastel topic tiles. Existing Home couple
artwork is reused; existing Material icons identify the actual four decks.
No replacement bitmap artwork or invented Figma exports are shipped. The existing
Pure chat, illustrated Quick Share and Palette picker are unchanged.

Content height follows text. Gutters are 20 dp (16 below 360); actions have at
least 48 dp touch areas. Larger text puts secondary header actions in a labelled
options menu and topic tiles in one column. Decorative hero art disappears when
it would crowd the question. The editor uses the shared immediate TextFieldValue
adapter so delayed model echoes cannot overwrite newer IME edits or selection.
The scroll body and pinned primary action consume native IME/safe-area insets.
The iOS host disables automatic whole-scene focus panning so these screen-owned
insets do not also move the header under the status bar. Native Questions,
Chat and Plan keyboard checks, plus the remaining physical-device/gesture
limitations, are recorded in the
[Stage 2 verification](../validation/questions-stage-two/README.md).

Approved scope adaptations: one full-width current card; no streak, invented
progress percentages, multi-question queue, games, answer-specific reactions,
or new intimate-preference controls. Those Figma features require additional
product/contracts. The recent projection is bounded by the current backend.
The new daily-dependent client requires the already existing Home backend and
migration `social.0023`; no backend changes/migrations are introduced here.

## Components and evidence

- `features/sparks/data`: typed contract, references, secure draft format, Ktor source.
- `features/sparks/presentation`: Home entry, state/lifecycle, browser/editor/reveal UI.
- `features/sparks/navigation`: typed IDs using existing AppNavigator.
- `features/chat/matrix/MatrixSparkEncryption.kt`: existing SDK encryption adapter.
- Matrix repository/mapping, attachment sheet and message bubble: encrypted Spark cards.
- Backend `social/services/sparks.py`, `social/api/sparks.py`, models/migration.

[Validation and screenshots](../validation/sparks/README.md) distinguish native
cipher/component tests from full-device/live-account coverage. The subsequent
[Home Show more check](../validation/sparks/show-more/README.md) verifies direct
catalogue navigation on iPhone 17 and active-Spark/draft regression coverage.
The
[live flow check](../validation/sparks/live-flow.md) covers iPhone 17 plus a second
native SDK client, real Synapse key exchange, both answer orders, drafts/retries
and native keyboard layout. [Phone-change recovery](../validation/sparks/recovery.md) now verifies both partners on fresh native devices using encrypted answer backups. Answers never backed up remain dependent on a surviving device. Two interactive phone UIs are not claimed.

## Phone-change recovery (2026-10-24)

Open an existing Spark → **Protect and recover answers**. Setup generates a random
256-bit recovery key using the pinned Matrix SDK. The user saves it outside the
phone and re-enters it before backup is enabled. New phones enter that key to unlock.
Sign-in/OTP alone is deliberately insufficient. A wrong key never replaces an
existing backup or the locally stored working key. Interrupted setup resumes its
original key; there is no destructive reset flow.

This is a Spark-specific encrypted answer archive, **not a Matrix device-trust
reset or a Megolm-key import**. Only answers successfully authenticated by the
existing chat encryption adapter are archived. The SDK's standard secret-storage
AES-256-CTR/HMAC-SHA256 encrypts each copy on the device. Synapse stores only
ciphertext in the owner's private account data. Each payload binds owner, author,
room, Spark, couple, membership epoch, question and a canonical hash of the original
Megolm envelope. A restored copy must match a freshly authorized envelope from the
normal mutual-reveal API. Neither Django nor Synapse receives the recovery key.
Original Matrix sender checks and permissions remain unchanged.

The key stays in existing protected platform storage, scoped to account, API origin
and Matrix server. Account logout clears that storage. Temporary key input is never
saved in navigation/saved-state bundles and clears on background, close and account
change. Setup/recovery run under the current authorized, encrypted couple-room gate.

**Back up all answers** scans every history page, not just the latest 30 Sparks.
The additive `GET /api/social/sparks/history/?after=<uuid>` returns at most 25 IDs
and a next cursor, never answers. Each ID is fetched separately through the
existing audience/reveal gate. The active, foreground Matrix room also maintains
backups: full scan once per room opening, then changed recent/active Sparks every
30 seconds. Unavailable keys/network failures remain retryable and appear as
backup attention/partial completion. This does not claim background execution
while iOS suspends the app. Run the full backup before replacing a phone.

Each partner has their own recovery key/archive. Recovery never discloses a
partner's answer before mutual reveal. Skipped/unauthorized envelopes cannot be
restored through the app. Historical encrypted archive copies are not automatically
purged by Skip; they contain only answers that account had already been allowed to
read. Possession of a key cannot recreate an answer that was never archived.

Deployment: ship the additive backend history route before the client. No new DB
migration or dependency upgrade is needed for recovery (the original Sparks
migration 0017 still applies). Recovery is an intentional extension using existing
Satoshi/theme components; no recovery frame was supplied in Figma. The live Figma
connector remained blocked by its Starter-plan quota.


## Home integration — 9 November 2026

Home and the Questions tab now reuse the existing Spark flows. Daily assignment, carry/skip behavior, metadata-only partner activity and catalogue revisits are described in [Home couple space](home-couple-space.md). The daily projection never includes answer ciphertext.
