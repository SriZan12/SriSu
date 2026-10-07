# Couple Chat implementation record

Record date: 2026-10-07. This is the central cross-repository record for the
Couple Chat rebuild. It describes the inspected design intent, agreed product
rules, target contract, current worktree foundation, security gates, and the
evidence still required before release. It does not declare the current chat
production-ready.

The authoritative supporting records are:

- [ADR 002: chat E2EE gate](../architecture/decisions/002-chat-e2ee.md).
- [Current-system audit](../architecture/system-design/01-current-system.md),
  [target architecture](../architecture/system-design/02-target-architecture.md),
  [contracts](../architecture/system-design/03-contracts.md), and
  [migration roadmap](../architecture/system-design/05-migration-roadmap.md).
- Backend `AGENTS.md`, `docs/workspace.md`, and the eventual backend chat
  contract/migration record. The backend remains authoritative for membership,
  authorization, ordering, idempotency, and feature-resource state.

## Repository baseline and inspection boundary

These are the local working-copy facts recorded before feature edits. They are
not deployment claims.

| Repository | Canonical identity / integration branch | Checkout at inspection | HEAD at inspection | Initial worktree state |
| --- | --- | --- | --- | --- |
| KMP frontend | `SriZan12/SriSu` / `dev-new-theme` | `dev-core-architecture` | `fa7e4fa` | Clean |
| Django backend | `SrizanKhadka/SriSu` / `dev` | `codex/modular-navigation-retire-singles` | `a9db211e0b6c5f9ba55c11670321d01dbf458ac4` | Only an unrelated modified `.DS_Store`; preserved and excluded |

`python3 tools/workspace.py status --remote` and explicit fetches reached both
remotes. The fetched integration refs were frontend `origin/dev-new-theme` at
`3fa6822` and backend `origin/dev` at `ecc8592`. The frontend's required navigation
baseline was cherry-picked onto new branch `codex/couple-chat-rebuild`, producing
pre-feature HEAD `89540da`; the backend feature branch of the same name was created
at `a9db211`. No checkout was reset, rebased, stashed, cleaned, or silently merged.
The same branch name is coordination only: the repositories, commits, rollout, and
push results remain independent.

## Figma evidence and limits

Design source: Figma file `LztysD1YvINX7RwZpnhhTt`, Message page
[`1:5`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5).
The captured Message canvas was inspected across the base conversation, voice,
Spark, Ask/poll, Planner, and chat-options areas rather than treating the first
visible phone as the whole feature.

Known node references from that inspection are:

- Message canvas child references
  [`184:142`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-142),
  [`184:218`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-218), and
  [`184:274`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-274).
- Interaction nodes
  [`184:28`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-28)
  (“hold to react”),
  [`184:33`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-33)
  (“photo viewer”), and
  [`184:38`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-38)
  (“plus picker”).
- Spark section
  [`184:2`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-2).
- Chat-options section
  [`185:1626`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=185-1626).

The captures establish visible hierarchy and flows, including:

- an intimate two-person timeline, message composer, message actions, reply and
  reaction treatments;
- voice recording and voice-message playback states;
- Spark prompt selection, sent/waiting, partner-answer, own-answer/reveal, and
  both-answers-visible states;
- Ask as both free-response and poll flows, including adding choices, waiting,
  vote activity, and a settled result;
- Planner creation fields for activity type, title, date/time, location, and
  reminder; partner responses of Yes, Another time, and Can't; and a plan detail
  with checklist treatment;
- a separate conversation-options area including a nickname interaction.

The connected Figma account is on the Starter plan. Context/property calls hit
that plan's limit, so exact frame names for every child reference, component and
variant properties, variables, hidden prototype connections, and export metadata
could not all be read. The node references above and captured pixels are evidence;
they are not a claim that every internal layer or prototype state was inspected.
No asset was exported and Figma was not modified. Where the captures do not define
data ownership or security semantics, this record deliberately defers the behavior
instead of turning sample copy into a backend contract.

### Inspected Message variants mapped to product state

The successful metadata read supplied exact names for six Message nodes. Spark and
options have separately verified section references. Ask and Planner were visibly
inspected on page `1:5`, but the connector did not return separate child IDs for
those groups before the Starter-plan limit blocked further context/property reads.

| Inspected Figma variant / action | Exact available Figma reference | Domain and backend authority | Implementation status |
| --- | --- | --- | --- |
| Empty conversation — “No messages yet” | [`184:142`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-142) | Authorized relationship room plus an empty, bounded history page and snapshot high-watermark. Empty is not an error and does not create sample messages. | Legacy screen can render no rows. Full v2 empty timeline/composer is **blocked** behind secure integration. |
| First message / new private space — “First message sent” | [`184:218`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-218) | One stable client operation, exact encrypted envelope, server sequence/revision, and the relationship instance that owns the room. Introductory copy is UI resource text, not persisted chat data. | KMP/backend operation foundations exist; a verified encrypted first-message flow is **blocked**. |
| Established thread — “The thread” | [`184:274`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-274) | Keyset history plus incremental changes; typed text/reply/tombstone projections; receipt high-watermarks; local Room projection is observable state. | Legacy plaintext thread exists but is no longer the secure route. Full v2 thread, pagination, grouping, unread divider, and scroll recovery are **not integrated**. |
| Message reactions and long-press actions — “hold to react” | [`184:28`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-28) | One desired encrypted reaction per `(message, actor)`; edit/delete/reply actions reauthorize and use operation ID plus expected revision. | Legacy UI exists. V2 backend/DTO foundation is **partial**; authoritative reducer and UI are **blocked**. |
| Media message / viewer — “photo viewer” | [`184:33`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-33) | Client-encrypted original and thumbnail, opaque owned staged attachment, encrypted caption/key/metadata, authorized ciphertext delivery. | Legacy plaintext photo path now has an authorization-aware delivery foundation. Secure photo/video lifecycle and viewer remain **blocked** by the attachment-format and platform work. |
| Composer plus tray — “plus picker” | [`184:38`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-38) | A presentation launcher only. Each item is enabled from server capability plus local platform readiness, then delegates to Media, Spark, Ask, or Plan ownership. The tray itself owns no data and grants no permission. | Existing composer exposes a photo affordance, not the inspected typed plus tray. Enable only completed capabilities; remaining items are **blocked/deferred**, never silent buttons. |
| Voice recorder / voice bubble | Page [`1:5`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5); separate child ID unavailable | Platform audio recorder/player, interoperable codec, real metering data, encrypted attachment, and encrypted duration/waveform/key metadata. | Existing mic control is inert. Voice is **blocked** by recorder/player, attachment E2EE, and interoperability work. |
| Conversation Sparks choose/wait/answer/reveal | Spark section [`184:2`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-2) | New versioned Spark template/session/responses with relationship-bound authorization, reveal transaction, revisions, and idempotent operations; encrypted prompt/answers in chat. | Product design is inspected. Domain, backend, KMP, and E2EE support are **blocked**, not represented by profile-story prompts. |
| Ask free-response and poll create/wait/vote/result | Page [`1:5`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5); separate child ID unavailable | New Ask/poll instance and vote authority; encrypted question/options/answers; explicit reveal/change-vote policy; idempotent desired vote. | Visually inspected, but product authority and privacy policy are **blocked** and no model/API exists. |
| Planner create/respond/detail/checklist | Page [`1:5`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5); separate child ID unavailable | `CouplePlanModel` is the nearest existing domain authority. Figma-only type/location/reminder/checklist need additive fields and revisioned operations; chat carries a typed reference rather than a stale Plan copy. | Basic Couple Profile Plan persistence is implemented outside v2. The complete Planner/card flow is **partial/deferred**. |
| Conversation options and nickname editor | Options section [`185:1626`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=185-1626) | A future typed conversation-settings resource. Nickname actor, subject, audience, limits, revision, and clearing semantics must be explicit and must not alter personal identity. | Visually inspected; existing generic settings/nickname fields are not sufficient authority. Save flow is **deferred**. |

