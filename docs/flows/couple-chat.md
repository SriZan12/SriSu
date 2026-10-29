# Couple Chat — Matrix cutover record

Record updated: 2026-10-28. This is the frontend-owned implementation and release
record for Couple Chat. The KMP client is now Matrix-only and the custom Django
message/WebSocket and ChatV2 transports have been removed. A local Compose
control-plane interoperability run verified Django authorization, JWT exchange,
Synapse room policy and membership for two synthetic couples. This does not claim
a two-client encrypted message exchange, production deployment, independent
security review, app-level database encryption, or the complete Figma experience.

The accepted direction is defined by
[ADR 003](../architecture/decisions/003-matrix-couple-chat.md). The rejected
libsignal design remains in ADR 002 as historical decision context only. Backend
code and tested contracts remain authoritative for Django authorization and
provisioning. Matrix specifications and the selected SDK are authoritative for
message transport semantics; this document does not redefine either protocol.

## Pure conversation appearance (2026-10-28)

The visible conversation now defaults to the approved **Pure** preview: white light
background, neutral incoming bubbles, charcoal outgoing bubbles, centered identity,
grouped timestamps, compact Spark question, voice note and composer. Explicitly saved
appearance choices remain selectable; the Palette grid follow-up below replaces the
original appearance sheet layout.
Pure is scoped through `SriSuChatTheme`; the rest of the app keeps its existing theme.

Messages still come from `SecureChatViewModel` and the Matrix repository. Sending,
replies, reactions, upload/recording progress, playback/seek/retry, pagination, receipt
handling and encryption use the existing callbacks. Standalone feature cards and voice
notes break text groups. Timestamps sit outside the final bubble of each group;
pending/failed messages always keep their status. The displayed time is the message
creation time, never an invented receipt time. Tapping the voice duration retains
the existing playback-speed action (also exposed to accessibility).

Spark uses the encrypted event's question and existing authorized destination. The
card says **Open Spark**, since chat does not own the live submitted/revealed state.
No extra Spark request, endpoint, response field, or feature was introduced.

[Pure implementation, source and verification](../validation/chat-pure/README.md)
includes same-size comparisons, intentional deviations and the native keyboard gap.

## Presence, typing and shared palette structure (2026-10-28)

All six chat presets (Pure, Original, Blend, Calm, Iris, Dawn) now share Pure's
header, typography, grouping, bubble corners, external 12-hour timestamps/read
labels, compact Spark/voice layout and composer. Existing preset backgrounds,
accents, incoming colors and outgoing gradients are retained in light and dark.
The appearance picker shows a miniature of this shared structure.

The header shows the authorized partner's Matrix presence: **Online**, **Offline**,
**Away**, or **Status unavailable** when absent, stale or disconnected. It no longer
shows the encryption subtitle. Presence does not come from the local connection
state, and encryption and room authorization are unchanged. Sync advertises online
while the existing foreground conversation session runs; this is Matrix activity,
not an app-wide Home-screen heartbeat. Offline/away timing follows the homeserver
and other active devices. We do not force an account offline when one screen closes.

Incoming `m.typing` events are held only in memory, scoped to the authorized Matrix
room and partner. The initial sync snapshot is retained; disconnect/background
clears it so reconnect cannot revive a stale bubble. The three-dot bubble respects
reduced motion, announces its meaning to accessibility and has no message/receipt
identity. It follows the latest messages without pulling a reader out of history.

Actual composer edits publish Matrix typing without draft content: first edit,
then at most every 3 seconds while editing, a 10-second server lease, and stop after
5 seconds idle. Empty draft, send, blur, recording, overlays, leaving, account/room
changes, loss of authorization and background stop the publisher. Requests are
serialized, bounded to 2 seconds, best effort and independent of send errors.
Screen-owner/session guards reject superseded owners; the server lease handles
failed stops. Older clients without this publisher cannot emit typing indicators.

Backend `deploy/synapse/render_config.py` enables presence (previously disabled).
No Django route, DTO, persistence migration or message protocol changes. Apply that
config on each homeserver; client code remains compatible with disabled presence
and older clients. Visibility stays governed by Matrix membership/authorization.
See [activity validation](../validation/chat-pure/activity/README.md) for platform
coverage, isolated live Synapse checks and deployment state.

## Illustrated Quick Share menu (2026-10-28)

The composer plus button opens the approved Quick Share sheet: Photos, Camera,
Video and Voice first, followed by Spark, Challenge and Plan under Together.
The exact approved illustrated artwork is bundled locally, retains its own colors,
and is not tinted by the theme. The sheet uses the selected chat palette and the
Pure typography/spacing; other menus and feature destinations are unchanged.
Ask and Poll remain preview concepts and are not exposed in the app.

Every option dismisses the sheet before calling the existing media controller or
navigation callback. Photos/video/camera still use their native picker/capture
paths; Voice starts the existing locked draft recorder. Permission errors,
cancellation, media limits, photo metadata removal, encryption and upload handling
remain owned by those existing paths. The old static limits paragraph is omitted
from this compact menu; validation and contextual errors remain active.

The sheet opens fully expanded, has an explicit close control and retains native
backdrop/back/swipe dismissal. Its heading stays visible while its content scrolls.
At 130% text and above, media actions reflow to two columns; labels and activity
descriptions wrap. System safe-area insets replace the preview's fake home bar.
See [source, platform checks and screenshots](../validation/chat-pure/quick-share/README.md).

