# Current system and design evidence

**PROPOSED design input, version 0.1 — 2026-09-26.** This is a bounded architecture
investigation, not a line-by-line code review or exhaustive audit. “Confirmed”
means visible in the inspected code/configuration; it does not mean reproduced
against a deployed service. No production data or credentials were used.

## Workspace and preservation

Frontend root: `/Users/srijan/studioprojects/SriSu`. Backend root:
`/Users/srijan/studioprojects/SriSu-backend`. Origins were checked independently;
the similarly named repositories belong to different owners. Both selected HEADs
match the live integration refs read with `status --remote` on this date. Exact
SHAs and starting branches are in [the index](README.md).

Pre-existing frontend changes: `.gitignore`, `README.md`, `.github/`, `AGENTS.md`,
`SriSu.code-workspace`, `docs/integration/`, `docs/project-context.md`,
`tools/codex-workspace.sh`, `tools/workspace.py`. Pre-existing backend changes:
`social/test_moments.py`, `.github/`, `AGENTS.md`, `README.md`, `docs/workspace.md`,
`srisu/workspace_test_settings.py`, and `tools/`. These are earlier workspace work,
including a test-only Http404 adaptation; they are not modernization changes.
File fingerprints were saved before branching so later verification can detect
accidental edits. New work in this run is documentation only.

Frontend recent history includes partner-linking and theme work. The backend
clone is shallow and exposes one local commit, a merge of couple recommendations;
its full historical evolution was not reconstructed. Remote publication commits
from the previous task were not silently made the baseline.

## Technology actually present

| Area | Observed implementation | Architectural implication |
| --- | --- | --- |
| Backend runtime | Python 3.13.5 in Pipfile/lock; Django 6.0.3; DRF 3.17.1 | Retain framework and user-model migration history |
| Authentication | Twilio 9.10.4 `client.messages.create`; custom `OtpModel`; Simple JWT 5.5.1 | Twilio currently transports SMS; application owns OTP correctness. Managed Verify is not in use |
| Database | PostgreSQL settings; development Compose uses PostgreSQL 17 | PostgreSQL is the concurrency-test target; SQLite cannot establish row-lock guarantees |
| Realtime | Channels 4.3.2, channels-redis 4.3.0, Daphne 4.2.1 | Reuse adapters and services; delivery/recovery needs an explicit contract |
| Cache | Default process-local cache; named Redis `couple_feed` cache; Redis client 7.4.0 | Throttles using the default cache are not a global distributed limiter |
| Media / jobs | Django filesystem storage; Pillow 12.1.1; `MomentFileDeletion`; `cleanup_moments` management command | No verified object-store, scheduled worker, push delivery, or production scheduler deployment |
| Deployment files | Dockerfile starts Daphne; Compose overrides it with development `runserver`, source mounts and exposed development ports | These files are evidence of a development topology, not of the live deployment |
| Mobile | Kotlin 2.2.0, Compose Multiplatform 1.8.2, AGP 8.12.2; Android min 24 / compile and target 35 | Keep current versions for design; upgrades need a separate compatibility reason |
| Client libraries | Ktor 3.2.3, coroutines 1.10.2, serialization 1.9.0, Koin 4.1.0, navigation 2.8.0-alpha13, Coil 3.3.0 | Existing networking/DI/navigation seams can be improved without a framework replacement |
| Local storage | KVault 1.12.0; DataStore 1.1.7 for OTP metadata; in-memory chat maps | No durable message/pending-operation database was found in the inspected source/dependencies |
| Source sets | Common Compose and feature code; Android and iOS storage/network/media implementations; Android, iosX64, iosArm64, iosSimulatorArm64 targets | Preserve platform separation; compilation does not prove device lifecycle behavior |
| Contracts | Handwritten DRF serializers, socket presenters and Kotlin DTOs; no OpenAPI generator/schema or realtime schema publication found | Formalize the existing contract before changing its shape |