This table is intentionally stricter than a visual implementation checklist. A
captured screen is evidence of intended interaction, not evidence that its domain,
permissions, encrypted payload, persistence, or failure recovery already exists.

## Agreed messaging behavior

WhatsApp and Viber are familiarity references; SriSu's design system remains the
visual source of truth. SriSu uses the following coherent policy for the scoped
features:

| Behavior | SriSu decision |
| --- | --- |
| Edit | Sender only while authoritative server age is `<= 15 minutes` from `created_at`, inclusive; text-capable content only; expected revision required; edited state remains visible. An age greater than 15 minutes is denied regardless of client clock. |
| Delete for everyone | Sender only while authoritative server age is `<= 48 hours` from `created_at`, inclusive; an age greater than 48 hours is denied. Success replaces content with a durable tombstone and removes unreferenced downloadable media under the retention policy. It cannot retract screenshots or independently saved/downloaded content. |
| Delete for me | Either participant may hide an accessible message for that account across its devices; it does not mutate the partner's view. The per-user tombstone survives refetch and reconnect. |
| Reactions | At most one desired reaction per user per message. Setting another value replaces it; setting null removes it. The unique `(message, user)` authority and idempotent operation ID resolve retries and races. |
| Pending | The local durable outbox contains an operation that is not yet acknowledged by the server. It must not look delivered. |
| Sending | One active attempt is in flight for the same operation and exact ciphertext. |
| Sent | The server transaction durably committed the operation and returned or later reconciled the same authoritative result. A socket broadcast alone is not sent proof. |
| Delivered | At least one currently eligible recipient device authenticated, decrypted, and durably applied through the message sequence. A connected socket is not delivery. |
| Read | The recipient viewed the conversation through the message sequence while foreground, and the monotonic server receipt was accepted. Read is not inferred from app launch or socket presence. |

The first E2EE pilot permits one active secure-chat device per account. A future
multi-device policy must define per-device delivery aggregation before changing
the receipt meanings above. Typing remains ephemeral and expires; it is never a
durable receipt or relationship-monitoring signal. Push notifications, local
message notifications, voice calls, and video calls are outside this release.

## Screen and action-to-data mapping

Figma determines presentation and interaction intent. The server and encrypted
contract determine what data may exist or be shown.