## Palette grid theme picker (2026-10-28)

The chat appearance control opens the approved Palette grid: a single live sample
conversation above all six presets, labeled Pure, Original, Blend, Calm, Iris and
Dawn. Selected swatches have an outline/check and native radio-button semantics.
The picker stays neutral Pure light/dark; each preview uses the existing preset
colors resolved from the app's base palette. Preset IDs, gradients and saved
account/room-scoped preferences are unchanged.

Tapping a swatch changes only the sample. Apply calls the existing `chatTheme(id)`
path and closes; the close button, back, backdrop and swipe dismissal cancel without
applying. The unchanged selection keeps Apply disabled. The sample text and Read
label are demonstration content, never messages or receipts inserted into the room.
The draft and reply remain untouched by this menu.

The normal layout shows three columns and two rows. At 130% text the grid uses two
columns; at 250% it uses one. Content scrolls when necessary while heading, Close,
Apply and the local-only/device-appearance note remain available. Native safe-area
insets replace the preview's synthetic system bars. [Reference and platform
validation](../validation/chat-pure/theme-picker/README.md).

## Decision and ownership

Verification follow-up, 2026-10-23: the authenticated Moments flow exposed a
secure-chat loading stall. Nested bootstrap/room-verification timeouts now become
retryable errors, including foreground revalidation, instead of escaping as
ordinary cancellation and retaining Loading. Parent cancellation and current
room/account ownership remain enforced; sending still requires secure validation.
A regression failed before the change and passed after it, including retry.
See the [Moments validation record](../validation/moments/full-flow/README.md) for
runtime evidence and limits; no underlying network cause is assumed from this test.

SriSu will run a private, self-hosted Synapse homeserver and use Matrix as the
only transport for new Couple Chat messages. The KMP client will use Trixnity and
its vodozemac crypto driver. There is no runtime fallback from Matrix to the old
Django message/WebSocket APIs and no second SriSu-designed encryption envelope.

The currently selected inputs are:

- Synapse `v1.162.0`;
- Trixnity `5.9.2`, including `trixnity-client`,
  `trixnity-client-cryptodriver-vodozemac`,
  `trixnity-client-repository-room`, and `trixnity-client-media-okio`; and
- Trixnity's pinned vodozemac package `v0.1.3`.

These versions are pinned implementation dependencies, but are not by themselves
evidence that the feature is safe to ship. Trixnity 5.9.2 requires Kotlin 2.3.21,
KSP 2.3.9 and Room 2.8.4 toolchain now selected by the Matrix integration work,
which is a material upgrade from the earlier application baseline. Trixnity does
not provide the former `iosX64` target, so this release supports `iosArm64` and
Apple-silicon `iosSimulatorArm64` and intentionally removes Intel-simulator
support. Live-device and live-Synapse evidence remain release gates.

Ownership is deliberately split:

| Boundary | Authority |
| --- | --- |
| SriSu account, device session, invitations, active relationship, couple membership, profile, Plans and Moments | Django/PostgreSQL |
| Stable public conversation identity | Django `ChatRoom.id` UUID |
| Django account ↔ Matrix user and `ChatRoom` ↔ Matrix room provisioning state | Django Matrix mapping records and retry worker |
| Message events, ordering, synchronization, receipts, encrypted attachments and room membership transport | Matrix/Synapse |
| Encryption/decryption, Matrix outbox, local timeline and cryptographic store | Trixnity/vodozemac on the device |
| Intended appearance and interaction | Figma, subject to implemented domain/security contracts |

`ChatRoom` is retained as a relationship-scoped control-plane identity. It is not
retained as the message store. Relationship responses and
`srisu://chats/<ChatRoom UUID>` continue to use that stable UUID; the authenticated
bootstrap call maps it to a Matrix room ID. Matrix IDs must not become navigation
arguments or Django authorization evidence.

## Figma evidence and limits

Design source: Figma file `LztysD1YvINX7RwZpnhhTt`, Message page
[`1:5`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5).
The inspected canvas includes the base conversation, voice, Spark, Ask/poll,
Planner, and chat-options areas.

Known references are preserved:

- empty conversation
  [`184:142`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-142);
- first message
  [`184:218`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-218);
- established thread
  [`184:274`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-274);
- hold to react
  [`184:28`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-28);
- photo viewer
  [`184:33`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-33);
- plus picker
  [`184:38`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-38);
- Spark section
  [`184:2`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-2); and
- conversation options
  [`185:1626`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=185-1626).

Ask and Planner were visibly inspected on page `1:5`, but separate child IDs were
not available before the connected Starter-plan inspection limit was reached.
The captures establish visual intent, not backend fields, Matrix event types,
permissions, encryption behavior, or proof that every prototype state exists.

## Feature scope and truthful status

The 2026-10-17 presentation refinement (header identity, grouping, timestamps, status
icons, composer and state views) is recorded in
[couple-chat-ui-revisit.md](couple-chat-ui-revisit.md).

The 2026-10-19 recovery restores the October 12 presentation on
`dev-core-architecture` while retaining Matrix. See the recovery section at the
top of that review for Git provenance, implementation boundaries and current
verification. The earlier text-only scope below is extended by this recovery;
none of the retired Django message transports or custom crypto is reinstated.

