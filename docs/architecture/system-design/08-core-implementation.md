# Core implementation — 2026-09-26

The subsequent implementation request authorizes this foundation slice. It does
**not** approve every decision in proposal 0.1. This record describes implemented
code; [the validation record](09-core-validation.md) separates evidence and gaps.
No commit, push, merge, deployment, Figma edit, or existing-data migration was made.

## Baselines and ownership

| Repository | Local branch created | Starting HEAD |
| --- | --- | --- |
| Frontend `/Users/srijan/studioprojects/SriSu` | `dev-core-architecture` | `8af5deb9c9a246504bd1b3b4776122505687feb9` |
| Backend `/Users/srijan/studioprojects/SriSu-backend` | `dev-core-architecture` | `1a2df95c5aece1d110408bf90aa9ed6f9970da18` |

Both branches start from the user's existing `refactor/srisu-system-design`
checkouts, including uncommitted design/workspace files. Upstream integration
branches were read and had advanced; they were deliberately not merged. The
repositories remain independent changes requiring paired release coordination.

## Implemented boundaries

| Owner/lifetime | Responsibility | Main implementation |
| --- | --- | --- |
| Koin application | One environment, HTTP client/engine, secure-store coordinator, public catalogue database | `di/NetworkModule.kt`, platform `NetworkModule.*.kt` |
| Application child scope | Foreground state, socket transport, in-memory chat projection; canceled on application close | `ApplicationLifetime`, `BaseWebSocketClient`, `ChatRepository` |
| Session generation | In-flight request/response identity; credentials stay in existing KVault storage | `SessionCoordinator`, `HttpClientFactory` |
| Account UI lifetime | Navigation and ViewModelStore destroyed on account change; Authentication ViewModel shared within that account's navigation host | `app/App.kt`, `di/AuthModule.kt`, `AuthGraph.kt` |
| Feature ViewModel/state holder | Immutable state, cancellation, retry actions, durable loading/error/offline results | `InterestCatalogueStateHolder`, `EditProfileViewModel`, `ChatViewModel` |
| Django shared infrastructure | Opt-in exception mapping, correlation, safe diagnostics, disposable cache mechanisms | `srisu/api/`, `utils/exception_handlers.py` |
| Django Chat | Authorization selectors, command validation, subscription checks, services and presenters | `chat/selectors/access.py`, `chat/api/history.py`, consumer/protocol files |

Shared infrastructure does not own Chat mutation rules, Authentication proofs,
Moment ownership, Faves, or Social visibility. Existing services/models remain.
No new Gradle feature modules, service, transport stack or universal base classes.

## HTTP and session integration

`ApiEnvironment` uses one origin for HTTP and WebSocket. Build with, for example:

```sh
./gradlew :composeApp:assembleDebug -Psrisu.environment=development -Psrisu.apiBaseUrl=http://10.0.2.2:8000/
```

The development default is loopback, not the previous developer LAN address.
A physical device needs a reachable development origin. Staging/production require
HTTPS. Release packaging/link tasks reject development configuration. Android
cleartext is debug-only and app backup is disabled; iOS has separate debug and
release ATS plists. Platform release behavior still needs native/device verification.
No TLS trust-all callbacks or token-bearing socket URLs are used by the new client.

`safeRequest` retains the existing `ResultHandler` API while adding `ApiError`
(machine code, typed category, field codes, status, request ID, retry-after and
retryability) and success metadata. Envelope, raw/paginated resources and empty
responses are separate response shapes. 204/205 and empty bodies succeed without
JSON decoding; malformed JSON/protocol responses become serialization failures.
Cancellation, including a changed session, propagates as cancellation.

Ktor timeouts are 10s connect / 30s request / 30s socket. GET/HEAD retries are
explicit, at most two additional attempts, with bounded delay. There is no write
replay, rate-limit auto-retry, automatic redirect, or credential forwarding to
other origins. A logical request retains its account generation across retries
and body parsing. Connectivity failures do not clear credentials. Secure-storage
writes must succeed before publishing a new session.

**There is no implemented refresh endpoint in this baseline.** No pretend refresh
coordinator, refresh loop, or token-lifetime change was added. `SessionCoordinator`
provides generation-checked authorization and `saveIfCurrent` for the later real
Authentication refactor. Existing ten-year tokens and lack of device revocation
remain serious launch gaps. A secure-store clear failure returns failure while
immediately clearing memory; hardware/storage failure and restart recovery still
need a dedicated Authentication UX, not a promise of infallible deletion.

