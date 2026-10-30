# Security, privacy and access policy

**PROPOSED 0.1.** Mechanisms and acceptance requirements, not a security
certification. Deployment configuration and real service exposure were not
verified. [Evidence E1–E13](01-current-system.md) identifies the inspected risks.

## Immediate decisions and containment proposal

The inspected source logs credentials/private content and includes a literal
signing secret, debug configuration and ten-year JWT lifetimes. Do not copy these
values into reports. If deployed as-is, restrict diagnostic access, stop sensitive
logging, replace exposed secrets through the deployment secret store and revoke
affected sessions. Establish a controlled reauthentication notice and rollback
plan that never restores a compromised key. Investigate exposure using metadata
and access history, not by exporting private logs. No containment was performed
in this design task; actual deployed configuration needs authorized verification.

Changing JWT duration only affects newly issued tokens. B2 must define cutoff for
existing tokens, key rotation, session registry enforcement and legacy app behavior.
Do not leave a ten-year token usable through a legacy socket route after adding
revocation to REST. JWT and Django signing keys should be separate with documented
rotation impact, including signed feed cursors. Deleting a secret from Git HEAD
does not revoke it or remove historical exposure.

## Threat model and trust boundaries

Assets: login proof, sessions, phone identity, relationship graph, private messages
and media, unrevealed answers, participation, location/preferences, public media,
device tokens and backups. Public content still has audience and lifetime rules.

| Actor / threat | Boundary and mechanism | Residual risk / required test |
| --- | --- | --- |
| Anonymous attacker: OTP guessing, SMS pumping, enumeration | Bounded proof attempts, phone/IP/device/destination limits, global spend circuit breaker, generic entry responses | Shared IPs and spoofable device IDs limit attribution; test distributed requests and provider outage without real SMS |
| Authenticated unrelated user: object-ID probing, upload substitution | Scoped ORM queries, server-derived actor, current membership and media ownership checks | UUIDs are not authorization; negative tests for every transport/action, including original media URLs |
| Current partner: coercion, premature answer reading, editing another's Moment | Per-operation creator/participant rights, hidden fields absent server-side, separate consent | An authorized partner can screenshot or share information they can see; UI cannot prevent coercion or recall copies |
| Former partner: stale tokens, open socket, saved URLs, relinking | Membership epoch/history, revocation transaction, per-command/read/delivery checks; original audience snapshots | Already downloaded content remains; historical archive rights need explicit policy, default deny pending decision |
| Stolen/lost device or malicious local app | KVault secrets, OS app-private storage, backup exclusions, minimized offline cache, session revoke | Unlocked/rooted/jailbroken device and screenshots exceed ordinary app sandbox guarantees; do not claim full device compromise resistance |
| Compromised operator or admin account | Separate operator role, strong admin authentication, audited break-glass, least privilege, no default content browsing | Trusted-server architecture still permits privileged server access; E2EE would materially change this boundary |
| SMS/push/storage provider | Minimum data, scoped credentials, private buckets, redacted generic push | SMS provider sees destination and OTP content in current model; push provider sees device token/metadata; regional processing must be approved |
| Infrastructure outage/restore | Durable invariants, bounded retries, isolated backups and tested restoration | Disaster recovery bounded by RPO; restored data must reapply deletion/revocation records before serving users |

Network boundary: HTTPS/WSS only in release, allowlisted backend origin, no bearer
forwarding to arbitrary media hosts or redirects. Android network security and
iOS ATS configuration must reject release cleartext. Pinning is not a launch
default; it introduces key-rotation/outage costs and needs a supported recovery
strategy. Do not disable trust validation to make development endpoints work.

REST JWT authentication does not remove CSRF requirements from Django admin or
any cookie-authenticated endpoints. Restrict hosts/CORS/origins, use secure session
cookies for admin, add origin validation to browser-capable WebSocket entry, and
rate-limit frame/connection establishment. Native clients' absent Origin requires
an explicit tested rule; Origin is not a substitute for token validation.

## Authorization matrix

“Partner” below means the original, currently authorized member. Proposed archive
rights after unlink are unresolved. An authenticated public viewer must also meet
audience, block and content-safety policy. Operators have no ordinary user bypass.