| Screen / action | Visible intent | Authoritative data and operation | Access, failure, and empty behavior | Current factual status |
| --- | --- | --- | --- | --- |
| Base conversation | Partner header, ordered timeline, text/emoji/links, multiline composer, reply, copy, reactions, actions, date/unread grouping, typing/offline state | Current relationship instance and room from the server; encrypted inner message payload; server message sequence/revision; local outbox state; server receipt cursors | Both current members only. Empty timeline is a valid state. Cached reading must survive network loss. Revoked membership closes access and clears protected drafts. Unknown content versions render an inert update-required item instead of crashing. | Legacy UI supports several visible actions over plaintext/WebSocket data. The new route currently opens a fail-closed secure status screen; the full v2 timeline/composer is not integrated. |
| Text send / retry / cancel | Immediate local bubble, then truthful state transitions | Stable UUID operation; exact encrypted envelope; transactional server operation/result/change/outbox; Room transaction for provisional message plus outbox | Blank/oversized input is rejected before send. Ambiguous failure retries the same ID and exact ciphertext. Cancellation may stop an unclaimed local attempt but cannot claim to undo a server commit; reconcile first. | KMP Room/outbox/repository and backend service foundations exist in the working tree, but no working encrypted text exchange is verified. |
| Reply / jump to original | Context preview in composer and bubble; tap/accessible action finds source | Encrypted reply preview or source-derived in-memory preview; outer authorized `reply_to_id`; surrounding-history fetch when absent locally | Reply target must be in the same currently authorized room. Tombstoned, expired, or inaccessible originals show a content-free unavailable preview. No cross-room existence leak. | Legacy text/photo reply exists, but jump-to-original is an inactive callback. V2 reply rendering and surrounding-history recovery are not implemented. |
| Edit | Edit composer and visible edited marker | `operations` action `edit`, sender identity from authentication, expected message revision, replacement encrypted envelope, inclusive server-age rule `<= 15 minutes` | Draft is retained on recoverable failure. Conflict reloads current state. Deleted, hidden, legacy-plaintext, non-owned, or messages older than 15 minutes are not editable through v2. | Backend tests pass for authorization, revision conflict, idempotency, and the inclusive time boundary; integrated client UI is not implemented. |
| Delete | Separate Delete for me / Delete for everyone choices | Per-user visibility record or global revisioned tombstone; idempotent operation and durable room change | Global deletion follows the inclusive sender-only rule `age <= 48 hours`; older messages permit only authorized per-user hiding. Offline recipients receive the tombstone through changes; old history cannot resurrect content. | Backend tests pass for both scopes, viewer-specific projections, inclusive time boundary, tombstone redaction, and retryable media cleanup; the v2 client reducer/UI is not integrated. |
| React | Add/change/remove one reaction; optimistic presentation reconciles to authority | One encrypted desired-value reaction per `(message, actor)` with operation ID and change sequence | Current room member and accessible live message only. Duplicate retry returns the same state; delete wins over late display. | Unique backend state, authorization/idempotency tests, and typed KMP DTO foundation exist; v2 client mutation/reducer/UI is not integrated. |
| Photos/videos | Picker, ordered preview, caption, progress, retry/cancel, bounded viewer | Client-transcoded and client-encrypted original/thumbnail; opaque staged attachment; encrypted caption/key/metadata; server ownership, byte quota, room binding, expiry | No public object URL. Missing/expired download shows an unavailable item. Cancellation and failed replacement preserve prior valid content. | Backend ciphertext stage/fetch/cancel routes and guarded legacy delivery now exist as foundation. The reviewed client encryption format, KMP calls/UI, video support, progress/recovery, and interoperability remain blocked. |
| Voice recording and playback | Hold/lock recording where shown, cancel, preview, send, duration, real waveform, play/pause/seek | Platform recorder produces an interoperable codec; actual metering samples; encrypted attachment; duration/codec/waveform and key inside encrypted payload; one audio-session owner | Permission denial, interruptions, audio focus/session changes, backgrounding, cancellation, unavailable media, and storage failure are explicit states. Android output must play on iOS and vice versa. | The legacy mic button has no action. Platform recording/playback and the reviewed attachment AEAD format are absent, so voice remains blocked. |
| Spark | Choose prompt, send/wait, partner response, own response, reveal, both answers | A future versioned Spark template/session/response domain; message holds only an authorized activity reference plus encrypted private display payload | Current members of the originating relationship instance only. Partner answer is absent before allowed reveal. Duplicate submit and reveal races are server-idempotent. Missing/revoked session becomes an inert unavailable card. | No Spark models, APIs, repository, navigation, or tests exist. Design is inspected; implementation is blocked by domain and E2EE work. |
| Ask — free response | Ask a custom question and wait/respond in-chat | A future Ask activity instance, not Couple Profile story answers; encrypted question/answer content and revisioned participant state | Relationship-scoped authorization on every read/action. Recoverable failure keeps the draft. Unlink prevents further access. | No matching domain/API exists. It must not be silently mapped to fixed profile-story prompts. |
| Ask — poll | Add options, send, vote/change vote if policy permits, show waiting/activity/result | Future versioned poll, participant vote authority, idempotent desired vote, encrypted option text; only deliberately disclosed routing/state metadata may be server-visible | Product/security policy must decide whether votes are changeable and what result state is visible before both vote. Unknown/withdrawn poll is inert and content-free. | Design states are visible, but authority, reveal rules, and E2EE metadata policy are undefined; blocked rather than invented. |
| Plan / Planner | Activity type, title, date/time, location, reminder, partner response, detail/checklist | Existing `CouplePlanModel` currently owns title, start, response/note, completion, revision, request ID, and original membership audience. Richer Figma fields require additive domain fields. Chat should carry an activity reference, while current Plan API remains the live authority. | Current members in the plan's captured relationship audience only. Create/respond use stable operation/request IDs and revisions. Missing/revoked plan renders unavailable; no stale embedded card may authorize an action. | Existing Couple Profile Plan create/list/respond works outside v2 and emits a legacy plaintext `profile_action` card. Activity type, location, reminder, checklist, richer response UI, and secure v2 card are not complete. |
| Moment share/reply card | Open or reply to an eligible Moment without converting it into permanent chat media | Existing Moment ID and live Moment repository; optional content-free activity reference; any permitted temporary preview is encrypted and still subordinate to original audience/expiry | Resolve authorization and exact expiry on every open. After expiry/deletion/block/unlink, show a content-free unavailable card. Never copy caption/photos into permanent plaintext or grant access from the message alone. | Backend Moment authorization/expiry/media exists; KMP has discovery summaries only and no Moment detail/share/reply chat flow. Deferred. |
| Challenge card | Invite/respond/open a practical couple activity | Future challenge template/version/instance repository; idempotent action and expected revision; encrypted private contribution text | Current relationship participants only. Stale or withdrawn source resolves to authoritative unavailable state; retries do not duplicate an instance. | No Challenge implementation or inspected complete card flow exists. Deferred. |
| Conversation options / nickname | Separate options screen and nickname editor from the Figma options area | A typed conversation-settings contract. Current `ChatRoom.settings` is untyped, and `CoupleMembershipModel.nickname` lacks an audited actor/subject meaning for per-viewer chat aliases. | Server derives actor and room. A nickname must not mutate personal identity or leak into unrelated profiles. Decide whether the alias is viewer-owned, shared, or self-owned before schema/API work. Empty removes only the authorized alias. | Figma area is inspected; backend semantics, route, repository, and UI save flow are not implemented. The existing fields must not be repurposed by assumption. |

## Architecture and ownership

The target keeps one authoritative path instead of parallel feature state:

```text
Compose screen
  -> feature-scoped ViewModel
  -> ChatV2Repository
  -> account/environment/room-scoped Room tables (observable source)
  -> authenticated HTTP snapshot, changes, and mutation APIs
  -> Django chat-v2 services and PostgreSQL transaction
  -> durable ChatChange + ChatOutbox
  -> Channels/Redis content-free sync hint
  -> HTTP changes reconciliation back into Room
```

Important boundaries:

- `ChatNav.ChatScreen(roomId: String)` carries a stable UUID only. It must never
  carry a message list, partner object, owner flag, or decrypted content.
