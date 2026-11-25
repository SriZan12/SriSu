# Personal partner nicknames

Implemented in the original checkouts on `codex/home-refresh-partner-nicknames`.
Frontend base: `3872d11ca4d17a40691040830a28efed918bae74`; backend base:
`8e5c91645caae34541d5d523afab0dc0b5b77c2f`. Prior local dark-mode refinements are
preserved. The frontend and backend changes are published together against
`dev-core-architecture`; deployment and production migrations remain separate.

## Behavior

Settings → Together → Partner → Nickname and Our profile → Partner nickname
(below the names/date, before the statistics) open the same compact editor. Each
partner sets their own display name for the other person. Save, edit and Remove
nickname use server persistence, independent of the other partner's preference.
The editor explains that setting/changing a nickname sends the chosen value to
the partner in a private notification, as explicitly requested by the user on
25 November. This is a personal display preference rather than a public profile
rename. Canonical names remain visible in identity/profile details;
search, account editing and pre-link identity are unchanged.

Nickname display covers Home, Chat (header, composer, typing and reply labels),
Questions, Challenges, Plans, Moments, connected partner/couple profiles and
notification actor labels. Typed user IDs and current membership select the alias.
Messages, captions, question answers, story values and Plan titles are never
searched/replaced. Plan assignments continue to store IDs; story choice labels
can show a nickname while their existing stored value stays canonical.

Removing a nickname restores the real name, including already-loaded notification
actor spans. Missing actors and literal message content never receive an alias.
Emoji initials are read as a complete Unicode code point rather than half a
surrogate pair. Long labels wrap within the existing responsive layouts.

## State, synchronization and privacy

`PartnerNames` owns one reactive, in-memory display projection scoped to the
authenticated account and current couple. It refreshes when the app enters the
foreground and on Home's swipe-to-refresh; a successful Settings load/save updates
it immediately. Reads are deduplicated, and older revision responses cannot undo
a newer save. Session or relationship changes clear the projection and discard
late responses. A failed refresh keeps the already verified current label;
before the first successful load in a new process, canonical names are used.
This is not a new real-time synchronization channel or a persisted offline alias
cache. On a second device, foregrounding or refreshing fetches the saved value.

The backend stores the setter's nickname on their current membership, separate
from legacy shared nickname metadata. Settings remains setter-only; the newly
authorized notification reveals the current chosen value to the other partner.
Unrelated users, discovery serializers and public profile projections cannot read it. Unlinking
clears both private labels; a later connection starts without inherited nicknames.
This is authorized server-held metadata, not end-to-end encrypted content.

Save uses both connection revision and independent nickname revision to reject
stale/concurrent changes. Same-value retries are idempotent. A failed or timed-out
save reconciles with GET before the next attempt; drafts remain editable and a
failed reconciliation offers Retry connection. A discovered disconnection also
refreshes the app's membership/navigation state.

Authenticated notification previews resolve names for their recipient. The inbox
adds optional actor IDs, canonical fallback names and explicitly typed actor text
segments, allowing cached UI labels to update safely. Generic push fallback
payloads and privacy/redaction preferences remain unchanged. Already displayed OS
alerts do not rename retroactively. Android's existing protected-preview reader
uses the personalized server title. The existing iOS generic notification path
does not gain a private-preview extension as part of this work.

## Couple-profile editing and partner alerts

Follow-up branch: `codex/couple-profile-nickname-alerts` in both original checkouts,
based on frontend `07b8ce9f38d951a0c4de91321a1dc5e8580bf611` and backend
`561a894fa5a94f4c532bab914fb9e6a71f881841`. The profile entry is owner-only and is
omitted from visitor/public-preview/answer screens. Settings context must match
the displayed couple. Loading/failure is localized to the nickname row; a failed
load offers retry, never a misleading unlinked state. The existing Settings
ViewModel owns validation, revisions, saving, reconciliation and display-name
updates; both surfaces share `PartnerNicknameEditorHost`.

`nickname.changed` is emitted transactionally for a nonempty changed nickname.
Same-value retries, clearing, invalid writes and rolled-back saves emit nothing.
The existing Requests notification preference applies. The protected preview and
inbox show the chosen nickname; generic push fallback contains neither name nor
nickname. Tapping opens the current couple profile. The event stores membership
and revision metadata, not a nickname snapshot: changed/removed nicknames and
ended/replaced relationships invalidate old inbox rows, previews and destinations.
Already displayed OS alerts cannot be retracted. Existing nicknames are not
backfilled into notifications.

This follow-up adds no migration. It adds optional `notify_partner` to the save
request, defaulting to false to preserve the old editor’s privacy promise. Both
updated editors send true after displaying the disclosure. Deploy the backend
before the updated client: the old backend rejects unknown request fields. Older
clients can still save nicknames without alerts and parse the couple destination,
but their inbox allow-list omits this new event. Native iOS remote
push delivery retains the existing backend capability limitation; in-app inbox
support is shared across Android and iOS.

## Contract and deployment

Backend-owned contract (also pinned in this frontend):

- GET `/api/social/settings/` retains `settings-1` and canonical `partner.name`,
  adding nullable `nickname` and `nickname_revision` (starts at 1).
- PUT `/api/social/settings/partner-nickname/` accepts `connection_id`,
  `expected_revision`, `expected_nickname_revision` and `nickname`.
  Empty string/null clears it. The response is the current Settings context.
- The backend normalizes NFC and whitespace, limits to 40 Unicode code points,
  and rejects control, direction-override and invisible-only input. Emoji and
  non-Latin scripts remain supported. The client provides immediate length/control
  feedback; server validation remains authoritative.

Apply backend migration **`social.0025_private_partner_nickname`** and deploy the
backend before the client. Older clients ignore additive fields. On an older
backend the new editor remains unavailable rather than pretending a local-only
nickname was saved. No account, content or chat-encryption schema changes.

## Design and verification

Uses the approved Home and Settings compositions, existing spacing/type/theme
tokens, normal touch targets and current light/dark palettes. The new controls are
intentional requested additions; there is no new Figma frame or pixel-parity claim.
Existing design reference: `LztysD1YvINX7RwZpnhhTt`, design-system node `0:1`;
live access was previously quota-limited. See the [verification record](../validation/home-nicknames-2026-11-25/README.md).