The first releasable slice is intentionally narrow: private encrypted text,
timeline synchronization, durable retry/catch-up for sends already accepted into
Trixnity's outbox, truthful offline/error state, current-relationship
authorization, and logout/account-switch isolation. Sending is disabled
without a current secure room observation and `SyncState.RUNNING`; this slice does
not promise creation of new messages while offline.

| Figma/product area | Matrix-era ownership | Status for the first release |
| --- | --- | --- |
| Empty conversation and text thread | Trixnity timeline backed by the mapped encrypted Matrix room | Implemented and compile/unit verified; live two-device Synapse verification remains |
| Send/retry/offline recovery | One Matrix transaction ID and Trixnity's gated durable outbox; never a parallel Django message operation | Implemented for retry/catch-up of already queued sends; sending is disabled while offline (the draft stays editable), and live restart verification remains |
| Delivery/read state | Real Matrix read receipts | Pure-style external time and Read label from a known partner receipt. Independent delivered/played receipts are not fabricated. Two-device runtime validation remains |
| Reply, reaction and deletion | Matrix reply relations, encrypted message annotations, own-message redactions | Restored with current-room gating and outbox retry. Edit remains absent. Encrypted annotations have a readable text fallback on the prior client; see recovery record |
| Photo/video and viewer | Trixnity/Matrix encrypted files and thumbnails | Restored compact bubbles, captions, real upload progress, picker/camera and typed full-screen viewer; one attachment, 16 MiB/120 s limits; live exchange remains unverified |
| Voice recorder/playback | Recovered native recorder/player and encrypted Matrix audio | Restored inline single-owner playback with actual progress; the recorded waveform travels inside the encrypted `m.audio` content as MSC3245/MSC1767 (see below); no played receipt; native runtime recording/playback still needs device validation |
| Spark | `sparks-1` ciphertext gate plus an encrypted Matrix question/reference card | Implemented locally; see [Sparks](sparks.md) |
| Ask/free response and polls | Separate versioned SriSu domain plus encrypted display content | Unimplemented and disabled |
| Planner/Plan cards | `CouplePlanModel` remains domain authority; Matrix may carry an encrypted typed reference | Basic Plan data exists outside chat; rich type/location/reminder/checklist/card flow is unimplemented |
| Story prompt cards | Couple Profile remains domain authority; a future Matrix card needs an encrypted typed reference | Unimplemented on Matrix; the retired legacy message side effect is not a fallback |
| Conversation appearance/nickname | Account/room-scoped secure local preferences | Six palettes, Pure default and shared structure. Header presence uses Matrix. Nickname/privacy options remain absent; no legacy `ChatRoom.settings` calls |
| Push notifications and voice/video calls | Separate future systems | Out of scope |

Unknown Matrix event or content versions must render as inert/update-required
items rather than crash or grant an action. A Figma control is not enabled until
its domain authority, encrypted representation, failure behavior, accessibility,
and Android/iOS implementation are complete.

### Voice note event content

Voice notes are encrypted `m.room.message` events with `msgtype: m.audio`, the
usual `body`/`filename`/`file` (`EncryptedFile`)/`info` (`duration` ms,
`mimetype`, `size`), and the voice extensions used by Element:

- `org.matrix.msc1767.audio`: `{ "duration": <ms>, "waveform": [<int 0–1024>, …] }`;
  at most 100 samples, peak-downsampled from the recorder's 0–100 levels.
  `waveform` is omitted when the recorder produced none.
- `org.matrix.msc3245.voice`: `{}`.

Trixnity's typed `m.audio` drops unknown keys, so `MatrixVoiceContent.kt` swaps
the client's `m.room.message` serializer: audio content carrying either block
stays a `RoomMessageEventContent.Unknown("m.audio")` with its raw JSON, and an
extra media-upload mapping replaces the cached encrypted file URL on send. On
receive, waveforms that are not arrays of numbers or exceed 1024 samples are
ignored; values are clamped and scaled back to 0–100. Older SriSu builds and
other clients without these blocks still decode plain encrypted `m.audio` and
show placeholder bars.

The timeline starts with 100 events and exposes Load earlier in 100-event steps,
up to a bounded window of 2,000. Stable transaction keys survive local
send acknowledgement. Messages from another device do not force scrolling while
reading older messages. Live pagination under concurrent sync/media loading
remains a runtime verification gap; the window cap is not a deletion policy.

Message entrances (2026-10-20) use a 160 ms opacity-only transition: one reveal
when a loaded timeline appears, then Compose lazy-item fades for newly inserted
visible messages, including local sends. The existing stable transaction keys
prevent replay on acknowledgement, receipts, reactions or thumbnail updates.
Placement, size and exit animations are disabled, so they do not move reading
anchors or animate media geometry. Alpha is read in the graphics layer; the
initial reveal uses ModulateAlpha to avoid a full-list offscreen buffer. System
motion-duration settings are respected, with an immediate visible state when
motion is disabled. This is a user-requested motion refinement to the previously
verified Figma thread `184:274`; a fresh Figma read hit the Starter-plan tool limit.

Verification for this motion change: 26 focused Android tests/renders and 13 iOS
simulator tests/renders passed, along with Android/iOS Kotlin compilation, theme
audit and `git diff --check`. The paused-frame tests check the partial fade and
completion, immediate reduced motion, stable send acknowledgement, unchanged
pagination anchor, and no per-frame content recomposition/measurement during the
initial reveal. Android midpoint and settled screen captures were visually
reviewed. This is not physical-device frame-time profiling; no new animation
library, blur, spring, staggered sequence or continuous effect was introduced.