Evidence: backend `Pipfile.lock`, `srisu/settings.py`, `srisu/asgi.py`, `Dockerfile`,
`docker-compose.yaml`; frontend `gradle/libs.versions.toml`,
`composeApp/build.gradle.kts`, `core/session/`, platform `session/`, `di/`.
Frontend paths below are relative to
`composeApp/src/commonMain/kotlin/com/srisu/srisu/` unless stated otherwise.

## Representative flows inspected

| Flow | Actual chain | Preserve / design gap |
| --- | --- | --- |
| OTP → session | Auth screens → `AuthViewModel.verifyOtp` → `AuthRepository` → `AuthApiService` → `POST api/auth/verify-otp/` → `VerifyOtpSerializer` → `OtpModel` update and `UserModel.update_or_create` → Simple JWT → `OtpVerificationResponse` → serialized `Session` in KVault → navigation callbacks | Existing response and UI are reusable; proof consumption and session lifecycle need explicit concurrency rules |
| Profile → linking | Profile setup → existing auth service; partner screen → `FindPartnerViewModel` → chat API methods → `find_partner` / `CoupleConnectionView` → `create_or_get_couple_for_connection` → memberships and `sync_chat_room` → invitation/connected state | Preserve tested stale-search cancellation and pagination. Couple membership belongs to relationships, not authentication or chat |
| Text send | Chat composable action → `ChatViewModel.sendTextMessage` → `ChatRepository` → `ChatWebSocketClient` → `ChatConsumer.receive` → `ChatSocketHandlerMixin._handle_send_message` → `send_message` transaction → `MessageModel` and room update → broadcasts and command response → Kotlin event → room cache → ViewModel state → Compose | Persistence exists; no operation deduplication or recovery stream contract |
| History / room change | ViewModel selects room → repository sets active room and clears its map → socket `fetch_messages` → user-scoped selector → `id < cursor`, descending IDs → response carries `chat_room_id` → cache for that room | Per-room cache is valuable. ViewModel also replaces selected room with first room-list entry; room selection must be independent of list reordering |
| Receipts / edits / deletion | Socket actions → service functions → database state → action/event DTOs → repository updates | Preserve supported actions; define durable receipts, revisions, per-user deletion and retry semantics |
| Moment creation | No corresponding KMP feature found → backend `CoupleMomentView._write` → serializer → `save_moment` → locks / membership / creator checks / five-photo validation → Moment and file writes → compensation on failure → response | Backend implementation is substantial; mobile integration remains work to plan |
| Moment discovery / expiry | `CoupleFeedView` → ranked distinct couples → Redis session of IDs → database eligibility recheck → previews → authenticated photo action; `expires_at` filtering independent of cleanup | Preserve expiry checks, per-user Faves and cache-as-order-only behavior |
| Sparks / Challenges | No matching implemented domain models, endpoints or feature flows found in the inspected app trees; entertainment home is a placeholder | Proposed new capabilities, not demonstrated existing behavior to refactor |

Kotlin `safeRequest` unwraps the common `data` envelope, but not every backend
endpoint uses that envelope (Moment detail is a direct object). Future feature
services must choose the correct decoder; do not globally reinterpret old APIs.

## Capability status against the product brief