| Object / operation | Self / creator | Current partner | Other couple | Former member | Operator / provider |
| --- | --- | --- | --- | --- | --- |
| Own profile / session list | Read; allowlisted self-edit; revoke own sessions | Minimal permitted profile only | Minimal approved invitation/public projection | No special access | Restricted audited support metadata; never tokens |
| Invitation | Sender create/cancel; receiver accept/decline | Only named receiver/sender | No | No implicit restoration | State metadata for support; no consent override |
| Couple profile | Current member edits approved shared fields | Same, subject to contribution rules | Explicit public projection only | Deny until archive decision | Audited support/moderation workflow |
| Chat history/media | Current room participant reads; sender owns edits and delete-for-everyone | Reads; own per-user deletion, reactions and receipts | Deny | Deny pending archive policy | No routine reading; exceptional access reason and audit |
| Spark answer before reveal | Author reads/edits own until reveal | Submission status only | Deny | Deny | No routine answer access |
| Spark after joint reveal | Both accepted original current members | Same | Deny | Deny pending archive policy | Exceptional access only under defined process |
| Challenge participation | Own contribution; accepted members see agreed shared progress | Own contribution and accepted progress | Deny | Deny pending archive policy | Templates manageable independently of private participation |
| Moment create/edit/delete | Either member creates; only creator edits/deletes | Cannot edit/delete creator's publication; can report and exercise approved consent withdrawal | Deny | No continuing mutation right inferred | Moderation can restrict visibility through a distinct audited action |
| Moment read/photo | Eligible original current members while policy allows | Same | Only eligible unexpired public projection | Deny | Storage provider serves only guarded requests; admin exceptional access |
| Moment private notes/replies | Eligible original audience only | Eligible original audience only | Never in public serializer | Deny | No routine access |
| Faves/seen/hides | Personal list and desired-state writes | Not automatically shared | No access to another user's list | Remains personal, with unavailable targets hidden | Aggregated metrics only unless support need |
| Try This Together | Member creates private proposal from approved template | Accept/decline normally | No visibility into who tried it | Deny | Manage template availability, not private answers |

Map each permission into selector and operation tests. A hidden button is only
presentation. Search/list/detail/photo/socket paths need the same policy. Block
policy must define direction explicitly: recommendation is bilateral discovery
and public-media suppression, without silently unlinking an existing partnership.
Handling abuse by a current partner requires a separate safe unlink/report flow.

## Identity and OTP controls

Current Twilio Messaging transports a server-generated code. It does not verify
that code for SriSu. Recommended B1 retains this transport with established secure
random generation and keyed proof verification; evaluate Twilio Verify separately
for destination support, fraud controls, data processing and cost. Do not claim
Verify is already deployed or invent provider guarantees.

Normalize to E.164 using a vetted phone parser and explicit country context. Do
not guess country from device locale or merge accounts when normalization collides.
Before a uniqueness migration, inventory synthetic/test and historical formats,
quarantine conflicts and approve remediation. Phone change is a sensitive identity
operation, not a normal profile edit.

For retained server-managed OTPs: use a cryptographically secure generator;
store a keyed HMAC over challenge ID, normalized phone, purpose, generation and
code, with constant-time comparison and a server secret outside the DB. A plain
unsalted digest is insufficient for a six-digit search space. Separate HMAC-key
rotation from JWT key rotation; never log code/proof or echo code in responses.
Expiry five minutes, one active generation per purpose/phone, proposed five wrong
attempts per challenge, resend cooldown 60 seconds and bounded per-phone/hour
budget are starting values to tune. These limits are proposals, not current facts.

Use Redis atomic counters for fast multi-process budgets and database proof
state/attempt counters for durable consumption. Verification locks proof row,
checks expiry/attempt budget/purpose/generation, consumes once and creates/finds
the unique identity in the same transaction. Concurrent valid submissions cannot
mint independent successful sessions from the same proof. Requesting a code never
marks an already verified account unverified. A failed provider send changes
delivery state, not account identity.

Record provider dispatch state and request identity. Retry only on known-safe
provider outcomes; ambiguous acceptance counts against spend and needs bounded
reconciliation/cooldown. Limits by phone, challenge, IP/network, coarse device
signal, destination and total account spend reduce different attacks; do not use
a device-provided identifier as a trusted identity. Fail closed for sending when
global abuse control is unavailable, while preserving already authenticated use.