## Bootstrap and client lifecycle

The retained frontend route is
`ChatNav.CoupleConversation(roomId: String?, messageId: String?, origin: String)`,
where a non-null `roomId` is the Django `ChatRoom` UUID. A null room ID resolves
the authenticated user's current active relationship before opening the room.
Opening a resolved route performs this sequence:

```text
typed navigation with Django ChatRoom UUID
  -> authenticated Django Matrix-session bootstrap
  -> Django rechecks current active membership and device session
  -> response supplies homeserver, Matrix room/user IDs and short-lived login data
  -> Trixnity opens the account/relationship-scoped protected store
  -> JWT login seeds a volatile Matrix authentication repository
  -> Matrix sync starts while the durable sender remains gated off
  -> JOIN + Megolm + exact current-couple membership are validated
  -> a continuous security observer is installed, then the outbox gate opens
  -> the mapped encrypted room becomes the timeline source
```

The bootstrap endpoint is currently
`POST /api/chat/v2/rooms/{chat_room_uuid}/matrix/session/`. The `v2` path segment
does not make the retired ChatV2 message API part of the Matrix design. If the
route is renamed later, introduce the replacement additively and keep one pinned
contract during client rollout.

Because relationship acceptance commits before asynchronous Matrix room
provisioning finishes, that endpoint can briefly return HTTP `503` with
`retryable=true`. The KMP repository keeps the room in its loading state and
automatically makes at most four total bootstrap attempts, waiting 1, 2 and 4
seconds between them. The delays are cancellable, and every attempt remains bound
to the same open-room operation and authenticated account; a room switch, account
switch, logout or cancellation makes the old retry sequence inert. The client
does not automatically retry `401`, `403`, `404`, validation failures, other
statuses, or a non-retryable `503`. Exhaustion renders the normal retryable error
state and retains the explicit **Retry** action, which begins a new bounded cycle.

The response is private, `no-store`, and short-lived. The Django-issued JWT stays
in memory while needed for login and must not enter navigation, logs, analytics,
crash reports, preferences, screenshots or fixtures. Trixnity's Room repository
is overridden with a pre-seeded in-memory `AuthenticationRepository`, so the
resulting Matrix access token is not written to Room or ordinary preferences.
After process death the client must obtain a fresh Django bootstrap before it can
open the local Matrix account and read history.

Every API origin, homeserver, account, relationship, membership epoch and Matrix
room combination has a separately scoped Matrix client/store. Logout, account switch
must stop sync, close the previous client, invalidate late results, clear credentials
and apply the approved local-history wipe policy. A same-account Django credential
rotation restarts the in-flight bootstrap without destroying Matrix crypto/outbox.
An unlink immediately makes Django bootstrap fail, schedules Matrix membership
revocation, closes the old conversation, and prevents queued work from being
presented as delivered. A later relationship gets a new Django `ChatRoom`, Matrix
room and cryptographic context.

Each conversation ViewModel holds an opaque repository owner token. A newer
back-stack entry claiming the same room replaces the prior owner, so disposal of
an outgoing navigation entry cannot close the room underneath the current screen.
The repository also waits for Trixnity's client job to finish before deleting or
reusing protected storage; a timed-out graceful close triggers immediate local
close plus a bounded job join, and an unconfirmed stop keeps chat cleanup-blocked.
An authoritative partner-status snapshot that is no longer `ACCEPTED` clears the
cached accepted identity, room and couple IDs so a revoked relationship cannot
leave a stale Open conversation action.

## Local persistence

Trixnity Room is the only chat projection/outbox/crypto store. The custom
`ChatDatabase`, `ChatV2Repository`, legacy message client and socket runtime have
been removed; they are not a layer beneath Trixnity.

The old database contains `chat_v2_conversation`, `chat_v2_message`,
`chat_v2_checkpoint`, `chat_v2_outbox`, `chat_v2_tombstone`, `chat_v2_reaction`,
`chat_v2_receipt`, and `chat_v2_attachment_transfer`. It lives at:

- Android: `<noBackupFilesDir>/srisu-private-chat-v2.db`;
- iOS: `<Application Support>/SriSuChatV2/srisu-private-chat-v2.db`.

During the repository's suspend startup reconciliation, an idempotent versioned
cleanup checks and removes the exact database plus `-wal`, `-shm`, and `-journal`
sidecars even when an independently restored preference marker already exists.
The eagerly created platform-store constructors perform no fallible filesystem or
key cleanup; failures therefore block only Matrix behind its retry state instead
of crashing application startup. Cleanup never removes the catalogue database, a
Matrix store or a broad application-support directory. Room/KSP/SQLite remain
project dependencies because `CatalogueDatabase` and Trixnity use them.

The still older custom encrypted-chat runtime used these separate exact targets:

- Android: `<noBackupFilesDir>/couple-chat-v1/<accountId>/` and AndroidKeyStore
  aliases matching `com.srisu.couplechat.v1.<positive-account-id>`;
- iOS: `<Application Support>/couple-chat-v1/<accountId>/`, generic-password
  Keychain items with service `com.srisu.couplechat.v1.protocol-store`, and the
  exact temporary directories `SriSuChatExports` and `SriSuChatVideo`.

