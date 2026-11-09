# Home — couple space

Implementation: 9 November 2026, local branch `codex/home-couple-space` in both
repositories. See [Stage 3](../design/home-redesign/stage-3.md) for provenance and
[validation](../validation/home-couple-space/README.md) for checks and limits.

## Composition and entry points

The approved four attachments and Stage 2 preview define this Home. The live Figma
Home nodes still describe an older direction. Home uses a scoped white/lilac
palette, Satoshi type, content-driven heights and a scrolling column; it does not
shrink the full long reference into one phone viewport. Pure chat, illustrated
Quick Share and the Palette picker retain their appearance.

1. Identity and current requests, followed by two partners' Moments entries.
   The own-avatar plus opens the existing Moment creator. Tapping a status opens
   the author-only status picker; the connector opens the couple profile/date path.
2. The shared question opens its existing Spark detail. Locked artwork is fixed
   decoration: Home never receives an answer payload or ciphertext.
3. The latest eligible partner Spark/message opens its own destination. Open chat
   enters the existing Matrix conversation. Ask and quiet-day suggestions offer
   drafts, never automatic sends.
4. Coming up chooses one relevant Plan or shared idea. View all opens Plans & ideas,
   with the existing agenda, requests, current ideas and suggestion controls.
5. Your challenge opens the existing detail; a completed challenge offers the
   catalogue. Days, revealed questions and completed Plans use actual data.

The bottom destinations are Home, Questions, Chat and Profile. Questions reuses
the Spark catalogue/current/history screen. Explore remains available through
Profile and the unpaired Home. Chat without a connection enters partner linking.
The bell opens current Plan, Challenge and profile-sharing requests; it is not a
notification delivery history.

## State and interaction contracts

| Area | Rule | Action and return |
| --- | --- | --- |
| Daily question | One shared slot, stable couple timezone; unfinished carries until answered by both or skipped; completed/skipped remains for that day; catalogue revisits are labelled | Existing answer/reveal/skip flow; contextual chat retains the originating feature in the navigation stack |
| Private status | Optional preset, author-only, expires after 24 server hours; no public-profile mood change | Save/clear with revision and retry ID; refresh partner state while Home is visible |
| From partner | Compare local Matrix message timestamps with actual partner Spark activity; automatic daily assignment is not partner activity | Message opens its event in chat; Spark opens its own question |
| Quiet day | Requires fresh sources; unavailable sources are not interpreted as no activity | Thinking of you/Coffee/Miss you offer editable chat drafts |
| Coming up | Today agreed → future incoming → next agreed → outgoing → mutual idea → partner idea → own idea → empty | Incoming acceptance is explicit; pending reschedules retain the original agreed time; unresolved expired invitations remain reachable through requests |
| Shared idea | Coffee/Walk/Movie saves an unscheduled original-couple idea; one open idea per author | Me too/undo records interest only; both interested opens the existing date/time editor; saving creates a separate Plan invitation |
| Reminder | Existing personal device reminder, never a promise to remind both partners | Existing permission/scheduling feedback; no success until scheduling succeeds |
| Challenge | Active or last completed real challenge | No invented weekly release or deadline; Choose another opens the catalogue |

Shared ideas have no new push-notification channel in this release. The partner
sees them when Home refreshes. A Nudge opens a generic Plan-related chat draft;
it does not currently attach a specific Plan reference.

## Empty, stale and failure states

- Unpaired: own identity, connection action and existing invitation flow. No
  invented partner, anniversary, couple counters or conversation history.
- Sent/received invitations: distinct pending states and existing review/cancel
  paths. An incoming invitation is never accepted by opening Home.
- Newly connected: missing date/status remains a prompt, with empty history and
  a first shared question when the Matrix relationship epoch is ready.
- Initial loading, recoverable failure and a genuine empty response are separate.
  Partial feature failures retain labelled last-synced data and offer retry.
  Unknown counters show an em dash, not zero.
- Authentication/authorization loss clears private projections. Session,
  relationship and generation guards reject late callbacks. Original membership
  audience checks remain server-side.
- Pending mutations retain the exact operation ID, values and account/origin/
  member scope across recreation. Uncertain results can be retried; conflicting
  revisions require fresh state. Independent mutation groups can still proceed.
- Restored chat text/reply/Moment context is never silently replaced by a Home
  suggestion. Explicit Keep/Append/Replace handles the conflict; replacement
  clears the old context and the existing byte limit remains enforced.

## Matrix and privacy

Home obtains a summary lease rather than the foreground chat owner. It never
marks the room read, closes another owner's room, or uploads message previews to
Django. The local summary excludes own messages, reactions, deleted events and
Plan/Challenge reference cards. An unread boolean produces a **New** badge;
the reference's numerical badge is intentionally not fabricated.

Leaving Home releases only its own summary. An active conversation remains the
owner; after it closes, a still-visible summary may resume. The existing Matrix
encryption, upload, playback, reactions and reply paths remain in Chat.

## Adaptive layout

The column scrolls above the floating navigation. At large text, actions wrap,
statistics stack, decoration yields space, and navigation becomes two columns
with complete labels. The own-avatar plus is visually compact within a 44 dp
target. Question wrapping is measured from the actual text rather than hard-coded
for the example. Home adds no decorative motion, so reduced motion does not hide
feedback or change a required interaction. Native accessibility/hardware checks
still have the limits recorded in validation.