| Requirement | Evidence and status |
| --- | --- |
| Individual identity and consent-based partner linking | Implemented in part: phone OTP, profile, invitations, acceptance and membership creation |
| Exactly one current couple per user | Existing membership OneToOne and couple-position uniqueness; creation service locks users in ID order. Full unlink/relink lifecycle is incomplete |
| Couples-only product | Contradicted by legacy single connections, user suggestions, age/zodiac preferences and some copy. These must not define the future onboarding model |
| Shared couple profile | Backend implemented with member-scoped operations and tests; complete KMP couple-profile feature not found in this baseline |
| Chat | Persistence, history, replies, image upload, receipts, reactions, edit/delete and reconnect loop exist; reliability is partial |
| Either partner creates a Moment; creator alone mutates | Implemented in backend `save_moment` and view authorization; tests exist |
| Maximum five photos under concurrent updates | Parent/row locking and combined retained/uploaded count exist; PostgreSQL cases skipped by current local SQLite runner |
| Immutable 24-hour public lifetime | Backend sets `created_at + 24h` on initial save; edit does not renew. It currently hides private Moments too, which is broader than the requested public rule |
| Pagination / Global / Faves | Backend implemented: legacy page-number Moments plus signed cursors and stable Redis feed sessions; one card per couple, Faves per user |
| Notes and partner contributions | Private appreciation notes/replies exist. `partner_memory` is a creator-editable Moment field, not a separately consented partner contribution |
| Sparks / Challenges / Try This Together | Proposed; no implemented flows found |
| Notifications / moderation / account recovery | Production push, reporting/moderation workflow, phone-change recovery and account-deletion orchestration not verified/found in inspected paths |

## Design risk register

Severity is a design-priority assessment. The entries describe mechanisms to
address in later approved slices; no exploit tests or formal review were done.

| ID / priority / classification | Evidence and cause | Impact and recommended mechanism | Migration risk / verification |
| --- | --- | --- | --- |
| E1 High; confirmed code behavior | `AuthViewModel.checkSession` and profile update log session/tokens; `HttpClientFactory.getBearerToken` logs access and enables body logging; Android/iOS initialize `DebugAntilog`; backend `message_service.send_message` and `ChatConsumer.broadcast_to_room` print private content | Logs can disclose credentials/content. A1 removes payload logging, restricts telemetry fields, and tests canary redaction | Existing deployed logging/collection unknown. Restrict log access and rotate/revoke exposed credentials if exposure is confirmed; do not copy values into this document |
| E2 Critical if deployed as-is; configuration risk | `srisu/settings.py:25,28,30,193` contains literal secret configuration, DEBUG true, wildcard hosts and ten-year access/refresh lifetimes; no separate JWT signing key assignment found | Source-controlled signing material must never secure a deployment. A1 environment secrets and B2 session revocation/cutover | Live configuration unverified. Rotation can invalidate every session and signed cursor; plan controlled reauthentication and purge cached credentials |
| E3 High; confirmed behavior / concurrency risk | `SendOTPAPIView.post` calls `unverify_user` before limiter/provider delivery. `VerifyOtpSerializer.validate` reads code/status, then the view separately marks expired; no failed-verification counter or atomic consume in these paths | Request can disrupt verified-state checks; simultaneous valid verifications may both succeed; guessing is insufficiently bounded. B1 identity-stable requests, secure proof storage, attempt limits, locked consumption | SMS transport is not verification authority today. Preserve old shape; test double consume, failure, resend races with PostgreSQL and a mocked provider |
| E4 High; confirmed contract gap | JWT rotation settings exist, but `authentication/urls.py` exposes no refresh/logout route; client `HttpClientFactory` uses bearer injection without refresh orchestration | Revocable device sessions and safe short token expiry require a coordinated server/client slice | Do not shorten expiry before client support. B2 tests concurrent refresh, offline logout, old-client transition |
| E5 High; authorization design gap | Chat room selectors check participant IDs; they do not check current couple membership. Socket JWT validated at connection; subscribed broadcasts do not recheck authorization | Former membership or long-lived connections may retain access. B3/C1 introduce membership epochs, current-session checks and revocation handling | No deployed unlink exploit was tested. PostgreSQL + real Channels tests must cover unlink while connected and delayed sends |
| E6 High; confirmed missing ownership mechanism | `chat.models.MediaModel` has no uploader/room; `message_service._get_media_objects` accepts matching IDs globally; presenter emits storage URLs | An attachment ID cannot establish permission. C3 adds owned uploads, room binding and guarded downloads | Legacy files need attribution/backfill or quarantine; do not grant ownership heuristically. Negative cross-user media tests and proxy checks |
| E7 High; protocol risk | `request_id` is echoed but not persisted for deduplication; broadcasts happen after commit and before command success; client `BaseWebSocketClient.send` can return when session is null | Timeout or disconnect can lose confirmation, lead to duplicate retries, or clear unsent input. C1 durable operation IDs, pending states and HTTP recovery | Existing clients lack operation IDs; compatibility adapter cannot promise their deduplication. Inject disconnect after commit |
| E8 High; confirmed state inconsistency | `ChatViewModel.observeChatRooms` selects `chatRooms.firstOrNull()` on list changes; repository active room is separate. Media optimistic matching uses first same-type local message in room | Selected header/send target can diverge from visible room; concurrent photo sends can reconcile to the wrong pending item | Keep per-room cache; C1/C2 add immutable route room and operation-ID matching. A/B reordered-response tests; UI runtime reproduction still outstanding |
| E9 Medium; confirmed persistence mismatch | `_delete_message_for_me` changes shared `delete_option` but does not create the `MessageDeletion` records used by history selectors | Locally removed item can return after a fetch; inconsistent multi-device state | C2 writes per-user tombstones and preserves delete-for-everyone. Verify delete/refetch/reconnect from two devices |
| E10 Medium; concurrency / ordering risk | Room previews/unread JSON updated by read-modify-write; room pagination uses timestamp without ID tie-breaker; message edits lack revisions; typing stored without TTL | Lost unread updates, skipped tied rows or stale mutation order are possible | C2 row serialization, ordered mutations, deterministic cursors and ephemeral typing. These are not measured performance claims |
| E11 High; privacy design gap | Authenticated `find_partner` serializes `UserModelSerializer`, including more profile data than a linking confirmation requires; no local throttle on this path | Exact-number lookup can become an enumeration/disclosure surface | B3 minimize fields, bound attempts, opaque target reference; decide acceptable pre-consent disclosure. No bulk enumeration was attempted |
| E12 Medium; product-policy mismatch | Moment `save` starts lifetime at creation; `eligible_moments` expires all visibility modes; cleanup code uses 30 days after expiry; no publish/draft state | Private retention and future draft publication cannot be inferred from public 24h requirement | D3 preserves current behavior until approval; backfill publication from original creation without extending old expiry |
| E13 Medium; bounded coverage | Auth tests placeholder; legacy chat test invokes live transport; current runner deliberately selects social suites | Passing baseline is not authentication/chat/end-to-end verification | Add isolated tests incrementally. No performance problems were measured; latency/capacity hypotheses need workload tests |