Startup account reconciliation removes those keys first and then those exact
directories on its IO dispatcher. A completion preference is audit-only and
never suppresses rechecking, so restored or partially deleted legacy data is
retried. Failure blocks Matrix use through the existing cleanup error state
instead of opening either store. No legacy key, record, message or attachment is
imported into Matrix; neighboring directories and every `SriSuMatrix` path are
preserved.

The Matrix account database contains encryption state, a durable outbox and
private timeline data. Android uses app-private/no-backup storage and platform
file-based protection; iOS uses Data Protection and backup exclusion. The stable
device ID is an exact file inside the same relationship-scoped protected root,
not SharedPreferences/NSUserDefaults. Trixnity is configured with
`storeTimelineEventContentUnencrypted = false`, so decrypted bodies of encrypted
timeline events are cache-only rather than written into Room. This is still OS
protection only: Room may contain plaintext durable-outbox bodies, ciphertext,
crypto keys and metadata. No SQLCipher or equivalent app-level encryption is
claimed; a reviewed encrypted-at-rest design remains a production release gate.

Account removal is journaled before asynchronous cleanup in the same protected
Matrix root. Startup reconciliation strictly enumerates only the allowlisted
`scope-<hash>/account-<id>` layout: a signed-out launch removes all discovered
accounts, while a signed-in launch removes foreign accounts plus any tombstoned
current account. A failed enumeration or deletion keeps the journal and blocks
Matrix use until a later reconciliation succeeds.

Trixnity normally starts `OutboxMessageEventHandler` during client construction,
independently of `/sync`. SriSu overrides that exact named handler with a wrapper
whose child scope exists only while the fresh Django bootstrap, encrypted-room
gate, `SyncState.RUNNING`, and foreground/open-room observer are all valid.
Background, close, retry, any non-running sync state, account/relationship
change, token recovery, observer failure/completion, or any security-gate loss
cancels and joins the sender before the transition completes. This prevents a
restored durable outbox from draining before authorization and a current sync are
re-established.

## Authorized destructive legacy-data cutover

The product owner explicitly authorized destruction of legacy chat data on
2026-10-08. No old plaintext history, custom ChatV2 ciphertext, attachment, outbox,
receipt, reaction, deletion or change record is migrated into Matrix.

The destructive scope is limited to obsolete chat transport data:

- legacy/custom-v2 message, media, reaction, deletion, operation, change, outbox,
  receipt, visibility and staged-attachment rows;
- the two legacy message/media join tables;
- objects under `chats/media/`, `messages/media/`, `chat_encrypted/`, and the
  historical `chats_media/` prefix; and
- the custom mobile ChatV2 database and the exact older `couple-chat-v1`
  directories, key namespaces and iOS temporary export directories described
  above.

The cutover preserves users, device sessions, invitations, couple connections,
couples, membership history, profiles, Plans, Moments, non-chat media, Django
`ChatRoom` UUIDs, Matrix mappings, migration history, and Matrix/Synapse data.
The backend checkout's unrelated `.DS_Store` is explicitly preserved. Deletion
must use positive table/path allowlists; never broad `media/**`, database
`CASCADE`, repository cleanup, or app-support-directory deletion.

Schema cleanup uses new forward purge/drop migrations after the cutoff. It does
not edit or squash the applied chat migration chain: the retained Matrix mapping
migration historically depends on the earlier ChatV2 migration even though the
Matrix runtime no longer imports that transport.

Before deletion, take an encrypted, access-controlled, time-limited rollback
snapshot: exact commits/images, database dump and migration list, protected-table
counts, legacy chat table counts, storage key/size/digest manifest, Matrix mapping
export, and Synapse database/media/config state. Restore it in isolation before
the point of no return. Private content must not be copied into tickets, prompts,
fixtures or logs. Expire the rollback archive after the approved rollback window.

## Exact retirement and retain inventory

This is a post-cutoff allowlist, not permission to delete early. Re-run a reference
scan against the exact release commits because the Matrix integration is active
work. If an item still has a retained caller, split/refactor it before removal.

### Django data and schema

| Disposition | Model / table | Required handling |
| --- | --- | --- |
| Retain | `ChatRoom` / `chat_chatroom` | Keep `id` and relationship ownership as the public identity and Matrix mapping target. Remove transport-only `last_message_id`, `unread_count`, `is_typing`, `settings`, `last_sequence` and `encrypted_v2_started_at` only through a forward migration after callers are gone |
| Retain | `MatrixUserMapping` / `chat_matrixusermapping` | Keep deterministic account mapping and provision state |
| Retain | `MatrixRoomMapping` / `chat_matrixroommapping` | Keep the one-to-one `ChatRoom` mapping, membership epoch and provision/revoke retry state |
| Purge/drop | `MediaModel` / `chat_mediamodel` | Delete only allowlisted legacy chat blobs and rows; never infer ownership by deleting a broad media directory |
| Purge/drop | `MessageModel` / `chat_messagemodel` | Includes plaintext, custom-v2 envelope and `profile_action` rows; first remove the `ChatRoom.last_message` reference and replace Couple Profile/Plan card producers |
| Purge/drop | `MessageDeletion` / `chat_messagedeletion`; `MessageReaction` / `chat_messagereaction` | Delete explicitly before the message table rather than relying on broad cascade |
| Purge/drop | `ChatOperation` / `chat_chatoperation`; `ChatChange` / `chat_chatchange`; `ChatOutbox` / `chat_chatoutbox`; `ChatReceipt` / `chat_chatreceipt` | Obsolete Django message idempotency, change stream, hint outbox and receipt cursors |
| Purge/drop | `MessageVisibility` / `chat_messagevisibility`; `EncryptedAttachment` / `chat_encryptedattachment` | Obsolete per-user tombstones and custom staged-ciphertext transport |
| Purge/drop | `chat_chatroom_pinned_messages` and `chat_messagemodel_medias` | Explicitly remove the two message/media join tables after detaching their parents |

