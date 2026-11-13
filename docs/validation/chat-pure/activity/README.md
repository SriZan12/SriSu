# Shared chat layout and Matrix activity — 2026-10-28

Frontend: `SriZan12/SriSu`, branch `chat-ui-pure`, base
`6d77bc5f534835b201cff00809d20b1184f2d06a` (uncommitted changes).
Backend: `SrizanKhadka/SriSu`, existing branch `codex/couple-chat-rebuild`, base
`6e0935c1c4f24267b9a956d259db5d113e9bb903` (pre-existing work preserved).
Design: approved [Pure preview](../reference.html), originally associated with
[Figma message page 1:5](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5).
The earlier live Figma connector was quota-limited; this follow-up uses the actual
saved approved source, not a new inferred Figma design.

## Changes

All six palettes use Pure's geometry, typography, header, group spacing, 12-hour
timestamps, read labels, Spark card, voice controls and composer. The existing
palette generation and light/dark colors remain intact. The mini theme previews
reflect the shared layout. Only chat is themed.

The encryption subtitle is replaced with real partner presence. Unknown/stale data
says **Status unavailable**, never a fabricated **Offline**. **Away** preserves
Matrix's `unavailable` presence value. E2EE, room security, replies, reactions,
uploads and playback continue through the existing paths.

Partner typing is transient, filters by room and partner identity, captures initial
sync, and clears on disconnection/background. The outgoing publisher sends no text,
uses a 10-second server lease, refreshes at most every 3 seconds during editing,
and stops after 5 seconds idle or on send/blur/leave/recording and authorization
changes. Failed ephemeral requests do not surface as failed messages.

## Verification actually run

- `python3 tools/workspace.py verify all`: passed core contract consistency, theme
  validation, Android unit tests, Android/iOS compilation, isolated Django system
  checks and the allowlisted backend tests.
- Android JVM/Robolectric native graphics: **448 tests, 447 passed, 1 opt-in live
  transport skip**. Includes all six palettes in light/dark, an explicit Offline
  partner, typing in an empty room, typing start/stop, history scroll preservation,
  320dp/200% text, constrained composer, read/group metadata and voice-speed action.
- iOS simulator test host: **61 tests passed**, including the activity unit tests,
  twelve theme renders, recovery, playback ownership, timeline, contrast and motion.
  Device: `SriSuCoupleChatEvaluation`, iOS 26.5, Apple silicon simulator
  `0055683D-7DBA-44C7-855D-4AA64123C522`.
- Backend isolated allowlist: **294 tests, 271 passed, 23 expected skips** (including
  database-specific cases). Synapse renderer test confirms presence is enabled and
  the private-profile configuration is retained.
- `.venv/bin/python tools/verify_matrix_activity.py` in the backend: passed against
  a disposable pinned Synapse 1.162.0 container, temporary SQLite database and four
  synthetic users. Verified online/away/offline, authorized partner sync typing,
  explicit stop, server expiry, denied identity spoofing, denied unrelated presence
  reads/typing writes, and no partner activity in an unrelated user's sync. Existing
  development data, credentials and rooms were not used. Temporary container and
  database were removed. The script honors server rate limits; Synapse returns 400
  with an explicit authorization error for typing identity spoofing.
- Local development `synapse` service restarted to activate the renderer change;
  generated configuration reports `presence.enabled: true`. No production deploy.

[Matrix presence and typing protocol](https://spec.matrix.org/v1.18/client-server-api/#typing-notifications)
and the pinned Trixnity 5.9.2 source informed transport integration. No Django API
or schema change is needed. Other homeservers need the same configuration change
and restart; clients tolerate presence-disabled servers and older clients.

## Visual evidence and limits

- [Android — all light palettes](activity-all-light.png)
- [Android — all dark palettes](activity-all-dark.png)
- [iOS — all light palettes](ios-all-light.png)
- [iOS — all dark palettes](ios-all-dark.png)
- [Small screen with 200% text](activity-small-large-text.png)
- [Reading older messages during typing](activity-history.png)
- [Offline partner](activity-partner-offline.png)
- [Typing with no messages](activity-empty-typing.png)

Reviewed the rendered grids for shared alignment, spacing, bubble grouping,
metadata and clipping. Presence and typing intentionally extend the reference.
Platform emoji rendering differs; all text is synthetic fixture content. At large
text sizes the composer stacks controls and history remains scrollable.

These are real Compose platform test renders, not an authenticated two-phone
Android↔iOS session. Live API checks do not prove native E2EE/media exchange. The
initial Pure native keyboard automation limitation remains: no fresh native IME
transition, device recording/playback output or frame-time benchmark is claimed.
Matrix presence follows the foreground conversation sync lifecycle and homeserver
expiry; it is not an app-wide online heartbeat. Other active devices can keep a
partner online after one device leaves chat.
