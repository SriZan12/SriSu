# Incremental migration and implementation handoff

**PROPOSED 0.1 — no implementation authorized.** Order is A → B → C → D.
Obtain explicit approval before each major phase and any material design change.
If A and B are approved together, record that explicitly; do not infer approval
of Chat or Social. Each slice must leave both repositories runnable and unaffected
features usable. Slice labels below identify work, not estimates or promises.

## Shared release rules

1. Record selected frontend/backend commits, current schema digest and relevant
   Figma nodes. Check fresh remote integration state and preserve local work.
2. Add schema/behavior compatibly on the server; test current released fixtures.
   Deploy server capability behind a flag before shipping a dependent mobile app.
3. Release mobile support to internal users, then a small opted-in cohort. Measure
   errors, recovery, adoption and operational cost against the proposed targets.
4. Enable enforcement only after approved minimum-version/cutoff policy. Flags
   never bypass authorization to “roll back” a security correction.
5. Remove legacy paths only after supported-client usage is zero over an agreed
   window and recovery is demonstrated. Initial proposal: 30 days after the
   announced cutoff, based on sanitized protocol/version counts, not user content.

Database approach: additive fields/tables first; bounded resumable backfill;
measure locks; validate data; then constraints/indexes; remove obsolete fields in
a later release. Keep the user model and migration history. For each migration
record whether reverse migration is safe, data-destructive or unsupported.
Reverting application Git does not reverse database changes. Keep a compatible
previous app binary; use forward repair for irreversible data transformations.
Backups are disaster recovery, not a casual rollback that discards new writes.

Legacy authentication/chat risks remain during a staged migration. A phase being
“runnable” does not certify every deferred feature for public release. If deployed
exposure is confirmed, agree a narrow containment/hotfix or disable the affected
surface explicitly; do not quietly fold an emergency rewrite into design work.

Paths below are relative to each repository. Mobile feature paths are under
`composeApp/src/commonMain/kotlin/com/srisu/srisu/`; platform changes go into their
existing Android/iOS source sets.

## Phase A — only foundations required by Authentication

### A1: coherent environment and diagnostic boundary

**Purpose/preserve:** preserve current auth screens and payloads while making
HTTP/socket origin selection, redacted diagnostics, request IDs and cancellation
consistent. No generic repository rewrite, new database or module split.

**Targets/data/contract:** backend `srisu/settings.py`, settings/environment
documentation and logging call sites in auth/chat; frontend `core/data/remote/`,
`core/session/`, Ktor factory, DI and platform logging initialization. Configure
one trusted origin per environment. Move deployment secrets to environment/secret
store; fail startup if required production values are absent. No domain migration
or public payload change; establish draft contract inventory and synthetic fixtures.

**Verification:** canary credentials/phone/message text never appear in logs;
release builds reject cleartext and wrong-origin bearer forwarding; cancellation
propagates; existing isolated baseline passes. Build both platform targets and
verify environment selection on devices before declaring runtime coverage.

**Rollout/recovery/removal:** staging first; coordinate signing-key changes with
session reauthentication rather than silently invalidating everyone. Revert only
safe configuration/diagnostic changes; never restore an exposed key or payload
logging. Remove hardcoded LAN-origin fallback when all build flavors select their
environment. A exit: auth works with existing contracts, redaction proven, no
new framework dependency required. **Approval gate before B.**

## Phase B — Authentication, sessions and onboarding/linking

### B1: one-time OTP proof without identity disruption

**Purpose/preserve:** keep existing phone entry, code entry, resend and onboarding
behavior, including established response envelope. Twilio Messaging remains unless
provider migration is separately approved. No new social feature or visual redesign.

**Targets/data/contract:** backend `authentication/models.py`, `api/views.py`,
`api/serializers.py`, `urls.py`, proposed OTP operation/provider adapter and selected
isolated tests. Add challenge generation/purpose, keyed proof, expiry, attempt and
consumption state with a deliberate short-lived legacy-code transition. Existing
active plaintext OTPs may be invalidated with a resend requirement, not copied into
new permanent stores. Frontend `features/auth` APIs/repository/ViewModel and OTP
state use typed errors, bounded resend, cancellation and stable request identity.
Keep old shape; add challenge identity optionally before requiring it in v2.