Preserve migrations `chat/0001`–`0005` as applied history. Add later forward
migrations that detach `ChatRoom` transport fields and remove obsolete models.
Never rewrite `0004_chat_v2_foundation` merely because retained
`0005_matrix_provisioning` depends on it.

### Django routes, processes and files

Retain the Matrix route
`POST /api/chat/v2/rooms/<uuid>/matrix/session/` and its implementation in
`chat/api/matrix.py`, `chat/selectors/access.py` and
`chat/services/matrix_provisioning.py`. Also retain
`social/services/relationship_service.py`, `reconcile_matrix_rooms`,
`matrix_status`, the Compose `matrix_reconciler`, Matrix settings/environment,
`chat/test_matrix.py` and `deploy/synapse/`.

After the supported-client cutoff, remove these message endpoints:

- `GET /api/chat/rooms/` and
  `GET /api/chat/rooms/<uuid>/messages/` once relationship/bootstrap state is
  the room-entry source;
- `POST /api/chat/media-upload/` and the guarded legacy media delivery routes;
- `GET /api/chat/v2/capabilities/`;
- `GET|POST /api/chat/v2/rooms/<uuid>/messages/`,
  `GET /changes/`, `POST /operations/`, `PUT /receipts/`, and
  `POST /attachments/` plus `GET|DELETE /attachments/<uuid>/`; and
- `/ws/chat/` only after relationship-change/Find Partner consumers no longer
  depend on it.

Stop and remove the Compose `chat_outbox` and `chat_maintenance` processes and
their `dispatch_chat_outbox`, `run_chat_media_cleanup` and
`cleanup_chat_media` commands. Retire `backfill_chat_v2`, `chat_v2_status` and
the legacy chat seed/delete commands. Keep `matrix_reconciler` and its status/
reconcile commands.

Prune the legacy implementation under `chat/api/`, `chat/consumers/`,
`chat/websocket/`, `chat/presenters/`, `chat/selectors/`, `chat/services/` and
`chat/utils/` only after preserving the Matrix files listed above. In particular,
remove the message/history/media/v2 serializers and handlers, consumer/routing
stack, message/chat-room presenters and selectors, and message, reaction, receipt,
typing, custom-v2 and outbox services. Refactor `chat/models.py`, `chat/urls.py`
and `chat/admin.py` rather than deleting them. Refactor or remove
`social/services/couple_profile_chat.py` before `MessageModel` disappears.

Remove the `CHAT_V2_*`, `CHAT_LEGACY_MEDIA_TTL_SECONDS` and
`CHAT_MEDIA_CLEANUP_BATCH_SIZE` configuration surface. Retain all `MATRIX_*`
configuration. Remove Channels/Daphne/channel-layer configuration or dependencies
only when the final non-message WebSocket caller is gone; Redis remains in use by
non-chat features.

### KMP code and local data

`ChatNav.CoupleConversation(roomId: String?)` is now wired only to the Matrix
repository. Home and Couple Profile use the null-room form; accepted invitations
and validated external links can supply the stable Django room UUID directly.
This cutover removed the legacy Django message client:

- `composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/data/remote/api/ChatApiService.kt`
  and `ChatRepository.kt`;
- message/room/mutation/receipt/typing DTOs and response contracts under
  `composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/data/remote/dto/`
  and `composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/data/remote/response/`; and
- `composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/data/remote/websocket/`
  (`ChatSocketRequests.kt`, `ChatWebSocket.kt`, `ChatWebSocketEvent.kt` and
  `ConnectionState.kt`) after the Find Partner/relationship signal is replaced.

It also retired the entire custom transport under
`composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/v2/`, including
`E2eeEngine.kt`,
`ChatV2Repository.kt`, `ChatDatabase.kt`, `ChatV2ApiService.kt`,
`ChatV2Contracts.kt`, its domain/rollout/presentation classes, schema exports and
ChatV2-specific tests. Also retire the platform actuals at
`composeApp/src/androidMain/kotlin/com/srisu/srisu/features/chat/v2/crypto/PlatformE2eeEngine.android.kt`
and
`composeApp/src/iosMain/kotlin/com/srisu/srisu/features/chat/v2/crypto/PlatformE2eeEngine.ios.kt`.
Legacy DI bindings, socket utilities and contract fixtures were removed after
their last callers. The retained boundary is
`composeApp/src/commonMain/kotlin/com/srisu/srisu/core/session/SessionCoordinator.kt`
and its logout wipe hook.

The `dev-core-architecture` reconciliation also removed its later custom runtime
under `features/couplechat/`, the `secureChatNative` module, the iOS
`SecureChat/` bridge and drivers, `contracts/couple-chat-1/`, and their native,
integration and evaluation tests. None of these paths is a Matrix fallback.

