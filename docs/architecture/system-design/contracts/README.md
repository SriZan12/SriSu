# Draft contract subset — proposed 0.1

These are **documentation specifications**, not implemented endpoints or a full
inventory of existing APIs. They describe the B2/C1 refresh and chat recovery seam
so compatibility, identifiers and failure behavior can be evaluated concretely.

- `openapi.proposed.json`: OpenAPI 3.1.0, refresh, message send/history and changes.
- `chat.proposed.schema.json`: JSON Schema 2020-12, shared message/change definitions.
- `realtime.proposed.schema.json`: known v2 send/ack/change socket envelopes.

All example identities/content are synthetic. There is intentionally no live
server URL, usable credential, current mobile code generation or CI change.
Other feature contracts are specified in [the design](../03-contracts.md) and
must be completed as schemas before their implementation slice changes behavior.
These drafts must not be mistaken for complete Auth/Chat/Social API coverage.

Accepted schemas move to the backend's single contract authority during approved
implementation; mobile then pins the published artifact digest. Do not maintain
separate editable definitions in both repos. External JSON Schema references here
are relative files and must resolve locally during validation.

The socket schema validates known event types. Runtime clients also need a tolerant
outer-envelope decoder: an unknown additive event is recorded without private
payloads, and a detected sequence gap triggers HTTP reconciliation. Do not discard
the connection merely because a newer server introduces a safe unknown event.

The shapes do not enforce object authorization, row locking, idempotency,
durability, visibility or ordering by themselves. Those are operation invariants
and fault/concurrency tests in the canonical design.