**Verification:** PostgreSQL two simultaneous correct verifications yield exactly
one consumption/session-issuance transition; replay/expired/wrong-generation codes
fail; guessing is bounded; provider timeout/resend race cannot unverify existing
identity or exceed spend budget. Mock SMS; no real numbers. UI preserves input and
shows waiting/error/retry without automatic SMS loops. Old auth fixtures still decode.

**Rollout/recovery/removal:** additive schema → shadow counters/metrics → enforce
proof service in staging → small cohort → all requests. Safe rollback keeps new
proof checks; a provider outage disables new send with retry guidance. Do not
restore insecure generation/plaintext/replay behavior. Remove legacy proof fields
after all active challenges expire and old endpoint adapter uses the new service.

### B2: revocable device sessions and coordinated refresh

**Purpose/preserve:** stay signed in during ordinary access expiry, make logout and
account switching deterministic, preserve profile/onboarding navigation.

**Targets/data/contract:** backend new session registry/service and endpoints in
`authentication`, JWT auth adapter and socket authentication; additive device
family/rotation/revocation schema. Mobile `core/session`, platform KVault storage,
HTTP factory, session-scoped DI and socket token provider. Add versioned refresh,
logout/session projection and capabilities; no global reinterpretation of v1 DTOs.

**Verification:** simultaneous 401s issue one refresh; failed/ambiguous refresh
ends session without loops; logout during refresh and account switch cannot restore
credentials or state. Test reuse, disabled account, revoked REST/socket, clock skew,
absolute expiry, offline logout, reinstall and two devices. Exercise current Ktor
3.2.3 behavior explicitly; a newer documentation example is not compatibility proof.

**Rollout/recovery/removal:** session-aware backend first with explicit temporary
legacy policy → capable mobile → measured adoption → announce reauth/cutoff → rotate
or reject old credentials at every route. Separate five-minute access issuance from
enforcement of already-issued long tokens. Feature rollback may return to login
instead of refresh; it must never reinstate a revoked family. Remove legacy JWT
acceptance only after cutoff and supported-client tests. Credentials require a
server-approved path to recover; do not migrate a plaintext token through logs.

### B3: explicit onboarding and transactional couple lifecycle

**Purpose/preserve:** preserve phone-based invitation UX and existing linking/theme
tests. Awaiting acceptance is a valid authenticated state. Move relationship
ownership behind existing callers without conflating it with dating/singles mode.

**Targets/data/contract:** backend `social/api/views.py`, relationship/profile
service and existing membership models; frontend `features/chat` linking APIs,
`FindPartnerViewModel`, navigation, profile state, proposed relationship facade.
Add minimal lookup response, stable invitation operations, state revision,
membership epoch/history and consistent lock policy. Retain current OneToOne/slot
uniqueness; backfill/report inconsistencies before adding constraints. Apply active
membership checks to legacy and new chat access at the same time.

**Verification:** double/crossed invitation, accept/cancel race, already-linked
users, two concurrent accepts and unlink/write race on PostgreSQL. Unrelated-user
lookup disclosure/limits, former-partner REST/media/socket access, epoch-invalid
offline queue, no content inherited by a new partner. Retain existing stale-search
and pagination behavior. Approved archive/consent choices are prerequisites to
shipping unlink UI; secure denial can precede archive functionality.

**Rollout/recovery/removal:** services under old route adapter first → minimal lookup
and session projection → mobile navigation → approved unlink flow. New history is
retained on rollback; do not reconstruct a relationship from old flags. Remove
legacy `is_engaged`/route authority only after callers use membership truth; keep
derived compatibility fields temporarily. B exit: identity, session and couple
boundary proven across HTTP/socket, user can finish onboarding. **Approval before C.**

## Phase C — Chat

