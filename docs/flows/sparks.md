# Sparks: private questions and mutual reveal

Implemented 2026-10-23, with recovery added 2026-10-24, from frontend `ee9742becd54bd1c30ad76e04e482085662d497b`
(`dev-core-architecture`) and backend `ace26f623db19858bce62392c9a905cb81d8ffb1`
(`codex/couple-chat-rebuild`). Work remains on these feature branches; see Git history for publication commits.
Backend contract: `sparks-1`; migration:
`social.0017_sparksession_sparkanswer_and_more` (applied locally).

## Experience

Home has a compact Sparks entry after Moments; it resumes the active Spark, or
shows the latest revealed Spark. Chat's attachment sheet opens the same feature.
The existing Home/Chat/Profile navigation stays unchanged.

Choose Deep, Memories, Playful or Future. Each contains six curated, versioned
questions. Previous/next/shuffle stay within a deck. Send creates one active Spark
for the couple and sends its encrypted question card into the existing Matrix
conversation. Failed card publication is explicit and retryable. Stable Matrix
transaction IDs and Spark-specific row keys keep retries as one logical card.

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
sync/retry. A short 180ms fade respects reduced-motion scale. Continue in chat
returns to the original chat when entered there; All decks opens the catalogue.
Returning to the decks refreshes recent history so a just-finished Spark appears
immediately. Secure-connection loading and interruption have distinct feedback.
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
while the destination is resumed (8s session, 15s Home) and is cancelled on pause.
UI composables perform no network requests or encryption themselves; state lives
in feature ViewModels and transport in the existing Ktor/Matrix repositories.

## Figma and intentional adaptations

Source: [Sparks section 184:1634](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-1634),
Home page `1:3`, design-system page `0:1`. The five Spark frames were visually
inspected in the browser during the preceding design discussion. The connector's
Starter-plan quota blocks current design-context extraction and artwork export.

The implementation retains the four decks, outlined question card, optional
Instrument Serif heading pairing, Satoshi controls/body text, and charcoal pill
CTA. Deck accents reuse Spark/Ask/Planner/Moments theme roles. Answer/reveal use a
compact question, instead of repeating the tall browsing card. Both partners can
answer first. Waiting uses a neutral lock/status rather than blurred real text.
The unavailable sleepy illustration is replaced with the existing icon family's
spark glyph; no claim of exact asset parity. Dark mode derives from current theme
tokens. Deck buttons use a 4-column layout, switching to two columns on small
screens/larger text, so the fourth deck never clips.

## Components and evidence

- `features/sparks/data`: typed contract, references, secure draft format, Ktor source.
- `features/sparks/presentation`: Home entry, state/lifecycle, browser/editor/reveal UI.
- `features/sparks/navigation`: typed IDs using existing AppNavigator.
- `features/chat/matrix/MatrixSparkEncryption.kt`: existing SDK encryption adapter.
- Matrix repository/mapping, attachment sheet and message bubble: encrypted Spark cards.
- Backend `social/services/sparks.py`, `social/api/sparks.py`, models/migration.

[Validation and screenshots](../validation/sparks/README.md) distinguish native
cipher/component tests from full-device/live-account coverage. The subsequent
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
