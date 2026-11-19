# ADR 002: gate chat E2EE behind platform libsignal adapters

Date: 2026-10-07. Status: superseded on 2026-10-08 by
[ADR 003](003-matrix-couple-chat.md); retained as the rejected libsignal
evaluation record, not an implementation or release approval.

## Context and current-state audit

SriSu's chat brief requires end-to-end encryption (E2EE), including private
text, replies, reactions, voice notes, media and thumbnails. The current system
does not meet that requirement:

- The Django backend persists message text and ordinary media metadata/URLs. It
  has no device identity, prekey, session, encrypted-envelope or per-device
  mailbox model.
- The KMP application has no libsignal dependency. Its iOS application embeds a
  generated Compose XCFramework directly and has no CocoaPods or native crypto
  injection boundary.
- KVault protects authentication credentials. Room/SQLite chat state is not
  automatically encrypted and is not a transactional Signal protocol store.
- Existing [system ADR-010](../system-design/06-decisions.md#adr-010-trusted-server-encryption-model-pending-explicit-approval)
  selected a trusted-server model while E2EE remained unapproved. TLS and
  server-side/storage encryption protect different threats and must never be
  presented as E2EE.

This record defines the smallest defensible experimental boundary for evaluating
E2EE. Until every release gate below passes, the existing trusted-server behavior
remains the only implemented behavior and no SriSu UI, store listing or support
material may claim that chats are end-to-end encrypted.

## Decision

Use the maintained [Signal libsignal repository](https://github.com/signalapp/libsignal)
behind SriSu-owned platform adapters. Do not implement a new cryptographic
protocol and do not consume libsignal's internal Rust, JNI or C bridge API from
shared Kotlin.

```text
commonMain
    Chat repository and opaque, versioned encrypted-envelope contract
    E2eeEngine interface (no libsignal types)
        |
        +-- androidMain adapter -> official libsignal Java/Android artifacts
        |
        +-- Swift host adapter  -> official LibSignalClient CocoaPod
                                  -> small injected ObjC-compatible facade
```

The two adapters must pin the same reviewed libsignal release. Cross-platform
test vectors and Android-to-iOS/iOS-to-Android conversations are release
requirements. Keeping libsignal types behind the interface limits changes when
the upstream bridge changes and prevents crypto state from leaking into UI,
navigation or general network models.

The official project exposes Java and Swift APIs backed by Rust, but explicitly
states that use outside Signal is unsupported and that APIs and bridge layers can
change without notice. Its [README](https://github.com/signalapp/libsignal#readme)
documents the Android artifacts and support caveat. The supported Apple path is
CocoaPods with `LibSignalClient`; its
[Swift README](https://github.com/signalapp/libsignal/blob/main/swift/README.md)
states that Swift Package Manager use is unsupported except for local
development. The [podspec](https://github.com/signalapp/libsignal/blob/main/LibSignalClient.podspec)
is the authoritative deployment/linkage input. Kotlin also documents that a
pure Swift library is not directly consumable from Kotlin unless it exposes an
Objective-C-compatible API; see the official
[iOS dependency](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-dependencies.html)
and [Objective-C interop](https://kotlinlang.org/docs/native-objc-interop.html)
guides.

The archived
[libsignal-protocol-java](https://github.com/signalapp/libsignal-protocol-java)
and [libsignal-protocol-c](https://github.com/signalapp/libsignal-protocol-c)
repositories are not acceptable dependencies for new work.

## License and dependency policy

Libsignal is published under
[AGPL-3.0-only](https://github.com/signalapp/libsignal/blob/main/LICENSE). The
official repository does not advertise a separate commercial license or an
application-store exception. SriSu currently has no repository license that
settles compatibility with its intended distribution. This record is not legal
advice: the product owner must obtain a documented licensing decision covering
source distribution, backend/network obligations and Apple/Google store terms
before libsignal enters a release build.

The unsupported-external-use warning also creates an engineering ownership
cost. SriSu must pin exact versions, monitor upstream security releases, maintain
both adapters, and budget for breaking migrations. No floating dependency or
unreviewed automated upgrade is permitted.

## Threat model and unavoidable metadata

When complete, the target protects message content and attachment keys from the
network, storage provider, application server and ordinary server operators. The
server still authenticates accounts and enforces current couple membership; E2EE
does not replace authorization.

The target does not protect content on an unlocked or compromised endpoint, from
screenshots or recipient export, or from a malicious conversation participant.
Remote deletion cannot retract previously displayed or downloaded plaintext.
Endpoint malware, coercion and operating-system compromise remain outside this
protocol boundary.

The service necessarily observes some routing metadata: account and device IDs,
current relationship membership, IP/network logs, submission and delivery time,
ciphertext and attachment sizes, mailbox operations, retry identifiers and
possibly read/delivery events. Ciphertext length and traffic timing can reveal
patterns even when content is opaque. Collect the minimum required metadata,
apply explicit retention limits, avoid content-derived logs and never describe
E2EE as anonymity or metadata secrecy.

The encrypted inner payload must bind a schema/protocol version, conversation
UUID, relationship membership epoch, client operation/message UUID,
sender/recipient device IDs, content type, revision and referenced message IDs.
Recipients validate that context against authenticated outer routing. The server
continues to derive access from the authenticated identity and server-side
membership, never a client-provided owner or couple flag.

## Protocol state and lifecycle

Use libsignal's current protocols rather than treating encryption as a stateless
message helper:

- [PQXDH](https://signal.org/docs/specifications/pqxdh/) defines asynchronous
  establishment using identity, signed, one-time and post-quantum prekeys.
- [Double Ratchet](https://signal.org/docs/specifications/doubleratchet/) advances
  keys for each message and defines bounded out-of-order/skipped-key handling.
- [Sesame](https://signal.org/docs/specifications/sesame/) describes the device
  lists, per-device sessions, mailboxes and identity-change handling needed for
  asynchronous multi-device messaging.

Local identity keys, prekeys, remote identity trust, sessions and the exact
outgoing ciphertext must be stored transactionally. An ambiguous retry reuses the
same operation ID and ciphertext; it must not advance the ratchet again for the
same logical send. Incoming authenticated decryption, replay/duplicate handling,
new session state and durable local application must also commit atomically.
Platform keystore/keychain material should wrap an encrypted local protocol and
message store; a string preferences wrapper alone is insufficient. Ordinary cloud
backup must not silently copy decryptable protocol state. Platform controls are
described by the official [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
and Apple [file data protection](https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files)
documentation; using those controls does not remove the need for an encrypted,
transactionally consistent protocol store.

For the first experimental implementation, allow exactly one active E2EE device
per account and enforce that invariant on the server. Device replacement or app
reinstallation creates a new identity, revokes the old registration and produces
a prominent safety-number/key-change warning for the partner. Old history is
unrecoverable unless a separately reviewed E2EE transfer or backup is implemented.
Do not share a private identity key between devices to simulate multi-device.

True multi-device support is deferred until SriSu implements Sesame-style fresh
device lists, one session and envelope per recipient device, copies for the
sender's other devices, device addition/removal and clearly defined per-device
receipt aggregation. A socket acknowledgement is not delivery. `delivered`
should require at least one eligible recipient device to authenticate, decrypt
and durably apply the message.

Logout revokes the device registration/prekeys/mailbox and wipes local protocol,
plaintext cache and outbox keys. Unlinking or partner replacement advances the
relationship membership epoch, rejects old sessions/outbox work and never reuses
the old cryptographic conversation. Account deletion revokes devices and schedules
server ciphertext/blob deletion, without promising deletion from another user's
device. Offline and partially completed lifecycle operations need reconciliation
tests rather than optimistic UI-only cleanup.

## Attachments are intentionally undecided

Libsignal does not provide SriSu's complete attachment format and lifecycle. Do
not enable photos, video, thumbnails or voice under the E2EE feature gate until a
separately reviewed streaming authenticated-encryption format is selected and
tested on both platforms.

The required shape is client-side metadata removal, compression/transcoding,
thumbnail/waveform generation and encryption before upload. Original and
thumbnail use independent random keys. Only opaque ciphertext is uploaded; the
locator, authenticated hash, key and media metadata travel inside the encrypted
message. The server may enforce authenticated ownership, ciphertext byte limits,
quota and expiry, but cannot validate decoded dimensions or MIME, scan content,
remove EXIF, transcode media or generate a private thumbnail.

[libsodium secretstream](https://doc.libsodium.org/secret-key_cryptography/secretstream)
is a maintained candidate, not an accepted decision. Adopting it would add another
native dependency and still requires an exact chunk/envelope specification,
nonce/key lifecycle, corrupted/truncated-stream behavior, retry rules,
interoperability vectors and security review. The official
[Messenger E2EE overview](https://engineering.fb.com/wp-content/uploads/2023/12/MessengerEnd-to-EndEncryptionOverview_12-6-2023.pdf)
and [Viber encryption overview](https://www.viber.com/app/uploads/Viber-Encryption-Overview.pdf)
provide primary-source examples of encrypting an attachment with an ephemeral key
and carrying its pointer/key inside an encrypted message; they are design evidence,
not a primitive to copy blindly.

## Non-production feature gate

The experimental path must be fail-closed and isolated from ordinary plaintext
chat:

1. A backend-controlled, environment-scoped allowlist enables E2EE only for
   synthetic/internal test accounts. Production defaults to disabled; a local
   client flag cannot override it.
2. The client requires an explicit server protocol capability, successful local
   device registration and an exact supported envelope version before entering an
   encrypted conversation.
3. Encrypted and plaintext messages use distinguishable versioned contracts and
   storage. There is no silent downgrade or fallback to plaintext after an E2EE
   failure.
4. Existing plaintext history is kept separate. No automatic re-encryption or
   migration is claimed until provenance, retention and user-visible transition
   behavior are approved.
5. The first pilot is text-only, one active device per user, two current partners,
   no backups and no history recovery. Attachments, voice, linked devices and
   production privacy copy remain disabled.
6. Diagnostics may record opaque IDs, protocol versions and failure categories,
   but never keys, plaintext, decrypted payloads or raw protocol records.

This boundary permits integration and failure-recovery testing without telling
users that an incomplete system protects their production conversations.

## Production release blockers

Every item below blocks an E2EE claim or production enablement:

1. A documented AGPL/application-store/source-distribution decision.
2. A build spike proving the same pinned libsignal release in Android and iOS,
   including the Swift/CocoaPods injection boundary, all supported ABIs, linker
   behavior, reproducible builds and measured application-size impact.
3. Backend device/prekey/mailbox/encrypted-envelope contracts, authenticated
   registration/revocation, rate limits, atomic one-time-prekey consumption and a
   reviewed legacy-plaintext migration/retention policy.
4. Crash-safe transactional protocol stores, encrypted local content storage,
   backup exclusion, durable exact-ciphertext outbox and account-scope cleanup on
   both platforms.
5. Safety-number/QR verification, identity-change UX, reinstall/replacement,
   logout, unlink, revocation, account deletion and lost-key behavior.
6. A selected and reviewed attachment streaming-AEAD format before any media or
   voice is enabled.
7. Android-to-iOS and iOS-to-Android tests for bootstrap, delayed and out-of-order
   delivery, skipped-key bounds, duplicates/replay, tampering, simultaneous sends,
   process death during send/decrypt commits, ambiguous retries, identity changes,
   reinstall, revocation, unlink, stale membership epochs, corrupted attachments
   and key loss.
8. Independent security and privacy review of the implementation, protocol
   integration, metadata/retention behavior and release copy.

Passing compilation or demonstrating one encrypted text exchange is not evidence
that these blockers are resolved. If the project cannot accept libsignal's license
or unsupported-use risk, stop and evaluate another maintained, independently
reviewed protocol implementation under a new ADR; do not weaken this gate or invent
custom cryptography to meet a schedule.
