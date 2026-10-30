# Architecture decision records

**Original records: PROPOSED, design 0.1, 2026-09-26.**
The later core request implements bounded parts of ADR-001/002/005/006/007;
[chapter 08](08-core-implementation.md) records the actual differences, including
public-catalogue-only Room and snapshot recovery without durable operation IDs.
Other choices remain proposed. “Selected” means recommended
within this proposal, not user approval or implementation. The workload assumptions
and constraints are in [the architecture](02-target-architecture.md).

## ADR-001: keep a modular Django monolith and shared KMP client

**Context:** there are working services, models, migrations, theme, screens and
limited observed operational requirements. Team capacity/production scale is unknown.

**Alternatives:** full rewrite; split services/databases; reorganize every package;
incrementally strengthen existing feature boundaries. **Selected:** the last,
retaining Django/DRF/Channels/PostgreSQL and KMP/Compose/Ktor/Koin.

**Tradeoffs/cost:** one deployment couples release cadence, but makes transactions,
authorization and operations understandable for a small team. Service extraction
around complex workflows costs less than moving all ORM models. Preserve historical
table/content-type/user-model identity. Trivial CRUD does not need ceremonial layers.

**Risk/revisit:** large modules can remain tangled if boundaries are only names.
Test public operations and dependency direction. Reconsider extraction only for a
measured isolation, ownership or independent scaling bottleneck, with operational
capacity to support another service.

## ADR-002: backend-owned versioned contracts, pinned mobile artifact

**Context:** handwritten contracts already differ in envelope/ID/event semantics;
installed mobile releases cannot update atomically with the server.

**Alternatives:** let DTOs drift; duplicate edited specs in both repos; generate
everything immediately; one authoritative artifact with tested adapters.
**Selected:** backend OpenAPI 3.1 + JSON Schema 2020-12 and synthetic examples;
mobile pins immutable digest. Canonical architecture remains in frontend docs.

**Tradeoffs/cost:** schema annotation/fixture tests add maintenance, but identify
breaking changes before release. Retain handwritten DTOs initially; generated
clients are optional when their code quality and multiplatform behavior justify it.
Current docs contain only a draft subset, not a claim of complete API coverage.

**Risk/revisit:** schema can lie unless validated against responses. Backward
compatibility includes authorization, errors and semantics, not just JSON shape.
Revisit tooling after a small supported-version spike; no blind dependency upgrade.

## ADR-003: retain SMS transport for the first proof-hardening slice

**Context:** current Twilio Messaging delivers app-owned OTPs; replay/attempt and
identity-state behavior require repair. Provider country/cost constraints unknown.

**Alternatives:** hardened application proof; managed Twilio Verify; switch identity
provider/product. **Selected:** narrowly harden the current transport first;
evaluate Verify with actual destinations, pricing, compliance and failure behavior.

**Tradeoffs/cost:** retaining transport minimizes API/UX migration but SriSu keeps
proof/abuse responsibilities. Use cryptographic randomness, keyed verification,
atomic consume, bounded guesses and spend limits; do not invent crypto primitives.
Managed verification can reduce this burden but introduces provider constraints
and fees. This is not a recommendation to permanently rebuild provider features.

**Risk/revisit:** account recovery and recycled numbers remain unsolved by SMS
alone. Reconsider before wider launch or if fraud/support cost outweighs migration.
Provider switch and recovery-factor policy need explicit approval.

## ADR-004: revocable device sessions with conservative refresh recovery

**Context:** ten-year JWT configuration and absent refresh/logout endpoints cannot
support bounded access and deterministic device revocation.

**Alternatives:** long-lived bearer tokens; opaque sessions replacing JWT entirely;
short JWT access plus registry-backed rotating refresh. **Selected:** retain Simple
JWT as signing foundation; add explicit device registry/revocation and coordinated
mobile refresh. Proposed access five minutes, absolute session 30 days.

**Tradeoffs/cost:** DB/session checks and custom rotation/reuse policy add state;
they permit revocation and device management. Simple JWT settings alone do not
provide all those guarantees. Short token life is not instant revocation by itself.
One refresh in flight per device session prevents ordinary client refresh storms.

**Risk/revisit:** response lost after rotation requires login in the first design;
no blind refresh retry/reuse grace. Measure reauthentication friction before a
separately designed bounded replay mechanism. Legacy tokens need explicit cutoff
across every transport. Never reverse a compromise-driven key rotation to roll back.

## ADR-005: database durability plus recoverable realtime hints

**Context:** Redis/Channels events can be lost; code currently broadcasts after
commit before command success. Mobile needs missed message/edit/delete recovery.

**Alternatives:** trust sockets; Kafka/event sourcing; periodic full-history reload;
relational messages plus operation dedup and bounded change index. **Selected:**
relational truth, per-room sequence/revision, durable operation identity, HTTP
snapshot/changes, best-effort socket hints. Small DB durable jobs for necessary
notification/revocation effects; preserve specialized media deletion queue.

