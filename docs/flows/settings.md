# Settings

## Scope and sources

Implemented locally on `codex/settings-ui` in both repositories. Bases: frontend
`f810047d939327974efb011096e5932be8c07e33`, backend
`e9878cdda1a5d30986aa6295ff633168e66d5684` (`dev-core-architecture`).
Visual authority: user-supplied `Frame.png` and `Frame (1).png` (Settings, disconnected
and expanded connected states). Figma file `LztysD1YvINX7RwZpnhhTt`; Settings node
not supplied; live frame inspection unavailable under the existing Figma tool quota.
No claim of live Figma parity. Status bar, phone frame and textured surrounds are
presentation chrome, excluded from the implementation.

## Design and research decisions

The reference is normalized to a phone layout: 20 dp gutters, minimum 60 dp rows,
22 dp line icons, 8 dp row gaps, existing 16 dp medium shape, 16/24 body typography,
20 sp title. Text wraps and increases row height. The list respects safe areas,
scrolls, and caps width at 560 dp on wider displays. White/warm-neutral rows and a
soft lavender Partner group come from existing theme roles; dark mode is derived.
The connected group expands without decorative motion. Real names/days are used;
connected days come from the connection acceptance time, not anniversary date.

Research:
- [Apple Settings](https://developer.apple.com/design/human-interface-guidelines/settings):
  keep options focused, respect system accessibility, leave task-specific settings near the task.
- [Android app languages](https://developer.android.com/guide/topics/resources/app-languages):
  language choices must correspond to actual shipped translations. Only English is currently bundled;
  Language explains this, rather than presenting a nonfunctional picker.
- [Apple account deletion](https://developer.apple.com/help/app-review/guideline-reference/5-1-1-account-deletion):
  account-creating apps need in-app deletion initiation. SriSu has no complete deletion workflow.
  This remains a release requirement: define shared-data retention, media purge, Matrix identity/key
  lifecycle, reauthentication and a tested deletion job before adding the destructive control.

## Entry points and behavior

25 November addition: [personal partner nicknames](partner-nicknames.md) adds a
server-synced personal nickname editor inside the connected Partner group and
below the identity header in Our profile. Both use the same editor and revisioned
save flow. Setting/changing the nickname privately notifies the partner with the
chosen value; the editor states this before saving. Removing it sends no alert.

Profile's gear -> `ProfileNav.Settings`. Inbox gear continues to Notifications.
You: Account -> personal profile editor / visibility; Notifications -> existing
server-backed preferences; Language -> accurate supported-language information.
Together: connection context loads independently; failure is not treated as unlinked.
Unlinked -> existing Find Partner flow, including invitation handling. Linked ->
partner detail, personal shared-activity alerts, confirmed Disconnect. Shared
preferences -> existing Plans/reminders and couple sharing controls; unlinked users
see Connect. No user can change their partner's notification preferences.
App: Downloads -> image-cache clearing with confirmation (Coil cache only; no chat
DB/media deletion); Appearance -> persisted device System/Light/Dark, with matching system-bar contrast (chat palettes
unchanged); Accessibility -> system accommodation guidance; Support -> offline
troubleshooting and real permission/notification destinations; Privacy & Security
-> existing visibility, permissions and optional diagnostics. Sign out confirms and
uses existing session revocation/cleanup.

Useful additions held for product details: verified support email and public policy/
terms URLs (requested from user); full translations; account deletion above; session
management/data export. No invented email, legal document or decorative preference.
Plan reminders remain per-device and configured on the relevant Plan; Moment audience
is chosen at publication, not silently broadened by Settings.

## Backend contract and deployment

See backend `docs/settings.md`. Additive authenticated GET `/api/social/settings/`
and POST `/api/social/settings/disconnect/`; existing relationship service is reused.
No database migration. Deploy backend before client; older servers show retryable
connection-unavailable state. The rest of Settings remains usable. Legacy relationship
calls remain compatible. Responses are private/no-store; unknown fields rejected.
Expected connection revision prevents stale confirmation and a retry cannot disconnect
a later relationship. Account/session change clears in-flight client state.
Partner projection validates both active members against the connection participants;
inconsistent legacy records return a retryable conflict without partner details.
Both routes are included in the backend-owned HTTP inventory and its pinned client copy.

## QA hardening

Notification writes bind to the session at the time of the tap, before coroutine
dispatch or mutex waiting; queued actions cannot transfer to a newly signed-in account.
Appearance observation retries temporary DataStore/Okio IO failures with bounded
backoff, preserving the last known appearance and resuming after storage recovers.
Disconnect confirmation is disabled during refresh and dismissed when its partner
snapshot/revision changes. See the validation record for regression evidence and limits.

## Verification

Results and screenshot evidence are recorded after verification in this folder. Native
UI fixtures are synthetic; native layout tests do not establish live-device/backend
coverage. No remote publication or deployment is part of this task.