- `SessionCoordinator` and the server-authenticated user scope every request and
  result. Environment, account, room, and session generation scope local keys.
- Room is the observable synchronization source. WebSocket callbacks are hints;
  they do not directly append authoritative UI messages.
- Django services own membership checks, stable ordering, expected revisions,
  operation deduplication, and change creation. REST and future socket commands
  must call the same service functions.
- PostgreSQL stores opaque envelopes and required routing metadata for secure v2,
  never private message plaintext. Redis carries invalidation only, not the sole
  durable copy of a message or mutation.
- Feature domains own Spark, Ask, Plan, Challenge, and Moment state. Chat renders
  typed references through those repositories; it does not duplicate their
  authorization logic inside a giant message ViewModel.

Current draft anchors are
`features/chat/v2/data/ChatV2Repository.kt`,
`features/chat/v2/data/local/ChatDatabase.kt`,
`features/chat/v2/data/remote/ChatV2Contracts.kt`,
`features/chat/v2/crypto/E2eeEngine.kt`, and backend
`chat/services/v2.py`, `chat/models.py`, `chat/presenters/v2.py`, and
`social/services/relationship_service.py`. Their presence is not equivalent to
successful migration, routing, compilation, integration, or device verification.

## Relationship-instance lifecycle

Conversation identity belongs to a specific accepted relationship, not to a
reusable unordered pair of user IDs.

1. **Pending invitation:** no active chat is created. Only the intended receiver
   may accept; expired, rejected, cancelled, or unrelated requests cannot create
   a room.
2. **Acceptance transaction:** lock the invitation and both users in stable order;
   verify pending state and absence of conflicting active memberships; commit the
   accepted connection, Couple, exactly two memberships, and exactly one ChatRoom.
   Retry of the same operation returns that same relationship and room. The
   backend response supplies `chat_room_id`; the UI does not invent it.
3. **Active:** every history, mutation, receipt, attachment, socket subscription,
   event delivery, and feature-card action rechecks both active memberships and
   the accepted connection. A cached capability is not authorization.
4. **Repair:** an accepted relationship missing its room is reconciled through the
   same invariant-preserving service. Pair lookup must not attach an old room.
5. **Unlink/revocation:** end both memberships, advance a durable membership epoch,
   reject queued operations sealed for the old epoch, revoke secure-device/session
   access as defined by ADR 002, close/reject sockets, and retain history under the
   approved policy without exposing it. Previously downloaded content cannot be
   recalled.
6. **Later relink or partner replacement:** create a new relationship instance,
   room, membership epoch, and cryptographic conversation. Never reuse prior
   history, sessions, keys, or outbox work merely because a user pair repeats.

The backend transactionally accepts and creates/reuses a room keyed by Couple, and
`unique_chatroom_couple` protects one room per Couple. Ending a connection retains
rows while current-membership selectors deny access; relinking creates a new
relationship and room. Post-commit content-free lifecycle hints now reach both user
groups, and PostgreSQL tests cover v2/legacy send versus unlink and v2 send versus
device revocation under the shared lock order. A protocol-bound membership epoch
and complete revocation of legacy sid-less tokens remain release gates. KMP still
defaults an unknown epoch to `0`; secure sending remains unavailable until a real
server epoch is loaded and bound into the encrypted context.

## Chat-v2 HTTP contract

The typed KMP draft identifies the contract with:

```http
X-SriSu-Chat-Contract: chat-2
```

The intended routes are:

| Method and route | Purpose | Key contract |
| --- | --- | --- |
| `GET /api/chat/v2/capabilities/` | Environment/account gate | `api_version`, `encrypted_writes_enabled`, `protocol_status`, byte limits, supported content kinds |
| `GET /api/chat/v2/rooms/{room_uuid}/messages/` | Newest-first keyset history | optional `before_sequence`, bounded `limit`; returns room, high-watermark, messages, `has_more`, next sequence |
| `POST /api/chat/v2/rooms/{room_uuid}/messages/` | Idempotent encrypted send | `operation_id`, `content_kind`, opaque `envelope`, optional same-room `reply_to_id`, owned staged `attachment_ids` |
| `GET /api/chat/v2/rooms/{room_uuid}/changes/` | Incremental reconciliation | `after_sequence`, bounded `limit`; ascending authorized changes, high-watermark, next scanned position |
| `POST /api/chat/v2/rooms/{room_uuid}/operations/` | Edit/delete/reaction desired state | `operation_id`, action, `message_id`, expected revision where required, replacement/reaction envelope where required |
| `PUT /api/chat/v2/rooms/{room_uuid}/receipts/` | Monotonic delivery/read cursor | `operation_id`, `delivered_through`, `read_through` with `read <= delivered <= room high-watermark` |
| `POST /api/chat/v2/rooms/{room_uuid}/attachments/` | Stage owned ciphertext | multipart operation ID, claimed ciphertext digest and ciphertext bytes; server enforces room/device ownership, byte limit, digest, state, and expiry without inspecting plaintext |
| `GET/DELETE /api/chat/v2/rooms/{room_uuid}/attachments/{attachment_uuid}/` | Authorized ciphertext fetch / cancel | current room visibility and attachment state are rechecked; claimed content cannot be cancelled as if never sent |

A successful mutation returns the same `operation_id`, whether it was replayed,
the resulting `change_sequence`, and the current authorized message projection.
Messages expose server ID, sequence, revision, sender ID, content kind, opaque
envelope, reply ID, authorized opaque attachment/reaction references, tombstone
and hidden flags, legacy-plaintext provenance, and server timestamps. They do not
expose private decrypted content.

One operation ID is unique per room and authenticated actor. The server hashes the
canonical semantic payload in the same transaction. Exact replay returns the
original result; reusing the ID with a different payload returns a conflict.
Revision conflicts never blind-overwrite. An unrelated/former user receives a
neutral not-found response where object existence is sensitive. Authentication,
validation, conflict, rate-limit, resync, and unavailable outcomes must use the
existing structured error convention and meaningful HTTP status, not HTTP 200 for
every result.

