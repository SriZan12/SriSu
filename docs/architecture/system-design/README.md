# SriSu system design and implementation status

**Current status:** proposal 0.1 is partially implemented by the user's later
core-architecture request. Read [the actual core implementation](08-core-implementation.md)
and [verification evidence](09-core-validation.md) before using the proposed target.
The original design task was design-only; the broader phase decisions below remain
proposals. Local implementation is on `dev-core-architecture` in both repositories
and has not been committed, pushed, merged or deployed.

## Recommendation

Keep Django as a modular monolith and KMP as a shared, feature-oriented client.
Keep PostgreSQL authoritative, Redis disposable, and private media behind an
authorization boundary. Improve the existing services and screens in small
backend–mobile slices. Add durable client pending operations and server recovery
records where losing or duplicating an action matters. Avoid a wholesale rewrite.

The private couple experience is the core product. Authentication without a linked
partner is valid onboarding. Existing singles-discovery terminology and routes
are legacy behavior to retire deliberately, not a target product direction.

## Read in this order

1. [Current system and evidence](01-current-system.md): inspected paths, preserved
   work, actual capabilities and limitations.
2. [Target architecture](02-target-architecture.md): ownership, data, runtime,
   client behavior, workload assumptions, and operating model.
3. [Contracts and critical sequences](03-contracts.md): existing versus proposed
   contracts, refresh, linking, chat recovery, reveal, and expiry.
4. [Security and privacy](04-security-privacy.md): threats, authorization matrix,
   sessions, media, unlinking, and retention decisions.
5. [Migration roadmap](05-migration-roadmap.md): vertical slices, rollout,
   compatibility, rollback, and the first Authentication handoff.
6. [Decision records](06-decisions.md): alternatives and reasons.
7. [Validation and traceability](07-validation-traceability.md): actual baseline
   results, future gates, requirement coverage, and design-state coverage.

This directory is the canonical design. The backend's `docs/system-design.md`
is a versioned pointer, not another editable copy. Design version `0.1` describes
the following starting commits plus the pre-existing workspace setup files:

| Repository | Starting branch | Starting commit | Local design branch created |
| --- | --- | --- | --- |
| `SriZan12/SriSu` | `dev-new-theme-couple-profile` | `8af5deb9c9a246504bd1b3b4776122505687feb9` | `refactor/srisu-system-design` |
| `SrizanKhadka/SriSu` | `codex/workspace-integration` | `1a2df95c5aece1d110408bf90aa9ed6f9970da18` | `refactor/srisu-system-design` |

Both branches were created from their selected local HEAD without stashing,
resetting, pulling, committing, or changing the baseline. Matching names do not
create an atomic release. The integration targets remain `dev-new-theme` and
`dev`. This design has not been committed or published; the earlier workspace
setup PRs are separate work.

## Approval decisions

Recommendations below are proposals, not established policy. Approval of a phase
does not silently approve every later product decision.

| Decision | Recommended starting point | Consequence / approval gate |
| --- | --- | --- |
| OTP provider | Harden existing Twilio Messaging flow first; evaluate managed Twilio Verify with destination pricing and availability | B1 can retain the current provider; switching provider needs explicit approval |
| Sessions and old clients | Five-minute access tokens, 30-day maximum device session, rotating refresh; migrate capable clients before enforcement | B2 requires an old-client reauthentication/minimum-version policy; ten-year issued tokens do not expire merely by changing settings |
| Couple unlinking | Stop shared access and new joint operations immediately; never transfer old content to a new partner | B3 requires a decision on each former partner's archive/export/deletion rights |
| Moment expiry and retention | Preserve the existing 24-hour visibility behavior until policy is approved; public lifetime stays immutable | Current code also expires private Moments and schedules purge 30 days later. Private archives and backup retention require explicit decisions before D3 |
| Publishing partner-contributed content | Separate explicit public sharing from private participation; creator cannot silently publish the partner's private answers | Consent model and withdrawal rights before public Social rollout |
| Sparks | Both accept a session; editable own answer until joint reveal; revealed answers immutable, with correction/withdrawal policy to decide | D1 requires reveal/withdrawal consent and unlinking policy |
| Challenges | Both accept; each confirms their contribution; completion only after both confirmations | D2 requires confirmation/cancellation semantics and any sharing consent |
| Encryption | TLS and storage encryption with trusted server access for this migration; no E2EE claim | If E2EE is a launch requirement, stop and redesign chat keys, devices, recovery, search and moderation before C1 |
| Audience and safety | “Public” means eligible authenticated community users; private remains original current members; add reporting and block enforcement | Decide moderation owner, target ages/regions, and abuse response before public launch |
| Scale and spending | Start with the labeled launch scenario and cost envelope in the target design | Confirm actual users, deployment region, monthly budget, retention and SMS markets before capacity commitments |

## First implementation handoff

After approval, begin A1/B1: a narrow OTP request/verification slice preserving
the existing API and onboarding screens. Establish a single environment origin,
redacted diagnostics and cancellation-safe error handling, then make verification
one-time and atomic, bound attempts, and stop request-time changes to an already
verified identity. Add isolated backend and KMP regression tests before changing
session lifetimes. See [the exact handoff](05-migration-roadmap.md#first-implementation-request-after-approval).

That was the stopping point for the original design task. The subsequent user
request authorized the bounded core implementation documented in chapter 08.
Broader Authentication, Chat durability and Social phases still need their own scope decisions.