Phone-only OTP cannot reliably distinguish a recycled number's new owner from the
old account holder. Before release, choose a recovery policy: recommend a separate
verified recovery factor, reauthentication for phone change, notifications to
existing sessions and a hold on sensitive recovery. Do not reveal old couple data
solely because a new device can receive an SMS if the account recovery policy has
not been satisfied. Product must choose the friction and support process; no
custom cryptographic recovery system is proposed.

## Sessions, membership and mobile data

Session registry contains device/session ID, user, created/absolute expiry,
refresh generation/hash identifier, revoked-at and minimal last-use metadata.
No raw refresh secret is stored in diagnostic/session lists. Signature checking
is followed by current registry/identity authorization. Start with DB checks for
revocable operations; avoid cached “allow” decisions that contradict immediate
revocation. Measure cost before adding bounded caches or revocation propagation.

Socket authorization occurs at connection, subscription, command, recovered read
and event delivery. Use current session/membership checks, not a forever-valid
connect-time principal. Schedule access-expiry close/re-authentication; reconnect
with a fresh token after coordinated refresh. Native clients can use an auth
header; if another client requires a query ticket, issue a short-lived one-use
ticket and redact proxy logs, rather than placing long-lived JWTs in URLs.

Unlink serializes against writes using the common lock order. No new couple write
commits after the unlink transaction linearizes. Private reads/delivery authorize
at the server; bytes authorized before revocation may already be in flight. State
that limit explicitly. Durable invalidation closes sockets promptly but is not the
only enforcement mechanism. Old queued operations retain original couple/epoch and
fail; they are never reassigned to a new partner.

KMP account generation guards refresh and all network completions. Clear or
isolate database, image caches, drafts, pending operations, socket subscriptions,
navigation back stack and push registration on logout/account switch. An account
ID in a DB filename is useful scoping, not encryption. If durable chat storage is
adopted, use OS file protection, backup exclusion and a vetted encrypted-storage
strategy appropriate to the threat decision; plain Room does not encrypt itself.
Keep credential material in current KVault platform storage and validate its
behavior on supported Android/iOS versions, reinstall and locked-device states.

Server revocation for a device that is offline is not instantaneous client cache
erasure. Recommended conservative default is to require foreground revalidation
before exposing shared content after session invalidation/expiry or uncertain
membership. Decide whether limited offline history is acceptable before C1; a
product cannot promise both indefinite offline reading and immediate remote recall.

## Media and lifetime

Uploads are allowlisted decoded image formats for the first implementation, with
size, pixel/dimension, aggregate request and per-user storage limits. Preserve the
current Moment JPEG/PNG/WebP and 10 MiB limit unless approval changes it. Avoid
accepting arbitrary HTML/SVG/polyglot content by filename or supplied MIME alone.
Re-encode images, strip EXIF/location, assign server names, and protect decoding
against decompression bombs. Scan additional file types before introducing them;
calls/video/documents are outside this task.

Stage outside webroot/private bucket with uploader/couple epoch, content digest,
verified metadata and expiry. Finalize atomically claims uploads for one authorized
target and enforces five photos under parent lock. Storage success and DB commit
are not one transaction: retain compensation and durable deletion records. Keep
grace periods for staged/orphan cleanup; never delete recently uploaded bytes by
an unsafe directory sweep. Existing media with unknown ownership is quarantined
or attributed from trusted relational records, not granted to whoever requests it.

Guard media through a service that checks current object permission and time on
every request. Deny raw origin paths at the reverse proxy/object bucket too; Django
route protection alone cannot constrain an independently public media server.
`Cache-Control: private, no-store` is the launch choice for private and expiring
images. Authenticated image caches must be scoped and cleaned. Public CDN/signed
URLs can weaken immediate revocation; any future use needs a maximum revocation
window approved and tested against exact expiry. No design can recall screenshots
or bytes already downloaded while authorized.

## Retention, deletion, consent and safety decisions