Changes advance over the scanned server sequence even when a private per-user row
is filtered, so hidden changes cannot stall or leak through cursor behavior.
Clients sort/deduplicate defensively, ignore lower entity revisions, atomically
apply a page plus checkpoint, and fetch a snapshot when the retained cursor is too
old. A socket event should contain only contract version, room ID, membership epoch,
and high-watermark/change hint; it never carries plaintext and never bypasses the
HTTP authorization/recovery path.

The implementation adds these views/routes in backend `chat/api/v2.py` and
`chat/urls.py`, persistence in `chat/0004_chat_v2_foundation.py`, and bounded
content-free hint publication in `chat/services/outbox.py` plus
`dispatch_chat_outbox`. Backend migration, SQLite, and PostgreSQL suites exercise
the contract and concurrency foundation. KMP supplies typed DTOs, a scoped Room
store, exact-envelope outbox, checkpoint reconciliation, and capability gate, but
does not expose the attachment calls or a usable encrypted conversation UI. This
is verified foundation code, not deployed or production-E2EE behavior.

## Offline source of truth and synchronization

The v2 Room schema is keyed by `(environment, account, room, resource)` and stores:

- conversation metadata and membership epoch;
- opaque messages and delivery state;
- an exact-ciphertext outbox with operation ID and retry schedule;
- incremental checkpoints and tombstones;
- opaque per-user reactions and monotonic receipts;
- attachment-transfer metadata without plaintext filenames/captions.

The required behavior is:

1. Render the scoped local projection immediately.
2. Persist the provisional message and exact encrypted outbox operation atomically.
3. Make at most one active attempt; retry with jitter using the same ID and bytes.
4. Merge the server acknowledgement or history result without replacing stronger
   local delivery state with weaker state.
5. Apply ascending authorized changes and checkpoint in one local transaction.
6. Use WebSocket notifications only to schedule reconciliation.
7. On foreground/resume, refresh capabilities and membership before draining the
   outbox, then catch up from the checkpoint.
8. On logout or account switch, cancel old work and clear that account's private
   projection/outbox so late responses cannot populate another account or room.

The present database deliberately stores opaque envelopes, not decrypted content
or keys. The UI cannot offer genuinely useful offline history until a reviewed
protocol adapter and protected local decryption/state design exist. Persisted
plaintext drafts are explicitly disabled; draft restoration remains blocked until
encrypted, backup-excluded, account-scoped storage is implemented. An unavailable
local store must produce a recoverable state rather than silently switching the
timeline to transient socket memory.

## Typed feature-card contract and readiness

The only existing chat feature cards are Couple Profile story and Plan actions.
They are currently server-authored `MessageModel.profile_action` JSON attached to
ordinary plaintext messages and rendered as a text bubble plus action button.
They are legacy compatibility, not the secure typed-card design. The story action
also needs prompt-specific navigation correction, and the Plan card is narrower
than the Figma Planner.

Sparks, Ask/polls, and Challenges have no authoritative product models or APIs.
Moments have substantial backend authorization/expiry support, but no KMP
Moment-detail/share/reply card flow. Therefore none may be advertised as a working
secure chat card merely because a visual card can be drawn.

For future integration, use a small, versioned and content-free outer reference:

```json
{
  "schema": "srisu.activity-ref",
  "version": 1,
  "activity_id": "00000000-0000-4000-8000-000000000001",
  "kind": "couple_plan",
  "resource_id": "42",
  "resource_revision": 3
}
```

The server derives room, actor, Couple, and membership from authentication; it
never accepts `couple_id`, `user_id`, `is_owner`, or `can_edit` as authority.
Private prompt text, options, votes, answers, captions, summaries, reply previews,
and any permitted historical rendering travel only in the encrypted inner payload.
Whether even `kind` and `resource_id` may remain server-visible is part of the
approved metadata threat model; a more opaque locator is required if that leakage
is not acceptable.

Each domain action uses its own durable `operation_id`, canonical payload hash,
and expected resource revision. The message card resolves live authorized state
through the feature repository instead of trusting stale embedded buttons. A
supported client maps the raw versioned payload to a sealed type. Unknown schema
versions display an inert update-required card and do not fail history parsing.

Recommended kinds are `profile_story_prompt`, `couple_plan`, and, after its client
flow exists, `moment`. Reserve rather than prematurely enable `spark_session`,
`ask`, and `challenge_instance`. Missing, deleted, blocked, revoked, or inaccessible
resources return a neutral unavailable projection. Moment expiry continues to use
the existing exact-expiry/not-found policy and a content-free tombstone; chat must
not permanently retain the Moment caption or media to defeat its lifecycle.

## Privacy and E2EE production gate

[ADR 002](../architecture/decisions/002-chat-e2ee.md) is the release boundary.
The current `UnavailableE2eeEngine` is intentionally fail-closed. There is no
libsignal dependency, Android adapter, Swift/CocoaPods facade, device/prekey
bootstrap, transactional protocol store, safety-number flow, or cross-platform
interoperability evidence. The backend cannot authenticate an arbitrary JSON blob
as valid E2EE merely because it is named `encrypted_envelope`.

Until every ADR gate passes:

- `encrypted_writes_enabled` defaults false and `protocol_status` is not `ready`;
- the client must not enter a secure composer unless server capability, current
  membership epoch, device registration, and the exact protocol version agree;
- no plaintext fallback is permitted after secure-chat failure;
- legacy plaintext history remains distinctly labelled and is never described as
  encrypted or automatically re-encrypted;
- voice, photos, video, thumbnails, captions, waveform metadata, private replies,
  reactions, Spark answers, Ask/poll content, Challenges, and Moment previews remain
  disabled until their client-side encrypted formats are reviewed;
- logs/metrics may contain opaque IDs, versions, sizes, timings, and categorized
  failures, but no plaintext, keys, raw protocol state, private card content, or
  decrypted media metadata.

Server-visible metadata still includes accounts/devices, current relationship and
room routing, IP/network logs, timestamps, operation and receipt identifiers,
ciphertext/attachment sizes, and traffic patterns. E2EE is not anonymity and
cannot protect content on an unlocked/compromised endpoint or retract content the
partner already saw or saved.

## Migration, rollout, rollback, and deployment

### Data migration