Django preserves legacy successful envelopes and HTTP status codes. Requests with
`X-SriSu-Contract: core-1` receive the new safe envelope for DRF exceptions; legacy
clients keep existing exception behavior. Manual legacy error responses outside
this slice remain legacy. KMP preserves their field names as generic `invalid`
codes without forwarding arbitrary server strings. Unhandled opt-in exceptions
mark transaction rollback and return a fixed 500, not internal exception text.

## Realtime transport and Chat recovery

The KMP transport owns connection states, foreground pause/resume, cancellation,
cleanup, serialized sends and five reconnect attempts after the initial attempt.
Backoff grows from 250ms to a bounded delay with 0.8–1.2 jitter. Account change or
logout replaces/cancels the connection; authorization/policy failures are terminal.
Explicit retry and foreground re-entry provide recovery after exhaustion. Ktor
pings are configured at 25s; OkHttp uses its engine ping setting.

The installed Ktor/OkHttp rejects the plugin's frame-size setter. The connector
therefore rejects frames above 1 MiB **after native receipt and before decoding**.
That bounds parsing, not all engine allocations. The feature event stream holds
at most 64 buffered events, suspending the producer; pending acknowledgments are
limited to 32, with a 10s timeout. Outgoing commands are limited to 64 KiB and
there is no offline command queue. This native-engine limitation needs pressure
measurement before larger payloads are supported.

Chat protocol decoding remains in `ChatWebSocketClient`. Events carry the captured
session generation; consumers reject obsolete generations. Event UUID deduplication
keeps only 256 recent IDs. Unknown/malformed events request a snapshot. Durable
mutations are treated as invalidations, so an older edit/delete event cannot
overwrite a newer HTTP snapshot. Typing is ephemeral and routed by room ID.

A successful socket send means only a transport write. Feature commands now await
a correlated server success. Timeout/disconnect after sending means **unconfirmed**
delivery; the draft is retained and no write is automatically replayed. A later
manual retry can still duplicate a command: durable operation IDs/revisions/outbox
remain C1 work, not an exactly-once claim.

The backend adds authenticated reads:

- `GET /api/chat/rooms/`: signed actor-bound cursor, 15-minute validity, descending
  `updated_at` and UUID tie-break; invalid/expired cursors return 400.
- `GET /api/chat/rooms/{uuid}/messages/`: descending message-ID cursor, existing
  visibility/deletion rules, maximum 50 records per page.

Both return `Cache-Control: private, no-store`. They share current-relationship
selectors with socket commands and delivery. Couple rooms require both current
members and an accepted connection. The compatible legacy singles path requires
an accepted connection whose phone pair matches the actual participants.

`fetch_messages` subscribes; `unsubscribe_room` removes a subscription; disconnect
removes all groups. Every command and private event rechecks authorization. A
watchdog checks expiry/activity/membership at most every 15s, in addition to checks
on traffic. Browser Origins require an explicit allowlist; native clients omit
Origin. Legacy query-token authentication remains a temporary server adapter.
Proxy access logs must redact its query string while old clients exist.

Chat reconnect/foreground triggers HTTP recovery. Active application chat also
refreshes every 30s to repair a lost final publication without waiting for another
event. Bursts are coalesced. The current message window replaces stale content;
older pages can be reloaded. This may reset history scroll after an invalidation
and should become incremental revision recovery at C1. Account changes, known
revocation, terminal auth failure and socket disconnect clear private projections.
Cancelled or mismatched-room HTTP results cannot populate the visible room.

The services commit before event publication/acknowledgment. Channels publication
failure does not turn a committed command into a false rejection. Process failure
between commit and acknowledgment is still ambiguous. No in-memory callback is
claimed to provide durable publication.

## Local persistence, server cache and privacy

There was no local relational database in the selected KMP baseline. Room 2.7.2,
SQLite 2.5.2 and matching KSP were added only after Android/iOS compilation checks.
Database schema 1 stores **public interest catalogue JSON and fetch time only**,
scoped by environment/version. Platform builders own paths/drivers; the DAO owns
atomic replacement. Exported schema is checked in. No destructive fallback or
migration from an imaginary earlier schema was added. Future schema changes require
an explicit migration and a reopen test from this exported version.

`InterestCatalogueRepository` coalesces loads, uses a 5-minute fresh interval, and
permits clearly labeled stale public metadata for up to 24 hours on connectivity,
timeout or server errors. It refuses fallback on 401/403. A changed session cancels
late results before writes. Private profiles, chosen interests, conversations,
media, tokens and OTPs are not stored in this database. Existing preference/secure
storage formats are preserved; KVault is not a claim that all private media caches
are encrypted or purged.