| Data | Existing evidence | Proposed decision / gate |
| --- | --- | --- |
| OTP | Plaintext record retained in model | Expire proof promptly; propose deleting terminal proof material within 24 h and retaining only minimized abuse counters for seven days; approve regional retention |
| Sessions | Very long JWT expiry | 30-day device family cap, explicit revocation; retain minimal revoked identifiers through all associated token expiry |
| Chat/private Sparks/Challenges | No verified end-to-end retention policy | Choose duration, offline policy, export and former-member rights before C1/D1. Do not silently infer permanent retention or shared deletion authority |
| Moments | Visibility 24 h for private and public; purge 30 days after expiry | Keep behavior until decision; public immutable 24 h is fixed. Private archive, early deletion and purge SLA require approval before D3 |
| Deletion/backup | Existing Moment file-deletion queue | Logical deny immediately, tracked physical purge retries, proposed backup maximum 35 days; approve exact retention and restore-deletion ledger behavior |
| Logs/audit | Sensitive prints currently present | Replace with allowlisted fields; propose 14-day operational logs, 90-day restricted security events, no payloads. Confirm region and need before rollout |
| Public contributions | `partner_memory` creator-editable field exists | Do not treat it as partner consent. Require explicit consent for publication of partner-originated private material and define withdrawal effect |

Account deletion must revoke sessions, disable new access, unlink current
membership and enqueue a tracked purge/export workflow. Shared messages, partner
contributions, abuse records and backups create competing interests: a deletion
policy must distinguish deleting an account's private data from erasing another
person's lawful copy/contribution. No immediate hard-delete cascade is authorized
by this design. Exports require recent authentication and short-lived authorized
delivery; an export must never include unrevealed partner answers.

Public launch needs report categories, block/hide behavior, a named moderation
owner, abuse escalation, appeal/contact process and target audience/age/regions.
Moderation hides content through a separate audited transition, not by impersonating
its creator. Sensitive report attachments have tighter access than ordinary admin.
Prefer Django admin for curated templates and restricted moderation queues, with
audited roles and strong authentication; no new CMS is necessary initially.

Push defaults to “New activity” without message text, answers, partner names or
photo previews. Device registration belongs to a user/session and is invalidated
on logout/switch; worker rechecks recipient authorization before dispatch. A stale
push opens a reauthorized route and reveals no stale content on the lock screen.

## Standards mapping and evidence needed

Version anchors: [OWASP ASVS 5.0.0](https://github.com/OWASP/ASVS) and
[MASVS 2.1.0 mapping](https://cheatsheetseries.owasp.org/IndexMASVS.html).
ASVS requirements and MASVS families guide tests; this is not a claimed assessment
level, completed control inventory or certification.

| Reference | SriSu mechanism | Proof required in implementation |
| --- | --- | --- |
| ASVS V6 authentication; V7.4.1–V7.4.4 session termination | One-time proof, bounded guesses; device revoke, disable-account revoke, sensitive-change revoke and visible logout | PostgreSQL race tests, REST/socket invalidation, local logout with failed network |
| ASVS V7.5.1 | Reauthenticate before changing sensitive identity/recovery attributes | Negative stale-auth tests and recovery usability validation |
| ASVS V8.1.1–V8.1.2; V8.2.3 | Object and field-level policy; hidden Spark answers and private notes absent | Actor/action matrix exercised across detail/list/photo/event/export paths |
| ASVS V5 file handling | Verified images, bounded resources, private storage and guarded delivery | Malformed/oversized/polyglot fixtures; expiry/ownership/URL-bypass tests |
| MASVS STORAGE, CRYPTO, AUTH | Minimized protected credentials/cache, standard TLS/storage crypto, lifecycle isolation | Device storage/backup inspection, account-switch and revoked-session tests |
| MASVS NETWORK, PLATFORM, CODE | Trusted-origin traffic, platform lifecycle and secure link handling, supported dependency inventory | Release network capture, malicious deep link tests, Android/iOS builds and dependency scans |
| MASVS PRIVACY, RESILIENCE | Private notification defaults, retention/deletion controls and explicit device-compromise limits | Consent/retention tests, privacy review and proportionate tamper assessment without claiming perfect resistance |

Framework references: [Django transaction semantics](https://docs.djangoproject.com/en/6.0/topics/db/transactions/),
[Simple JWT settings](https://django-rest-framework-simplejwt.readthedocs.io/en/stable/settings/),
[KVault platform storage](https://github.com/Liftric/KVault), and
[Twilio Verify API](https://www.twilio.com/docs/verify/api). Verify behavior against
the pinned versions when implementing; current documentation is not a substitute
for testing the project's installed Ktor/OS/library combination.