- Preserve users, invitations, Couples, memberships, rooms, messages, Moments,
  Plans, and migration history. Do not drop or recreate legacy tables.
- Add v2 fields/tables/indexes through normal migrations. Backfill stable room
  message order in bounded resumable batches before enforcing non-null sequence
  constraints. Resolve duplicate Couple rooms before enabling the uniqueness
  constraint in a production database.
- Mark legacy messages with explicit plaintext provenance. Do not fabricate an
  operation ID, delivery proof, E2EE provenance, or valid protocol envelope from
  text/timestamps. Keep legacy IDs stable.
- Introduce relationship/membership epoch storage and bind it to devices, rooms,
  envelopes, cursors, and outbox rows. Reconciliation must report inconsistent
  active memberships instead of choosing a relationship heuristically.
- Migration reverse behavior must be documented per migration. Reverting Git is
  not a safe reversal of committed messages or new schema; prefer forward repair.

### Rollout order

1. Land migrations, authorization, relationship repair, typed errors, idempotent
   operations, history/changes, and durable outbox publication with capabilities
   disabled by default.
2. Apply migrations and run backfill/constraint validation in staging. Configure
   ASGI/Channels, Redis, worker leases, rate limits, request/body limits, private
   object storage, orphan cleanup, retention, and content-free observability.
3. Ship the KMP storage/reconciliation code while secure entry remains disabled.
4. Complete the same reviewed E2EE protocol version on Android and iOS, then run
   cross-platform, crash-recovery, tamper, revocation, and two-user tests with
   synthetic internal accounts.
5. Enable a backend-controlled environment/account allowlist for a text-only,
   one-device internal pilot. A local client flag cannot override it.
6. Add mutation UI, then separately reviewed attachment/voice support and typed
   feature cards. Expand only after each slice's authorization and recovery tests.
7. Consider production enablement only after the ADR's licensing, build, protocol,
   lifecycle, interoperability, privacy, and independent-review blockers close.

Backend support must precede a dependent mobile release. Matching branch names and
successful pushes are not an atomic release and do not deploy either repository.

Private media must remain inaccessible at the object-store/CDN origin. The initial
safe delivery model is an authenticated, room-authorized proxy that rechecks current
membership, attachment ownership/state, expiry, and message visibility before
streaming opaque ciphertext with private/no-store headers. The proxy cannot inspect,
transcode, dimension-check, virus-scan, or generate thumbnails from E2EE plaintext;
those content-dependent operations occur on the client before encryption. If direct
signed ciphertext URLs are later approved, their lifetime must be short and no
longer than remaining authorization/expiry, with the explicit caveat that block,
unlink, or deletion cannot revoke a URL already issued until it expires. A Django
view alone does not make a publicly readable bucket or CDN private.

### Rollback

- Disable new secure operations through the server capability/allowlist while
  retaining already committed ciphertext, operations, sequence, and change rows.
- Keep authorized clients able to reconcile committed results. Never reopen a
  private file, bypass a security correction, or assign a new operation ID merely
  to make a retry appear successful.
- Do not silently fall back from a secure conversation to legacy plaintext. If no
  approved secure path is available, show chat unavailable. Any temporary legacy
  access must be a separately labelled, explicitly approved compatibility policy.
- Stop starting new feature activities while preserving authorized existing domain
  records. Missing cards degrade to content-free unavailable items.
- Preserve additive schema during application rollback unless a separately tested
  reverse migration is demonstrably safe and non-destructive.

## Implementation status at final verification

“Verified foundation” means the listed code compiled and its focused/automated
checks passed. It does not mean the complete product flow reached a device, passed
two-client interoperability, was independently audited, or was deployed.

| Area | Status | Evidence / remaining boundary |
| --- | --- | --- |
| Repository and architecture audit | Verified | Both repository identities, instructions, integration refs, legacy chat, relationship flow, profile cards, Moments, and chat-v2 code were inspected. Fetched refs are recorded above. |
| Figma Message canvas | Partially inspected | Captured page `1:5` and known nodes above were inspected. Starter-plan limits blocked complete component/property/prototype context. |
| Relationship-to-room lifecycle | Verified foundation | Transactional idempotent acceptance creates one Couple, two active memberships, and one room; end/relink authorization and repair paths are implemented. SQLite/PostgreSQL suites cover migration abort/recovery and send/unlink/device-revocation races. A protocol membership epoch and legacy sid-less-token cutoff remain open. |
| Chat-v2 backend persistence/services | Verified foundation | Opaque messages, idempotent operations, ordered changes/outbox, receipts, per-viewer visibility/reactions, staged attachments, capability gate, authorized projections, and bounded workers are implemented. All 213 isolated tests pass on PostgreSQL; default capability remains fail-closed and no deployment is claimed. |
| Legacy chat media delivery | Verified foundation | Uploads are owned, room-associated, type/size/dimension/pixel validated, metadata-stripped and guarded by current authorization. Direct media URLs are rejected/denied, cleanup rechecks eligibility under lock, and failure retry paths are tested. Private origin/CDN configuration remains a deployment gate. |
| Chat-v2 KMP persistence/repository | Verified foundation | Environment/account/room/session-scoped Room schema, exact-envelope outbox, checkpoint reconciliation, typed DTOs, session clearing, DI, and fail-closed capability state compile on Android/iOS and pass unit tests. Useful decrypted history and most mutations are intentionally absent. |
| Stable navigation | Compiled fail-closed shell | `ChatNav.ChatScreen(roomId)` is a typed room-ID destination. Production retains explicit legacy compatibility; the experimental secure route opens an availability shell, not a conversation timeline/composer, and never runtime-falls back. |
| E2EE protocol | Blocked | The engine is deliberately unavailable. ADR 002 production gates, platform adapters, license decision, protocol stores, device APIs, and interoperability/security review remain open. |
| Encrypted text chat | Blocked | No verified Android-to-iOS encrypted send/decrypt/render path. The current secure screen has no composer or timeline. |
| Reply/edit/delete/reaction/receipts | Verified backend/schema foundation; blocked as integrated UX | V2 services/DTOs test reply authorization, desired reactions, per-user hiding, monotonic receipts, revision conflicts, and inclusive `<=15-minute` edit / `<=48-hour` delete-for-everyone boundaries. They are not wired to a secure KMP timeline. |
| Offline cached history/outbox | Unit-tested opaque foundation | Scoped Room/outbox/checkpoint behavior, exact-envelope retry, account/room isolation, and snapshot-to-subscribe catch-up pass unit tests. Useful decrypted offline reading, protected drafts, storage-full behavior, and real-device process-death validation remain unavailable. |
| Photos/videos | Backend transport/authorization foundation; secure feature blocked | V2 ciphertext stage/fetch/cancel routes and guarded legacy media delivery now exist. The attachment AEAD/thumbnail specification, origin/CDN enforcement, KMP transfer/client processing, video, progress/recovery, and cross-platform lifecycle verification remain absent. |
| Voice messages | Blocked | Legacy mic control is inert; recorder/player, codec interop, real waveform, session/focus handling, encrypted attachment format, and tests are absent. |
| Profile story/Plan cards | Legacy only | Plaintext `profile_action` JSON and a simple action button exist. They are not typed E2EE cards; story prompt routing and richer Figma Planner fields remain incomplete. |
| Sparks and Ask/polls | Blocked | Design states are known, but no domain models/APIs/repositories/navigation/tests or encrypted content contract exist. |
| Challenges | Deferred | No implementation or sufficiently inspected complete card design exists. |
| Moment cards | Deferred | Backend Moment policy exists; client detail/share/reply flow and secure reference behavior do not. |
| Conversation options/nickname | Deferred | Figma area exists; actor/subject semantics and typed backend/client save flow are unresolved. |
| Push/local notifications and calls | Out of scope | Extension points may be retained, but no fake delivery or calling infrastructure is included. |
| Production readiness | Blocked | Critical E2EE, attachment/voice clients, secure timeline, feature cards, token cutoff, retention policy, two-device/device validation, performance, deployment, and independent external security evidence are missing. |

