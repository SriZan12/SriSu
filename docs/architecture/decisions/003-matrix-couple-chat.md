# ADR 003: private Matrix transport for Couple Chat

Date: 2026-10-08. Status: accepted for implementation; release remains gated by
the verification items below.

This decision supersedes the proposed libsignal adapter in
[ADR 002](002-chat-e2ee.md) and the trusted-server chat choice in system
ADR-010. It applies only to Couple Chat. Django remains authoritative for SriSu
accounts, partner invitations, active couple membership, profiles, Moments,
Plans, Challenges, and every non-chat permission.

## Decision

Run a private, self-hosted Synapse homeserver and use Matrix rooms as the only
new message transport. The KMP application uses Trixnity with its vodozemac
crypto driver on Android and iOS. There is no plaintext or legacy-chat fallback.

Pinned implementation inputs:

- Synapse `v1.162.0`.
- Trixnity `5.9.2`, source tag commit
  `56583347f34cb5b190d9f3770842cbd46791a537`.
- Trixnity's pinned vodozemac binary package `v0.1.3`, whose Android and Native
  archives are checksum-verified by the published Gradle module.

Trixnity is Apache-2.0 and exposes common Android/iOS APIs for token login,
encrypted timelines, durable outbox processing, receipts, media, device
verification, cross-signing, and Room-backed persistence. Synapse is AGPL-3.0
software operated as a separate service. Distribution and deployment must keep
the applicable notices and source obligations; this record is not legal advice.

The current Trixnity release is built with Kotlin `2.3.21`. SriSu must consume it
with a compatible Kotlin/KSP/Room toolchain and pass the complete Android and iOS
build before the dependency update is accepted. Trixnity `4.22.7` was inspected
and rejected: although closer to the old SriSu toolchain, that line does not
provide the vodozemac client driver used by the current release.

## Ownership and provisioning

```text
SriSu invitation acceptance (Django transaction)
  -> accepted relationship + exactly two active memberships
  -> one stable SriSu ChatRoom identity
  -> one retryable Matrix-room provisioning record
  -> on-commit provisioning outside the database transaction
  -> private encrypted Matrix room containing those two Matrix users

Authenticated mobile session
  -> Django rechecks current membership for the SriSu room
  -> Django returns a short-lived Matrix JWT login credential and mapped room ID
  -> Trixnity logs in/restores its account store and starts Matrix sync
```

The Matrix user ID, room ID, or a client `isOwner` flag never establishes
authorization in Django. Provisioning identifiers are deterministic so a lost
HTTP response or worker retry cannot create a second conversation. Matrix
unavailability does not roll back an already committed partner acceptance; the
mapping remains pending and an idempotent worker retries it.

Every Couple Chat Matrix room is private, non-federated, non-public, and created
with `m.room.encryption` before participant messaging. Server-side directory
search and open registration are disabled. The service account is not a human
participant and must not receive room keys. It may provision membership and
room state only through the minimum administrative surface required by Synapse.

Unlinking immediately makes the Django bootstrap endpoint deny both former
members, marks provisioning for revocation, invalidates outstanding SriSu chat
entry state, and removes Matrix membership. A later relationship receives a new
SriSu room, Matrix room, and cryptographic session; identifiers are never reused
for a different relationship instance.

## Encryption, storage, and metadata

Trixnity encrypts on the sender's device and decrypts on authorized devices.
Synapse, PostgreSQL, Redis, Django, proxies, logs, and object storage must never
receive message plaintext, captions, attachment keys, private reply snippets, or
decrypted feature-card content. An encrypted Matrix event and its transaction ID
are retried as the same operation; SriSu does not wrap Matrix in a second custom
cryptographic protocol.

Matrix E2EE still exposes routing metadata, including Matrix account/device and
room identifiers, IP and timing information, event and attachment sizes,
membership changes, delivery requests, and encrypted-event counts. It does not
protect a compromised endpoint, an unlocked phone, screenshots, recipient
exports, or content already downloaded before a deletion.

Trixnity's Room account database contains encryption state and must be stored in
the application-private directory with Android file-based encryption and iOS
Data Protection/backup exclusion applied. A separate passphrase-encrypted
database or reviewed secure recovery design remains preferable before a broad
production release. The Django bootstrap JWT and resulting Matrix access token
stay in memory: Trixnity's persistent repositories are overridden with a volatile
authentication repository. They must not enter Room, logs, navigation arguments,
crash reports, or ordinary preferences. Decrypted bodies for encrypted timeline
events are configured as cache-only (`storeTimelineEventContentUnencrypted =
false`), but this does not encrypt Room's plaintext durable-outbox bodies, crypto
keys or metadata. App-level database encryption remains a production hardening
gate and no SQLCipher claim is made.

