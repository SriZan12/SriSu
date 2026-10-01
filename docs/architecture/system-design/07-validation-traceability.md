# Validation, traceability and design coverage

The later core implementation has a separate [actual validation record](09-core-validation.md).
This document retains the original design-stage baseline and future acceptance requirements.

**PROPOSED 0.1.** Baseline observations below occurred during this design task on
2026-09-26. Future acceptance tests are requirements, not results already achieved.
No production database, real SMS, private account or deployed backend was tested.

## Baseline actually run

Environment: macOS 27.0, build 26A428; backend existing Python 3.13.5 virtual
environment and locked dependencies; frontend existing Gradle/JBR environment
resolved by workspace helper. No application dependency upgrades. Baselines and
preserved dirty files are recorded in [current state](01-current-system.md).

| Check | Status / observed result | What it does not establish |
| --- | --- | --- |
| `python3 tools/workspace.py status --remote` | Passed: independent origins, branches/HEADs and live integration refs identified | Local published-workspace branch state is not silently synchronized with remote PR head |
| `python3 tools/workspace.py verify all` | Passed, exit 0 | Aggregates only the checks selected by the workspace helper |
| Theme check, via helper | Passed: 96 colors and 24 contrast pairs; minimum ratio about 4.75, above 4.5 | Full accessibility, dynamic type, visual fidelity and actual Figma state not established |
| `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 --console=plain` via helper | BUILD SUCCESSFUL in 4 s, 31 actionable tasks all UP-TO-DATE | Cached verification, not fresh test execution or a newly compiled iOS binary; no runtime Android/iOS claim |
| Backend `tools/workspace.py check` via existing `.venv` | Passed: system check, no issues | Does not inspect production security configuration |
| Backend `tools/workspace.py test` | Passed: 77 discovered, 72 passed, five PostgreSQL cases skipped; 2.215 s, isolated test DB destroyed | SQLite run does not prove row locks/concurrency; intentionally excludes legacy live-network chat test and incomplete auth coverage |
| Backend `.venv/bin/python tools/workspace.py migrations` | Passed: no model/migration changes detected | No migration was applied to an existing database; future data backfills need separate validation |
| Backend `.venv/bin/python -m pip check` | Passed: no broken installed requirements | Not a vulnerability audit or dependency support guarantee |
| `xcodebuild -version` | Blocked: selected developer directory is CommandLineTools, not full Xcode | Full iOS build/link/simulator/device workflow unavailable in this configuration |
| Live Figma metadata | Partial: file and top-level Design System page confirmed; deeper read blocked by Starter MCP quota | No new live screen/token/component inspection or visual approval |
| Production configuration, live HTTP/socket, real SMS/push, Android/iOS UI | Not run | Must be explicitly tested in authorized staging/device environments |
| Performance/load, restore drill, security scan/penetration test | Not run | All latency/capacity/recovery targets remain proposed |

The backend run intentionally exercises failure handling: simulated storage
`OSError: offline` and unavailable feed cache appear in output while assertions
pass. They are not unhandled production incidents or newly introduced failures.
The earlier uncommitted test-only Http404 adaptation remains part of the starting
workspace, not a change made during this task.

Local baseline log: `/private/tmp/srisu-system-design-baseline-checks.log`.
Preservation fingerprints: `/private/tmp/srisu-system-design-baseline.json`.
These are ephemeral local evidence, not cloud artifacts or a durable shared test
report. Design-document checks are recorded below after completion.

## Verification layers for implementation