## Automated test matrix required before release

| Layer | Required cases |
| --- | --- |
| Relationship backend | Two simultaneous accepts create one Couple and one room; wrong actor/expired/rejected/cancelled request denied; retry returns same result; accepted-but-missing-room repair; unlink/write and unlink/subscription races; later relink receives a new room/epoch and no old history. Run concurrency on disposable PostgreSQL, not SQLite-only skips. |
| Chat backend authorization | Unrelated, former, inactive, blocked, and unauthenticated access denied for history, changes, operations, receipts, sockets, attachments, and feature actions; spoofed sender/couple/room/reply/media identifiers fail without existence leaks. |
| Idempotency and ordering | Exact operation replay returns one message/result; mismatched reuse conflicts; concurrent sends allocate unique order; commit followed by lost ack/broadcast reconciles; outbox retry does not duplicate; filtered private changes do not stall cursors. |
| History and changes | Keyset pagination across tied/active writes; snapshot-to-live race; change gaps; duplicate/out-of-order pages; retained-cursor expiry and fresh snapshot; lower revision never resurrects edited/deleted content. |
| Message policy | Using authoritative server time, edit at exactly 15 minutes succeeds and at 15 minutes plus 1 microsecond is denied; delete-for-everyone at exactly 48 hours succeeds and at 48 hours plus 1 microsecond is denied. Delete-for-me stays per-user; reaction set/change/remove enforces one/user; edit/delete/reaction races converge. |
| Receipts | Pending/sending/sent/delivered/read remain monotonic; WebSocket presence alone does not deliver; read cannot exceed delivered or room high-water; duplicate and lower receipt requests are harmless. |
| KMP repository/store | Cached scoped load; atomic provisional message/outbox; exact-envelope retry after process death; ack loss reconciliation; changes/checkpoint transaction; room A result cannot update B; account/environment/session switch cancels and clears; storage unavailable/full is recoverable. |
| KMP presentation | Empty/loading/cached/offline/reconnecting/failed/revoked states; composer validation; draft retention when protected storage exists; stable scroll on prepend and incoming messages; reply unavailable state; accessible action alternatives; large text, small/large screens, keyboard and safe-area behavior. |
| E2EE | Android-to-iOS and iOS-to-Android vectors; bootstrap, simultaneous first send, delayed/out-of-order/skipped keys, exact-ciphertext retry, tamper/replay/wrong room/wrong epoch rejection, device replacement, identity warning, logout/unlink/reinstall/key loss, and absence of plaintext/keys from backend DB, Redis, files, logs, analytics, and crash output. |
| Attachments/voice when enabled | Cross-user substitution/download denial; ciphertext limits/quota/expiry; interrupted upload/download; orphan cleanup; corrupt/truncated stream; thumbnail/key separation; codec interoperability; permission denial, interruption, audio focus/session, seek and one-recorder/player ownership. |
| Feature cards | Unknown version remains parseable/inert; forged cross-couple reference denied; duplicate action is one transition; revision race conflicts; source missing/deleted/blocked/revoked; exact Moment expiry/media denial; private Spark/Ask/Challenge content absent from outer payload/logs/cache. |
| Compatibility/regression | Supported legacy messages retain IDs/provenance and parse safely; Authentication, invitations, Couple Profile, Moments, Faves, and partner linking still pass; old clients do not misread v2 ciphertext as text. |

## Manual two-user checklist

Run with synthetic accounts only: Partner A on Android, Partner B on iOS, plus an
unrelated third account. Record app/backend commits, build type, OS/device versions,
network conditions, server configuration, and timestamps.

1. Invite B, accept once while both clients are online, and confirm both reconcile
   the same backend-created room without forced duplicate navigation.
2. Repeat acceptance and race two accepts; confirm one relationship/room and the
   same authorized result.
3. Register the permitted secure devices, verify identity/safety UI, and confirm a
   client without the exact capability/protocol remains fail-closed.
4. Exchange multiline/Unicode/link text in both directions. Observe pending,
   sending, sent, delivered, and read only at their defined transitions.
5. Go offline before send, restart after local persistence, reconnect after the
   server commits but before the ack, and verify exactly one message with the same
   operation ID and no false delivery.