**Tradeoffs/cost:** more rows, worker supervision and cleanup rules; recovery beyond
seven proposed days needs a snapshot. This supports convergence without a second
authoritative message log. Per-room locking serializes writes, acceptable for two
participants at assumed load; measure lock time before weakening correctness.

**Risk/revisit:** at-least-once attempts can duplicate side effects unless consumers
dedupe. No exactly-once network promise. Revisit partitioning or external broker only
after measured DB/job throughput/availability problems justify operational cost.

## ADR-006: account-scoped mobile projection with explicit pending operations

**Context:** in-memory state loses pending sends; singleton token/user capture and
room selection divergence create lifecycle hazards.

**Alternatives:** keep all memory-only; ad hoc files; KMP relational store with
transactional pending/projection/checkpoint updates. **Selected:** scope all work by
account generation and membership epoch, one immutable room route, then evaluate
Room KMP for durable chat state at C1. It is a candidate, not installed/verified.

**Tradeoffs/cost:** local migrations and platform protected storage add complexity;
durable intent and checkpoint consistency make offline/retry behavior testable.
Retain KVault for credentials, never use ordinary DB columns for bearer secrets.
Keep repositories/use cases proportionate; do not rebuild auth around a chat DB.

**Risk/revisit:** Room is not automatic encryption; exact project Kotlin/Compose/
SQLite/OS compatibility needs a spike. If target support or privacy protection fails,
evaluate SQLDelight or a limited storage adapter with explicit reasons. Remote
revocation cannot erase an offline device's previously cached content immediately.

## ADR-007: preserve couple identity and add lifecycle history

**Context:** current uniqueness and ordered user locks are valuable; flags and
breakup status do not define revocation/archive/relink policy fully.

**Alternatives:** replace user/couple models; derive permission from `is_engaged`;
retain current constraints and add membership history/epochs. **Selected:** retain
model/migration identity and add explicit operations/guards/history. All feature
authorization uses current membership plus original audience where applicable.

**Tradeoffs/cost:** backfill/report inconsistent historical records and adapt older
callers. History permits old relationships to stay distinct from new ones. Do not
automatically hand old chat/Moment/Spark content to a new partner.

**Risk/revisit:** archive/export/deletion rights are product/privacy decisions,
not a schema default. Deny former-member access until policy approved. Revisit
history shape before allowing more than two members or multiple active couples;
neither is a current requirement.

## ADR-008: guarded media and explicit visibility versus retention

**Context:** Moments already have authenticated expiry-aware photos and deletion
jobs; chat media lacks ownership. Public Moments live 24 hours, but private lifetime
and deletion policy are unresolved.

**Alternatives:** public permanent URLs; short signed URLs; authorized proxy with
private storage. **Selected:** guarded proxy at launch, preserve Moment mechanisms,
apply owned staging to chat; evaluate signed delivery later only with accepted
revocation window. Publication time stays immutable and independent of cleanup.

**Tradeoffs/cost:** proxy delivery consumes app/network resources but supports
current permission/expiry checks. Storage expiry and physical deletion differ;
backup retention needs its own decision. Existing content must not get renewed
lifetime during publication-field backfill.

**Risk/revisit:** screenshots/downloaded bytes cannot be recalled. Revisit CDN only
when measured delivery cost/latency warrants it and policy accepts bounded residual
access. Never replace private authorization with an unguessable object key.

## ADR-009: private Social value first, affordable discovery second

**Context:** Sparks/Challenges are absent; Moments/discovery backend is substantial.
The product is couples-only and should be useful without public activity.

**Alternatives:** feed-first gamification/ML; one oversized Social domain; distinct
private participation with optional publication. **Selected:** curated versioned
templates in Django admin, async Sparks, small Challenges, existing Moment/Fave
mechanisms, rules-based ranking and narrow approved-template reuse.

**Tradeoffs/cost:** editorial effort and explicit consent/state machines instead
of generation/ranking infrastructure. Keep original template versions for sessions.
Do not train on private answers or reward posting through coercive streaks.

**Risk/revisit:** reveal, withdrawal, completion, moderation and audience decisions
need approval. Reconsider ranking complexity only after measured content supply,
engagement quality and diversity expose a specific shortcoming.

## ADR-010: trusted-server encryption model pending explicit approval

**Context:** current server processes messages/media; no verified E2EE mechanism.

**Alternatives:** TLS/storage encryption with server access; E2EE using an established
protocol and supported implementation. **Selected for this migration proposal:**
trusted-server model, tightly restricted operator access and minimized storage.
This does not claim E2EE and is an explicit user approval gate before C1.

**Tradeoffs/cost:** simpler multi-device recovery, moderation and integration, but
server compromise or privileged access can expose content. E2EE changes key/device
management, backups, previews, moderation, search and recovery, so cannot be added
as a small encryption helper. Never design custom cryptography.

**Risk/revisit:** if intended users require protection from server operators, change
this decision before storing more private chat data. No UI privacy claim may exceed
the chosen threat model.