| Layer | Scope and environment | Gate |
| --- | --- | --- |
| Backend operation/API | Synthetic users/couples, controlled clock, mocked provider/storage failures; explicit selected suites | Actor/action matrix, state transitions, response schemas and side-effect behavior |
| PostgreSQL constraints/concurrency | Disposable PostgreSQL of deployed major version; transactions and independent connections, barriers rather than timing sleeps | OTP double consume, session rotation, invitation race, message dedup, reveal/completion and five-photo locks actually execute |
| Realtime integration | Real ASGI/Channels with disposable Redis plus deterministic in-process tests | Auth expiry/revocation, lost event, backpressure, unknown frame, disconnect-after-commit, replay convergence |
| KMP shared | Repository/ViewModel reducers, controlled dispatcher/clock, synthetic DTO/local DB adapters | Account/room isolation, cancellation, pending state, retries and idempotent merges |
| Local DB/platform boundaries | Real chosen database on Android/iOS; KVault, file protection, notification and media permissions | Atomic projection/checkpoint write, process restart, backup behavior and logout cleanup |
| Contract compatibility | Released v1 fixtures, proposed v2 schemas, N and N-1 supported clients/server versions | Additive fields tolerated, unknown enums safe, old request semantics preserved, breaking changes gated |
| End-to-end | Two users, two couples, multiple devices; authorized staging and mocked billable providers where possible | OTP → profile → invite → accept → chat → private Social; unrelated/expired/revoked attempts fail |
| Device UX/accessibility | Android reference mid-range device and supported oldest test iPhone, release builds | Dynamic type, screen reader labels/focus, contrast, reduced motion, photo permissions, background/resume and touch targets |
| Operations | Synthetic load, fault injection and isolated restore | Stated latency/recovery/cost envelope measured; no secrets in logs; backup restore plus deletion/revocation ledger proven |

Concurrency tests must fail if locking/constraints are removed; avoid tests that
only restate implementation. Keep clocks injectable and fixtures deterministic.
Do not use production accounts/phone numbers or send real OTPs in CI. Never run
unrestricted backend test discovery while legacy `chat/tests.py` opens transport
on import; replace/isolate that test only during approved implementation.

## Critical requirement traceability

Contract names below refer to proposals in [contracts](03-contracts.md), unless
explicitly marked existing. Failure behavior is part of acceptance, not an optional
UI polish task.