6. Reply to text and, once enabled, voice/photo/video/card content. Jump to an
   unloaded original; then repeat after the source is tombstoned or unavailable.
7. Edit exactly at 15 minutes and immediately after it. Delete for everyone exactly
   at 48 hours and immediately after it. Verify the inclusive boundary succeeds,
   the later request fails, and B receives durable tombstones after being
   offline and that delete-for-me affects only the acting account.
8. Add, change, and remove each participant's one reaction while offline/online;
   race reaction with deletion and verify authoritative convergence.
9. Once attachment E2EE is enabled, exchange photo/video and voice both directions;
   exercise preview, progress, cancel, retry, seek, interruption, permission denial,
   app backgrounding, insufficient storage, corrupt/expired download, and codec
   interoperability.
10. Exercise every enabled Spark, Ask/poll, Plan, Challenge, and Moment-card state.
    Confirm repeated taps are idempotent, stale cards reload authority, unknown
    versions are inert, and missing/expired/private sources reveal no content.
11. Change nickname only after its ownership policy exists; verify the change is
    scoped to the intended viewer/relationship and never alters personal identity.
12. Open room A, switch accounts/rooms during slow history/decryption, and confirm
    no A content, receipt, draft, attachment, or late response appears in B.
13. Unlink while both sockets are connected and while A has queued work. Confirm
    REST/socket/card/media access stops, old queued operations fail by epoch, local
    protected state follows policy, and a later relationship gets a new room/keys.
14. Verify the unrelated account cannot enumerate, fetch, act on, or infer the
    room/messages/attachments/cards. Inspect sanitized logs for content leakage.
15. Repeat essential flows after Android/iOS process termination and foreground
    resume. With notifications out of scope, confirm the UI promises catch-up on
    resume rather than instant background delivery.

## Performance targets — unmeasured

These are initial acceptance targets, not achieved measurements. Measure release-
like Android and iOS builds with realistic encrypted histories and stated network/
server load. Report p50/p95, device, history size, payload size, and test network.

| Path | Initial target | Current evidence |
| --- | --- | --- |
| Warm text action to locally visible durable pending row | approximately 100 ms or less | Unmeasured |
| Open to first useful cached conversation page | approximately 300 ms or less | Unmeasured |
| Scroll, type, and record while synchronization/transfer runs | no user-visible stalls; collect frame-time/jank and main-thread/blocking traces | Unmeasured |
| Client send to durable server acknowledgement | Establish and report p50/p95 separately from local render and recipient delivery | Unmeasured |
| Server commit to recipient durable apply while both are foreground | Establish and report p50/p95; never substitute socket publish latency | Unmeasured |
| Foreground reconnect and checkpoint catch-up | Bounded exponential backoff with jitter; report time by missed-change count | Unmeasured |
| Memory, database, ciphertext media cache, and waveform decoding | Stay within explicit bounded page/cache/decode limits; report peak and steady state | Unmeasured |

Also measure backend query count/latency, outbox age/retry count, cursor-resync rate,
decrypt failures, duplicate suppression, attachment throughput, and sanitized error
categories. Do not log content or keys to obtain those metrics.

## Final verification record

The outcomes below are from the frozen implementation tree before commit/push.
Synthetic warning/error logs in the backend suite intentionally exercise storage,
cache, and socket-publication recovery paths; they are not test failures.

| Check / command | Final outcome |
| --- | --- |
| `python3 tools/workspace.py status --remote` plus explicit fetch | **Passed.** Frontend `origin/dev-new-theme` was `3fa6822`; backend `origin/dev` was `ecc8592` at inspection. |
| Frontend `python3 tools/theme/check_theme.py` | **Passed:** 96 documented colors; 24 role pairs at or above 4.5:1, minimum 4.75:1; token/provenance checks passed. |
| Frontend `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 --console=plain` | **Passed.** A forced full unit rerun reported 177 tests with 0 failures/errors and 1 expected skipped live-integration harness; the final combined command completed all 40 tasks successfully. Android and iOS simulator targets compile. |
| Frontend Room/chat-v2 focused tests | **Passed.** Scoped persistence, API/repository, isolation, account/room switching, outbox/reconciliation, and socket tests pass. The forced snapshot-to-subscribe handoff race passed 20/20 reruns with cursors `[0,1]`, final checkpoint `2`, and message `77`. |
| Backend `.venv/bin/python tools/workspace.py check` and `migrations` | **Passed:** no system issues and no migration drift. |
| Backend `.venv/bin/python tools/workspace.py test` | **Passed:** 213 tests, 17 PostgreSQL-only skips on the isolated SQLite suite. |
| Backend `.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin` | **Passed:** 213 tests, 0 skips on disposable PostgreSQL, including migration and concurrency/race coverage. |
| `python3 tools/workspace.py verify all` | **Passed:** synchronized contract fixture, theme, frontend unit/Android/iOS compile, backend contract, system, and 213-test isolated suite checks. |
| Paired API/contract fixtures and live KMP–Django integration | **Contract passed:** digest `32c62aa7d325b030e92c25fe569a2cc61024bc86270de983c30fd678d6520934`. **Live paired integration not run;** its test remains the one expected environment-dependent skip. |
| Android/iOS cross-platform E2EE and two-user workflow | **Blocked** by the unavailable protocol adapters and incomplete integration. |
| Attachment/voice interoperability and lifecycle | **Blocked** by the undecided encrypted attachment format and absent platform implementation. |
| Rendered comparison to Figma on Android and iOS | **Not run.** No simulator/device session was available, and full property/context inspection was limited by the Figma Starter plan. No pixel-accuracy claim is made. |
| Performance/load/reconnect measurements | **Not measured**. |
| Security/privacy/license review | **Internal adversarial review completed:** no remaining regression found in the enabled foundation paths after fixes. **Independent external protocol/privacy/license review not performed** and remains required before production E2EE enablement. |
| `git diff --check` | **Passed** in both repositories. |
| Commit/push/deployment | Exact repository SHAs and push outcomes belong in the final handoff after the commits exist. A successful push is not a deployment. |

Final delivery must add the exact committed SHAs and independent remote push
results. It must continue to distinguish compilation from live integration,
simulator/device verification, internal review, external security review, and
production deployment.