No server-readable history is migrated. The product owner explicitly authorized
destruction of previous chat data on 2026-10-08. A forward migration/management
operation removes legacy messages, reactions, receipts, attachment rows, staged
files, and obsolete v2 ciphertext/outbox state after the Matrix path is verified.
It preserves users, authentication, invitations, active relationships, couple
profiles, feature resources, migration history, and the relationship-scoped
SriSu `ChatRoom` identity used for navigation and mapping.

## Failure and rollout rules

- A missing or failed Matrix mapping renders a retryable setup state; it never
  routes content to the old Django WebSocket/message API.
- After the volatile Matrix session has been re-established, the current process
  may keep its last verified encrypted-room snapshot visible through a sync error.
  A cold process start requires a fresh Django bootstrap before cached ciphertext
  can be decrypted/read. The composer is disabled unless the room is freshly
  verified and sync is running. Trixnity durably retries messages already accepted
  into its gated outbox; this slice does not promise composing new messages offline
  and never reports delivery before server/receipt evidence.
- Trixnity's construction-time outbox worker is replaced by a named DI override
  whose child scope stays canceled until fresh Django authorization and the exact
  JOIN/Megolm/two-member Matrix gate pass. It is canceled before background,
  close, recovery, account/relationship change, or security-gate loss; `stopSync`
  alone is not treated as an outbox pause.
- Account or relationship changes close the previous Matrix client and select a
  database scoped by API origin, homeserver, account, relationship, membership
  epoch and Matrix room. Orphaned stores for that account are swept with an exact
  directory allowlist. Stale bootstrap/sync results cannot update another account
  or room.
- Membership is rechecked when Django issues bootstrap credentials and when
  lifecycle operations run. Synapse room membership is a transport capability,
  not a replacement for SriSu's relationship authority.
- Synapse JWT login credentials are replayable until their short expiry; they
  must stay memory-only and travel over TLS. The resulting Matrix access token
  is a separate session. The first rollout requests no Matrix refresh token,
  requires a 300–900 second non-refreshable access-token lifetime, logs out on SriSu
  logout/account switch, and re-bootstraps only while the same authenticated
  Django session is still current.
- The first shipped slice is encrypted text, timeline sync, gated durable outbox
  recovery, and truthful failure state. Replies, edits, reactions, deletion, receipts, media,
  voice, and typed SriSu cards are enabled only when their Matrix mapping and
  Android/iOS behavior are integrated and tested; controls must remain absent or
  disabled otherwise.

## Release verification gates

The following remain blocking evidence, not optional aspirations:

1. Android and iOS simulator compilation with the pinned Trixnity/vodozemac
   artifacts, plus real-device launch for every supported ABI.
2. A disposable Synapse integration test proving Android-to-iOS and iOS-to-
   Android encrypted text, process restart, offline catch-up, exact outbox retry,
   tamper/decryption failure, and absence of plaintext in Synapse/Django data.
3. Partner-acceptance concurrency, provisioning retry, unrelated-user denial,
   former-member denial, unlink/relink isolation, and expired login-token tests.
4. Local-store protection, backup exclusion, logout/account-switch wipe policy,
   device replacement, lost-key behavior, and user-facing identity-change UX.
5. TLS at every non-loopback endpoint, private registration/federation policy,
   rotated service credentials, bounded media/retention, sanitized observability,
   and a documented backup/restore procedure.
6. Independent security/privacy and license review before a production E2EE
   claim. Compilation and an internal two-user demo are not such a review.

Primary upstream references:

- [Matrix Rust cryptography audit](https://matrix.org/blog/2022/05/16/independent-public-audit-of-vodozemac-a-native-rust-reference-implementation-of-matrix-end-to-end-encryption/)
- [Matrix client SDK ecosystem](https://matrix.org/ecosystem/sdks/)
- [Trixnity source](https://gitlab.com/connect2x/trixnity/trixnity)
- [Synapse source and releases](https://github.com/element-hq/synapse)
- [Matrix client-server specification](https://spec.matrix.org/latest/client-server-api/)