ADR 003 and the [Couple Chat cutover record](../../flows/couple-chat.md)
supersede the earlier custom ChatV2/libsignal plan. Complete these slices in order.
Never run the two message transports as co-authoritative writers.

### C1: private Matrix control plane and infrastructure

**Purpose/preserve:** retain users, device sessions, invitations, relationships,
profiles and the existing Django `ChatRoom` UUID as the stable conversation/
navigation identity. Add a deterministic mapping to one new encrypted Matrix room;
do not move relationship authorization into Matrix.

**Targets/data/contract:** private Synapse 1.162.0 with separate PostgreSQL/media,
TLS, closed registration/federation, backup and secret rotation; additive Django
Matrix user/room mappings; current-membership bootstrap; idempotent provision/
revoke work and reconciliation. `POST /api/chat/v2/rooms/{uuid}/matrix/session/`
returns the mapping and a short-lived Matrix login credential only after the
Django session and current relationship are checked. It never proxies or stores
message content, device keys or Matrix access tokens.

**Verification:** deterministic provision retry, encrypted-before-invite room
state, outsider/former-member concealment, expired/revoked session, acceptance/
unlink races, delayed Synapse recovery, immediate bootstrap denial on unlink,
relink isolation, secret-free logs, backup and timed isolated restore. Reconcile
every eligible relationship to an active or intentionally revoked mapping before
mobile rollout.

**Rollout/recovery:** deploy Synapse and additive backend control-plane changes
first, migrate, then reconcile. Do not delete legacy data or disable legacy reads
in C1. If the control plane fails, disable Matrix entry and repair mappings; never
route a Matrix client to the old plaintext transport as a fallback.

### C2: Trixnity encrypted-text client

**Purpose/preserve:** keep the stable `ChatNav.CoupleConversation` contract and
existing Figma visual references while replacing both legacy Django messages and custom
`ChatV2Repository`/libsignal envelopes with Trixnity Matrix state.

**Targets/data/contract:** Trixnity 5.9.2 `trixnity-client`,
`trixnity-client-cryptodriver-vodozemac`,
`trixnity-client-repository-room` and `trixnity-client-media-okio`. Resolve its
Kotlin 2.3.21/KSP 2.3.9/Room 2.8.4 requirements against the app's selected
toolchain and make an explicit `iosX64` support decision. Trixnity Room is the
only timeline/outbox/crypto store; its app-private, backup-excluded,
protected-at-rest design must be reviewed before release.

The first client supports encrypted text only and has no plaintext fallback.
Replies, edits, reactions, deletion/receipts UI, photo/video, voice, Sparks,
Ask/polls, rich Planner/story cards, nickname/options, push and calls remain
unimplemented and disabled.

**Verification:** Android→iOS and iOS→Android encrypted text, stable-device login,
room/user identity verification, exact transaction retry after lost ack, restart/
offline catch-up, pagination, tamper/decryption and lost-key states, unknown-event
handling, account/room isolation, logout/account-switch wipe and unlink during
queued work. Exercise supported Android and iPhone devices against disposable
Synapse; compilation and old ChatV2 tests are not Matrix integration evidence.

**Rollout/recovery:** ship only after C1 is healthy and the minimum supported client
policy is ready. Observe redacted setup/sync/revocation signals. Before destructive
cutoff, rollback may disable Matrix entry and return to the matching supported
legacy deployment; content is never copied between transports.

### C3: cutoff and authorized destructive retirement

**Purpose/preserve:** the product owner authorized destruction of legacy chat data
on 2026-10-08, with no history migration into Matrix. Destruction is limited to
legacy/custom-v2 message transport. Preserve authentication, device sessions,
invitations, couples, membership history, profiles, Plans, Moments, non-chat media,
Django `ChatRoom` identities, Matrix mappings/data, migration history and the
backend checkout's unrelated `.DS_Store`.

**Prerequisites:** enforce the minimum Matrix-only client/cutoff policy; confirm no
released supported client needs old history; replace non-message relationship
signals that still share the legacy socket; disable legacy writes; drain or account
for in-flight work; then stop legacy chat outbox/media-maintenance jobs and retire
legacy chat HTTP/socket entry. Never remove the generic WebSocket infrastructure
while Find Partner or relationship-change signaling still uses it.