The explicitly removed obsolete schema/test set is:

- `composeApp/schemas/com.srisu.srisu.features.chat.v2.data.local.ChatDatabase/`;
- `composeApp/src/commonTest/kotlin/com/srisu/srisu/features/chat/ChatV2ApiServiceTest.kt`
  and `ChatV2FoundationTest.kt`; and
- `composeApp/src/androidUnitTest/kotlin/com/srisu/srisu/features/chat/ChatV2DatabaseTest.kt`,
  `ChatV2RepositoryTest.kt` and `PlatformE2eeEngineTest.kt`.

Legacy chat cases were removed or reduced to unrelated HTTP/session/catalogue
coverage in
`composeApp/src/commonTest/kotlin/com/srisu/srisu/core/ChatIsolationTest.kt`,
`composeApp/src/commonTest/kotlin/com/srisu/srisu/core/SocketFoundationTest.kt`
and
`composeApp/src/androidUnitTest/kotlin/com/srisu/srisu/core/CoreLocalTransportIntegrationTest.kt`
without deleting unrelated HTTP, session or catalogue coverage.

Retain the Matrix implementation under
`composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/matrix/` and its
`androidMain`/`iosMain` platform directories, the typed UUID route in
`composeApp/src/commonMain/kotlin/com/srisu/srisu/navigation/graph/ChatNav.kt`,
reusable Figma-aligned UI components, generic authenticated Ktor support and
account/session lifecycle. Room/KSP/SQLite and Ktor core/auth/OkHttp/Darwin remain
required by the catalogue and/or Trixnity. Delete the exact custom database and
sidecars listed above only through the exact platform cleanup described in Local
persistence; the Matrix store uses a separate validated root.

### Media allowlist

The obsolete object prefixes are `chats/media/`, `messages/media/`,
`chat_encrypted/` and historical `chats_media/`. Inventory database references
and object keys first, quarantine unexpected keys, then delete the manifest—not
a wildcard parent. Preserve `couples/moments/`, `couples/profile_private/`,
`couples/profile_photos/` and every other non-chat prefix. Tracked development
media follows the same allowlist. Preserve `.DS_Store`.

## Deployment and cutover order

Backend and Matrix infrastructure must precede the dependent mobile release:

1. Land additive Django Matrix identity/room mappings, current-membership access,
   short-lived bootstrap, retryable provision/revoke work, and synthetic tests.
2. Deploy private Synapse, its separate PostgreSQL/media storage, TLS/proxy policy,
   disabled public registration/federation, rotated secrets, backups and redacted
   observability.
3. Apply the additive backend migration and reconcile every eligible relationship
   to one active or intentionally revoked Matrix mapping. Do not delete old data.
4. Resolve the KMP toolchain/target incompatibilities and land Trixnity with a
   distinct protected per-account store. Prove Android↔iOS encrypted text against
   a disposable Synapse instance.
5. Release Matrix-only mobile entry with no plaintext fallback. Keep unsupported
   rich actions hidden. Observe setup failures, sync health and revocation without
   recording content.
6. After the minimum supported client/cutoff policy is enforced, disable legacy
   writes, drain or explicitly account for in-flight legacy work, then stop the
   legacy outbox/media-maintenance workers and retire `/ws/chat/` and legacy HTTP.
7. Take and restore-test the rollback snapshot. Block all legacy media prefixes,
   run the allowlisted server/client purge, and verify protected data is unchanged.
8. Remove legacy frontend/backend code, settings, dependencies, contract fixtures
   and tracked chat media. A Git history rewrite, if required for private tracked
   files, is a separate coordinated operation after the rollback bundle exists.

Matching branch names, successful compilation, merged PRs and successful pushes
are not deployment evidence. Record exact frontend/backend commits, migration,
Synapse image/config version and rollout time in the deployment handoff.

### Rollback boundary

Before destructive step 7, rollback can disable Matrix entry, revoke/freeze the
new Matrix rooms, and restore the previous client/backend route without moving
content between transports. After step 7, rollback requires the matching legacy
database, media, application images and supported client from the tested snapshot.
Running both transports for the same relationship after rollback creates split
brain and is forbidden.

There is never a silent secure-to-plaintext fallback. If Matrix is unavailable,
the UI shows setup/retry/unavailable state and preserves only behavior that the
Matrix client can truthfully reconcile.

## Another-laptop prerequisites

Before continuing this work on another laptop:

1. Clone/open the distinct repositories and read each `AGENTS.md`:
   frontend `SriZan12/SriSu` (`dev-new-theme`) and backend
   `SrizanKhadka/SriSu` (`dev`). Do not infer one repository from the shared name.
2. In the frontend, run `python3 tools/workspace.py status --remote`; also inspect
   the backend status through the workspace tool. Confirm remote, branch, commit,
   dirty state and the correct feature work before changing anything. Never reset
   or overwrite another contributor's worktree.
3. Install the documented Android Studio JBR/Android SDK and full Xcode required
   by the supported iOS targets. Confirm CocoaPods/native tool requirements from
   the selected Trixnity release.
4. Preserve the recorded Kotlin/KSP/Room/Trixnity alignment. This release
   intentionally supports `iosArm64` and `iosSimulatorArm64`; `iosX64` was removed
   because Trixnity 5.9.2 does not publish that target.