The server catalogue uses the existing default performance cache: explicit
namespace/version/scope keys, five-minute TTL, post-commit generation invalidation,
and authoritative DB fallback on cache outage. A late loader cannot repopulate the
new generation. Existing LocMem invalidation is per process; cross-process freshness
is bounded by TTL, not immediate. Combined server/client fresh intervals can reach
ten minutes; offline fallback is separately labeled. The small catalogue does not
justify distributed locks/stampede infrastructure.

No permission, OTP, Moment visibility or abuse decision is performance-cached.
Expired Moments still use their existing authoritative query rules. New history
read throttles are 120/min per actor using DRF's existing cache; cache failure rejects
the read rather than bypassing the throttle. This and socket's 30-command burst /
10-per-second **per-connection** guard are resource controls, not distributed abuse
protection or an OTP spend limit.

## Contracts, diagnostics and adding a feature

Backend `contracts/core-1/` owns JSON schemas, semantics and synthetic fixtures.
KMP `contracts/core-1/` is a pinned copy; its generated test fixtures come from that
copy. Backend tests validate actual serializers/responses against the schema.
KMP tests parse those same examples. This is an implemented subset, not a complete
OpenAPI description of all Authentication/Chat/Social APIs. The proposed v2 design
schemas remain proposed and are not advertised as live endpoints.

```sh
# Frontend: inspect drift before explicitly accepting a backend change.
python3 tools/core_contracts.py --backend ../SriSu-backend
python3 tools/core_contracts.py --backend ../SriSu-backend --update
```

The printed SHA-256 pins the three contract files even before this local work has
commit IDs. CI checks each repo's contract; a paired workspace verifies equality.
No CI claim assumes both branches deploy atomically.

For a new feature: place DTOs/API calls in its feature, use the single injected
client and an explicit response shape, map DTOs at its repository boundary, and
publish immutable ViewModel state. Keep authoritative success/error outcomes in
state; a dismissal action acknowledges transient UI. Use the owner's scope and
lifecycle-aware collection. Declare cache scope/freshness and session generation
before adding persistence. Add a contract fixture and a backend authorization test,
then a KMP parsing/state test. Do not derive permissions or endpoints from Figma.

Working examples are `ProfileApiService → InterestCatalogueRepository →
InterestCatalogueStateHolder → EditProfileScreen`, and `ChatApiService +
ChatWebSocketClient → ChatRepository → ChatViewModel/ChatScreen`. Existing profile
and chat visual components/tokens are retained; only loading/offline/retry status
is added. Current Figma content was not re-fetched or edited during this core task.

HTTP diagnostics contain status, elapsed time and a sanitized correlation UUID.
Backend structured logs use fixed event names and allowlisted metadata; new core
logs exclude exception text/body/query/user details. Legacy arbitrary KMP logging
calls are intentionally no-op adapters. Remove those adapters as feature call sites
are converted to the structured API. Cache hit/miss diagnostics are DEBUG-level.
No new metrics infrastructure is required; production aggregation/load percentiles
remain deployment work.

## Compatibility, remaining work and first Authentication slice

Deploy the backend additions before a new mobile build; existing installed clients
keep v1 envelopes/actions and query-token socket auth. No Django schema migration
is needed. Configure `DJANGO_SECRET_KEY`, allowed hosts and browser origins before
running the normal backend. `.env.example` is synthetic. If the former source key
was used in a deployment, treat it as exposed and plan coordinated rotation; this
work neither rotates deployed keys nor silently changes token lifetime.

Before deployment, reconcile the advanced integration branches and run paired
checks on the combined code. Full device session revocation/refresh, strict
unlink-versus-write transaction serialization, private media ownership/delivery,
private offline retention, durable message dedup/revisions and historical media
cache cleanup remain separate refactors. In particular, the legacy media model
has no owner and its current media-ID lookup is not fixed by room authorization.
Do not treat this core slice as production security certification.

Temporary adapters: `ResultHandler` convenience strings (remove as each screen
adopts `ApiError`); v1 socket schemas/query token support (remove only after a mobile
support cutoff); existing Interest DTOs in legacy navigation (remove during profile
model refactor); full-window Chat snapshots (replace after durable C1 revisions).
The lower-level transport/HTTP/session/DAO mechanisms themselves are active code.

**First Authentication refactor:** implement real OTP request → one-time verify
across Django and KMP while retaining Twilio Messaging and existing screens. Remove
the client's disabled-request bypass, protect already verified identities at
request time, store only protected proof material, atomically consume a proof,
bound attempts/resend/spend, and test replay/concurrent verification. Keep session
lifetimes unchanged until the device-session/legacy-client cutoff decision is made.