Take an encrypted, access-controlled, expiring rollback snapshot before deletion:
exact application/config versions, database dump and migrations, protected/legacy
table counts, media key-size-digest manifest, Matrix mappings, and Synapse database,
media and configuration. Restore-test the complete matching bundle in isolation.

**Allowlisted removal:** purge only obsolete message/media/reaction/deletion/
operation/change/outbox/receipt/visibility/staged-attachment tables and join rows;
objects under `chats/media/`, `messages/media/`, `chat_encrypted/` and historical
`chats_media/`; and the exact per-platform
`srisu-private-chat-v2.db` plus its `-wal`, `-shm` and `-journal` sidecars. The
mobile cutover also removes only `<noBackupFilesDir>/couple-chat-v1`, AndroidKeyStore
aliases matching `com.srisu.couplechat.v1.<positive-account-id>`,
`<Application Support>/couple-chat-v1`, generic-password Keychain items with the
exact service `com.srisu.couplechat.v1.protocol-store`, and iOS temporary folders
`SriSuChatExports` and `SriSuChatVideo`. Do not
use broad media deletion, database `CASCADE` or application-support directory
cleanup. Add forward purge/drop migrations; never rewrite the applied migration
chain that the retained Matrix mapping migration historically depends on. Remove
legacy code/contracts/dependencies only after data verification.

**Verification/rollback:** rehearse with sanitized production-like shape; compare
pre/post allowlisted counts and protected-data checksums; prove Matrix text still
works, former-member denial still holds and `.DS_Store` is unchanged. Before the
purge, rollback disables one transport before enabling the other. After the purge,
rollback requires the restore-tested database, media, application images and
supported client as one bundle; a partial rollback or dual-writer mode is forbidden.

### C4: optional rich chat capabilities

Define and approve each Matrix event/content contract, domain authority, retention,
failure UI and cross-platform test plan separately before enabling it. Matrix text
release does not imply media, voice, replies, edits, reactions, deletion, receipts,
rich cards, Sparks, Ask/polls, Planner enhancements or conversation options.
C exit requires all C1–C3 release gates and no critical unresolved privacy or
restore regression. **Approval before D.**

## Phase D — Sparks, then Challenges, then Moments/discovery

### D1: curated asynchronous Sparks

**Purpose/preserve:** implement the absent private interaction feature using shared
session/membership/media foundations without changing existing chat or Moment APIs.

**Targets/data/contract:** new `sparks` models/services/selectors/serializers/admin,
KMP `features/sparks` and navigation; versioned template/session/response APIs.
Add template versions, original participant membership IDs, response uniqueness,
session revision/reveal state and operation results. Start with one text interaction
type, both accept, no midnight expiry/streak/AI generation. Figma frame/state map
and reveal/withdrawal policy must be approved before UI implementation.

**Verification:** hidden partner answers absent from HTTP/socket/export/logs,
simultaneous submissions reveal once, duplicate submit/completion, edit/reveal race,
pause/skip/unlink, immutable template version and asynchronous return after days.
Use controlled clocks and field-level schema assertions.

**Rollout/recovery/removal:** schema/templates unpublished → internal flow → feature
flag cohort. Disable starting new sessions on rollback while preserving authorized
existing sessions and responses. Do not bulk delete sensitive data to undo a release.
Remove experimental interaction adapter after the chosen contract is stable; old
template versions remain for existing sessions. Check D1 acceptance before D2.

### D2: practical private Challenges

**Purpose/preserve:** add practical shared activities independent of public feed
engagement. Reuse membership policy and curated admin, not a giant Social ViewModel.

**Targets/data/contract:** new `challenges` app/operations and KMP feature; template,
instance, invitation, contribution and completion schema. API operations carry
revision/idempotency; time required, together/apart and cost are template metadata.
Explicit both-partner acceptance/completion and cancellation policy need approval.