## Figma evidence and limits

Live `get_metadata` confirmed the file and top-level page `0:1` on this run.
The subsequent page-content call hit the Starter-plan MCP quota; account lookup
confirmed Starter access. No live screen, component, variable or asset inspection
was possible after that. The design does not invent screen observations.

Retain the previously documented Satoshi typography, optional Instrument Serif
headings, warm palette, pill actions, shared components, and exported assets in
`docs/design-system/README.md` and its dated `figma-spec.json`. Dark mode is derived,
not a verified Figma dark design. Partner nodes `14:220`, `5:974`, `5:1118` are
documented references; confirmation artwork has explicit placeholders. Reinspect
these and obtain the Auth/Chat/Social frame IDs when access is available.

## Investigation coverage

Read the active auth routes/models/serializers/views; active chat routing,
middleware, consumer, handlers, message/receipt/typing services, selectors and
presenters; social membership/Moment/Fave models and services, routed views,
serializers, feed ranking/pagination, cleanup and selected migrations/tests.
Read KMP build/dependency definitions, session storage, network factory/request
handler, auth APIs/ViewModel, linking documentation and implementation entry
points, chat socket/repository/ViewModel/DTOs, navigation and entertainment home.

Not exhaustive: all Compose layouts/platform media internals, every legacy
serializer/route, every migration, all historical branches, deployed reverse
proxy/storage, real accounts, device sessions, production logs and metrics.
No performance baseline, penetration test, runtime Android/iOS UX validation,
or full third-party dependency compatibility audit was performed.
