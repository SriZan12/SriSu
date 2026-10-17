# Couple Chat — Matrix cutover record

Record updated: 2026-10-17. This is the frontend-owned implementation and release
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

## Decision and ownership

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

The first releasable slice is intentionally narrow: private encrypted text,
timeline synchronization, durable retry/catch-up for sends already accepted into
Trixnity's outbox, truthful offline/error state, current-relationship
authorization, and logout/account-switch isolation. The composer is disabled
without a current secure room observation and `SyncState.RUNNING`; this slice does
not promise creation of new messages while offline.

| Figma/product area | Matrix-era ownership | Status for the first release |
| --- | --- | --- |
| Empty conversation and text thread | Trixnity timeline backed by the mapped encrypted Matrix room | Implemented and compile/unit verified; live two-device Synapse verification remains |
| Send/retry/offline recovery | One Matrix transaction ID and Trixnity's gated durable outbox; never a parallel Django message operation | Implemented for retry/catch-up of already queued sends; composer is disabled while offline, and live restart verification remains |
| Delivery/read state | Matrix receipt semantics exposed only when tested across both clients | Required only if the UI can report it truthfully; otherwise hide it |
| Reply, edit, reaction and deletion | Standard Matrix relations/redactions plus SriSu product policy and current-membership gating | Deferred until each action is implemented and tested; controls stay absent |
| Photo/video and viewer | Trixnity/Matrix encrypted media with bounded cache and current-room access | Unimplemented and disabled |
| Voice recorder/playback | Platform recorder/player, interoperable codec and encrypted Matrix media | Unimplemented and disabled; the existing inert mic is not a feature |
| Spark | Separate versioned SriSu domain plus an encrypted Matrix reference/event | Unimplemented and disabled |
| Ask/free response and polls | Separate versioned SriSu domain plus encrypted display content | Unimplemented and disabled |
| Planner/Plan cards | `CouplePlanModel` remains domain authority; Matrix may carry an encrypted typed reference | Basic Plan data exists outside chat; rich type/location/reminder/checklist/card flow is unimplemented |
| Story prompt cards | Couple Profile remains domain authority; a future Matrix card needs an encrypted typed reference | Unimplemented on Matrix; the retired legacy message side effect is not a fallback |
| Conversation options/nickname | Future typed settings policy; not the legacy `ChatRoom.settings` JSON | Unimplemented and disabled |
| Push notifications and voice/video calls | Separate future systems | Out of scope |

Unknown Matrix event or content versions must render as inert/update-required
items rather than crash or grant an action. A Figma control is not enabled until
its domain authority, encrypted representation, failure behavior, accessibility,
and Android/iOS implementation are complete.

The current text-only UI intentionally displays the newest 100 events and does
not yet expose backwards pagination. This is a documented first-slice limitation,
not a claim that older Matrix history is deleted; pagination and scroll-position
retention require a separate UI/runtime verification pass.

## Bootstrap and client lifecycle

The retained frontend route remains `ChatNav.ChatScreen(roomId: String)`, where
`roomId` is the Django `ChatRoom` UUID. Opening it performs this sequence:

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

## Local persistence

Trixnity Room is the only chat projection/outbox/crypto store. The custom
`ChatDatabase`, `ChatV2Repository`, legacy message client and socket runtime have
been removed; they are not a layer beneath Trixnity.

The old database contains `chat_v2_conversation`, `chat_v2_message`,
`chat_v2_checkpoint`, `chat_v2_outbox`, `chat_v2_tombstone`, `chat_v2_reaction`,
`chat_v2_receipt`, and `chat_v2_attachment_transfer`. It lives at:

- Android: `<noBackupFilesDir>/srisu-private-chat-v2.db`;
- iOS: `<Application Support>/SriSuChatV2/srisu-private-chat-v2.db`.

At platform-store initialization an idempotent versioned cleanup checks and
removes the exact database plus `-wal`, `-shm`, and `-journal` sidecars even when
an independently restored preference marker already exists. It never removes the
catalogue database, a Matrix store or a broad application-support directory.
Room/KSP/SQLite remain project dependencies because `CatalogueDatabase` and
Trixnity use them.

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
- the custom mobile ChatV2 database described above.

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

`ChatNav.ChatScreen(roomId: String)` is now wired only to the Matrix repository.
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
sidecars listed above only after the Matrix store has a different validated path
and the old database is closed.

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

The KMP unit suite and Android/iOS compile targets are the local acceptance checks
for this cutover; see the task handoff for their exact final output. Contract
fixtures are synchronized from the backend `core-1` manifest. No physical-device
run, live two-user Synapse exchange, production data migration, deployment,
independent security review, commit or push is claimed here.