| Requirement | Owner / invariant | Contract / failure behavior | Required test | Slice |
| --- | --- | --- | --- | --- |
| No diagnostic disclosure | Auth/network/operations; allowlisted metadata only | All transports; no tokens/OTP/messages/answers in output | Canary redaction in success and exception paths | A1 |
| OTP one-time use | Identity proof; one consumed generation | Existing OTP shape + new service; expired/reused proof gives generic safe error | Sequential replay and simultaneous valid verification, five wrong attempts | B1 |
| SMS budget / provider failure | Identity adapter and limiter; bounded spend, verified identity stable | Send accepted differs from delivered; outage/backoff cannot loop | Cross-process counters, resend/send race, ambiguous provider response, no unverification | B1 |
| Safe refresh | Sessions; one rotation per generation | Refresh; ambiguous failure reauth, safe replay once | Concurrent 401s, reuse, lost response, absolute expiry | B2 |
| Logout during refresh | KMP session generation; old scope cannot publish | Local logout first; remote revocation when online | Pause refresh then logout/switch, deliver old response, assert no credential/state revival | B2 |
| Staggered releases | Contract authority + adapters | Supported v1/v2; explicit cutoff, no silent payload reinterpretation | Current released fixtures and N-1 matrix | All |
| Valid onboarding | Session/profile/relationship; pending partner is valid | Session projection; network error is not singles state | Restart/profile incomplete/pending/accepted/unlinked/deep link | B2/B3 |
| One active partnership | Relationship constraints + ordered user locks | Accept/cancel with operation/revision; conflict doesn't duplicate couple | Double acceptance, crossed invites, simultaneous invites to different people | B3 |
| Revoked couple access | Membership epoch + feature guard | HTTP/socket/media; old queued intent fails original scope | Unlink while connected, concurrent write, stale cached IDs and new partner | B3/C3 |
| Message durability and retry | Chat DB transaction and client pending store | Same operation → same entity; commit boundary independent of broadcast | Commit then kill before ack; retry produces one message | C1 |
| Realtime convergence | Change index + local checkpoint | HTTP changes; 410 snapshot after retention, pending preserved | Lost/reordered/duplicate/unknown event, gap and old cursor | C1 |
| Room isolation | Immutable room route / scoped projection | Event/fetch has room ID; never fallback to active room | A request resolves after switching to B; room-list reorder; two concurrent image sends | C1/C3 |
| Edit/delete/reaction semantics | Chat revision and per-user records | Revision conflict; tombstone not resurrection | Concurrent edit/delete, delete-for-me then refetch, stale reply preview | C2 |
| Meaningful receipts | Per-participant observed sequence | Monotonic through-sequence, not all currently stored messages | Send arrives between fetch/read; multi-device monotonicity, unread reconciliation | C2 |
| Hidden Spark answers | Spark operation/serializer; reveal atomically | No partner text before both submit; skipped/unlinked never reveal | Assert absence in every response/event/export, simultaneous submit and edit/reveal race | D1 |
| Challenge completion once | Challenge instance lock + operation identity | Both accepted confirmations; retries same result | Repeated/concurrent completion, cancellation conflict, one side effect | D2 |
| Creator-only Moment mutation | Moment service; membership isn't creator permission | Existing and v2 routes; partner/unrelated mutation denied | Creator/partner/third-party full method matrix | D3 |
| Maximum five photos | Parent lock + owned upload claims | Validate retained + new; conflict/validation retains safe draft | Concurrent add/delete/upload using PostgreSQL, retry/cancel/storage failure | D3 |
| Immutable 24-hour public visibility | Publication time + eligibility | Existing/proposed reads; deny `now >= expires_at` everywhere | Exact boundary, edit/add another Moment, stale cache, signed URL/proxy, delayed cleanup | D3 |
| Personal Faves / distinct discovery | Preferences/feed | Existing signed cursor; empty Faves stays empty; 409/410 restart | User A/B Faves isolation, cache outage, tied pagination, blocked/expired exclusion | D4 |
| Private template reuse | Challenge + Moment link | Approved template creates private proposal; no expired media grant | Repeated Try, withdrawn template, expired source, no original-creator notification | D4 |
| Account isolation | Session scope + local DB/cache/push | Old generation fails; no cross-account fallback | Switch while fetch/upload/refresh/push pending; inspect database and images | B2/C1/C3 |
| Privacy deletion/recovery | Feature owners + durable purge jobs | Immediate logical denial; approved physical/backup policy | Retry failed purge; restore old backup reapplies deletion and revocation | C3/D3 |

## Figma and state coverage

The current MCP quota prevents completing the visual trace. Preserve documented
theme/components/assets; do not invent new Figma screens or claim this design has
been visually signed off. `figma-spec.json` is a dated snapshot, not live sync.

| Area / references | Required state specification before UI work | Current coverage |
| --- | --- | --- |
| Design system page `0:1`; `docs/design-system/README.md` | Typography, token roles, spacing, shapes, assets; light versus derived dark | Prior local spec and theme tests; current page contents blocked |
| Partner flow nodes `14:220`, `5:974`, `5:1118` | Searching/empty/not-found/invalid/duplicate/pending/rejected/accepted; safe unlink later | Existing flow docs and tests; nodes not re-read this run; placeholder artwork remains declared |
| Auth frames, IDs pending | Phone/OTP loading, invalid/expired/throttled/provider error; offline, session expired, accessible countdown | Existing implementation inspected at logic seams; live frame mapping incomplete |
| Chat frames, IDs pending | Cached/loading/empty, pending/failed/retry, permission revoked, offline/reconnecting, deleted/reply/reaction/media errors | Current behavior inspected; room/runtime visual verification not run |
| Spark / Challenge frames, IDs pending | Invite/decline/waiting, own response, hidden/revealed, paused/skipped/cancelled/completed; consent and unlink | New feature proposal; no live Figma implementation claim |
| Moment / Global / Faves frames, IDs pending | Upload progress/cancel/order/limit, creator controls, expiry/unavailable, empty Faves, feed restart/report/block | Backend rules inspected; mobile feature mapping and approved screens needed |