**Verification:** accept/decline/cancel races, duplicate contribution/completion,
one completion side effect, template withdrawal, unlink, permission matrix, loading/
empty/failure/retry and private data absent from public serializers. No photo proof
or leaderboard. All earlier Auth/Chat/Spark critical journeys still pass.

**Rollout/recovery/removal:** publish curated templates after internal tests; flag
new instance creation separately from existing access. Forward-repair bad metadata
without changing pinned content semantics. Disable initiation on rollback, preserve
records. Remove pilot aliases when stable; check D2 before D3.

### D3: integrate and harden existing Moments

**Purpose/preserve:** connect KMP to the substantial existing backend, retaining
creator-only writes, five-photo enforcement, audience snapshots and fixed expiry.

**Targets/data/contract:** `social/models.py`, `moment_service`, serializers/views,
photo handler/cleanup; new KMP `features/moments` DTO/repository/ViewModels using
endpoint-specific decoders. Add staged uploads/draft publication only if approved;
backfill `published_at` from original creation and preserve existing `expires_at`.
Do not silently turn expired private content into an archive. Consent, private
retention, deletion/backup lifetime and partner contribution rules are D3 gates.

**Verification:** creator vs partner vs unrelated writes, simultaneous uploads
cannot exceed five, staging/commit failure compensation, exact expiry on detail/
feed/media/notes, stale cache and delayed worker, immutable lifetime on edit/new
Moment. PostgreSQL concurrency cases must run, not skip. Device image order,
accessibility, photo permission denial and process interruption tested.

**Rollout/recovery/removal:** existing contract fixtures → additive backend → mobile
internal integration → approved publication capability. New and old clients share
the same invariants. Rollback disables drafts/new publication if needed; existing
expiry and guarded reads persist. Remove legacy upload adapter after adoption and
orphan/deletion queues drain under the approved retention policy.

### D4: discovery, Faves and optional Try This Together

**Purpose/preserve:** integrate existing affordable ranking, stable paginated feed
sessions, per-user Faves and distinct Global/Faves, then add the narrow template link.

**Targets/data/contract:** existing feed/ranking/Fave services and KMP discovery
feature; optional Moment-to-approved-template reference and idempotent private
Challenge proposal operation. Add seen/hide/block preferences only with minimized
retention. Figma and moderation/reporting decisions precede public feature enablement.

**Verification:** empty Faves never silently becomes Global; one card/couple;
low-content fallback never expands audience; expiry/block on cached pages; 409/410
cursor recovery; private notes/answers absent; Try repeated once, expired source
photos inaccessible, withdrawn template cannot create new activity. Measure feed
latency/diversity and budget with synthetic launch load before tuning weights.

**Rollout/recovery/removal:** discovery first behind flag → reporting/block operations
ready → optional template reference cohort. On cache outage use bounded live first
page/restart behavior already designed; disable initiation/ranking experiment on
rollback without removing personal Faves. Remove pilot flags when metrics and
policy are accepted; never fold Faves into a couple-shared preference by migration.

## First implementation request, after approval

Approve A1 and/or B1 explicitly. Before starting, confirm deployed environment and
potential credential exposure, OTP destination/spend limits, continued Twilio
Messaging versus Verify, and how users handle the short proof cutover. Live Figma
access is needed if changing screens; initial behavior-preserving hardening can
keep the existing visual structure and record the current design access limit.

Deliver a small paired backend/mobile change: stable identity during OTP requests,
secure bounded proof, atomic consume, sanitized diagnostics, cancellation-safe
client errors and existing contract compatibility. No refresh-lifetime cutover,
database-wide refactor, new Chat feature or public Social behavior in that slice.

Required checks: current workspace baseline; new isolated OTP/API tests; disposable
PostgreSQL concurrency tests; schema/old fixture compatibility; KMP auth state/
cancellation tests; Android and iOS target builds; device OTP success/failure/resend
journey with synthetic provider; log-redaction check. Report missing device/Figma/
provider coverage separately. No commit, push, migration to an existing database,
or deployment is implied by this design handoff.