5. Set up Docker, PostgreSQL and Redis per the backend workspace guide, then use
   its reviewed `docker-compose.yaml` and `deploy/synapse/` configuration. Start
   the web, `matrix_reconciler`, `synapse_db` and `synapse` roles as documented.
   Copy only the synthetic `.env.example` shape and generate new values for the
   Matrix JWT, database, macaroon and form secrets. Do not copy another machine's
   `.env`, signing keys, database, access tokens or private media. Set the enabled
   Synapse service class, server name, device-reachable public homeserver URL,
   internal Compose URL, protocol ID, issuer/audience and bounded token lifetimes
   consistently on Django and Synapse.
6. Use synthetic accounts and a disposable Synapse/database for integration.
   Verify TLS for every non-loopback URL and keep registration/federation closed.
7. Run the documented frontend theme/unit/Android/iOS checks and selected backend
   Matrix/auth/social checks. `python3 tools/workspace.py verify all` is the final
   cross-repository baseline, not a substitute for the live two-client test.

If a required artifact, backend branch, Synapse image or secret is unavailable,
report the limitation. A link or branch name does not grant access.

## Release gates

No production E2EE or cutover claim is permitted until all applicable gates have
recorded evidence:

- **Build:** selected Trixnity/vodozemac artifacts resolve reproducibly; Android,
  `iosArm64` and `iosSimulatorArm64` pass; a real supported Android and iPhone
  launch is tested. Intel simulator support is not part of this release.
- **Transport:** Android→iOS and iOS→Android encrypted text, restart, pagination,
  offline catch-up, exact outbox retry, duplicate prevention, tamper/decryption
  failure, lost-key behavior and room isolation pass against disposable Synapse.
- **Authorization:** outsider, inactive, expired-session and former-member access
  fail; acceptance/provisioning races are idempotent; unlink revokes bootstrap and
  Matrix membership; relink cannot expose the old room.
- **Storage/privacy:** Django stores no Matrix login/access token, device key or
  message content; Synapse stores no message plaintext for encrypted rooms; tokens,
  keys and message content are absent from logs, analytics and crash output. Local
  store protection, backup exclusion, logout/account-switch wipe and
  device-replacement policy are verified.
- **Infrastructure:** TLS, private registration/federation, minimum service
  privileges, rate/media limits, secret rotation, monitoring, backup and timed
  restore are exercised. Synapse metadata exposure is documented honestly.
- **Destructive migration:** sanitized production-like rehearsal, pre/post counts,
  exact media allowlist, unchanged auth/social/profile checksums, unchanged
  `.DS_Store`, client database cleanup, and full snapshot restore pass.
- **UX/accessibility:** loading, empty, offline, retry, expired session, mapping
  pending/failed, permission denied, unlink and unknown-event states are usable on
  Android and iOS, including keyboard, safe area, large text and screen readers.
- **Governance:** independent security/privacy review and license/notice review are
  complete before making a production E2EE claim.

Media, voice, replies, edits, reactions, deletion, rich cards, Sparks, Ask/polls,
Planner enhancements, nickname/options and calls each require their own contract,
cross-platform implementation and tests. Passing encrypted text does not enable
or imply any of them.

## Verification record for the KMP cutover

Final local verification on 2026-10-18 used frontend implementation merge
`b0e335e89cd9a884fd1cdd5d4ebe229ccf5f7944` with backend
`2a7d1f7d502bbaf3fb11b2330835a0d64bbe3ea5`:

- Android unit tests passed: 197 tests, zero failures/errors and one expected
  opt-in integration skip. Android compilation, debug APK assembly and the
  device-only KeyStore cleanup test compilation also passed.
- Apple-silicon iOS simulator tests passed: 134 tests with no failures, errors or
  skips. The Xcode Debug app built for, installed on and launched in an iPhone 17
  Pro / iOS 26.5 simulator. A generic dual-architecture simulator build remains
  unsupported because this release intentionally removed `iosX64`.
- `python3 tools/workspace.py verify all` passed the paired contracts, theme,
  selected KMP checks, backend system check and 231 backend tests with 11 expected
  SQLite skips. The separate disposable KMP-to-Django loopback integration passed.
- The running development stack reported healthy Redis, Synapse, Synapse
  PostgreSQL and SriSu PostgreSQL services; Django and the Matrix reconciler were
  running. The live readiness command verified Matrix policy and all three eligible
  rooms with zero failures or outstanding work. Its short-lived attestation is a
  destructive-migration guard and may expire after the already-applied migration;
  it is not a runtime chat availability switch.
- LAN probes passed against the same ignored development configuration used by the
  app: the backend host on Django port 8000 and Synapse port 8008. The per-machine
  address remains outside source control.

The feature branch and integration merge were pushed normally; no deployment is
implied. No Android physical-device run, authenticated live two-user Android↔iOS
message exchange, production data migration or independent security review is
claimed. Those remain release gates above.


The historical text-only cutover notes above are superseded for Sparks by [the October 23 implementation](sparks.md): four decks, protected drafts and mutually revealed encrypted answers. Other unimplemented activities are unchanged.

## Core consolidation, 29 October 2026

The current Matrix UI, presence and waveform features are consolidated with the
activity-notification/Crashlytics branch. Matrix push remains unavailable. See
[notification compatibility and current scope](notifications.md#consolidated-core-integration--29-october-2026).