For each feature PR later, record file key, exact screen node, inspection date,
intentional deviations and generated/exported assets. Test dynamic text, contrast,
screen-reader order, keyboard/insets and error focus on both platforms. Use actual
icons/assets, never text/emoji stand-ins to claim design fidelity. Missing designs
should produce an explicit small state specification for approval, not a silent
redesign of the established theme.

## CI and release quality gates to add incrementally

Retain existing workspace/theme checks. Add selected backend tests and PostgreSQL
service jobs with synthetic data; restrict provider/network access. Add contract
schema validation, emitted-response fixtures and breaking-diff checks; schema drift
blocks a release when its supported client cannot decode or safely behave.

Use repository-adopted formatting/static tools where present. Where absent, choose
and pin a compatible tool during the relevant slice; first baseline existing
violations rather than reformatting the whole project. Run dependency/secret scans,
review actionable findings with provenance and expiry, and protect branch/release
credentials. No scan was performed or configured by this design task.

Migration checks include `makemigrations --check` through the isolated helper,
empty and representative restored-schema runs in disposable databases, bounded
backfill rehearsal and lock-time measurement. Never use an existing development
or production database as a CI migration scratch space. Schema additions precede
new clients; destructive cleanup waits for adoption and a restore rehearsal.

Android builds/shared tests run on the normal runner. Full iOS framework/app build,
link and simulator smoke need a macOS runner with the project's supported full
Xcode/SDK combination. Cached Kotlin compilation is insufficient. Device-specific
permissions, secure storage, notification and lifecycle tests remain release gates.

Report each required job as passed, failed, skipped or blocked with scope. A skipped
PostgreSQL/device gate cannot be summarized as “all tests passed” for a release.
Pair frontend/backend release SHAs and contract digest, track approved decisions,
monitor cohort metrics, then remove temporary adapters only under roadmap criteria.

## Documentation validation for this run

Passed: 13 new documentation files, 18 local Markdown links/anchors, balanced
fences, JSON parsing and whitespace checks. Both HEADs remain at their recorded
baselines on `refactor/srisu-system-design`. SHA-256 comparison confirmed all 19
pre-existing changed/untracked files unchanged; every newly added path is inside
the canonical design directory or the single backend pointer. Tracked diffs still
belong to the pre-existing workspace work.

Passed: both JSON Schemas checked with `jsonschema` 4.25.1; OpenAPI 3.1 validated
with `openapi-spec-validator` 0.7.2 including local external references. Sixteen
synthetic positive/negative schema cases passed, including all three Markdown
JSON examples, invalid-shape handling, tombstone privacy shape, UUID/ID
types and legacy long-text preservation. These are checks of the design artifacts,
not tests of an implemented server. Validation tools were installed only in
`/private/tmp/srisu-design-validators-20260926`; repository dependencies unchanged.

All ten Mermaid diagrams parsed and rendered using Mermaid 11.12.0 in an isolated
headless browser. The entity diagrams and critical sequence renders were visually
inspected; the relationship view was split for readability. Temporary SVG/PNG
renders are under `/private/tmp/srisu-design-diagrams/`; the Markdown Mermaid source
is canonical. An initial sequence-label syntax issue was corrected before the
successful final render. An initial deprecated validator invocation did not resolve
relative references; validation succeeded using its supported URL entry point.

No application code, existing tests, lockfiles, migration files or Figma content
changed. No commit, push, merge, deployment, existing-database migration or
implementation was performed. Publishing this design to other laptops remains a
separate action requiring authorization under this run's explicit restriction.
