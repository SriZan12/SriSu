# SriSu Home behavior and compatibility decisions

Stage 1, 2026-11-09. This supplements the [master brief](brief.md). Approved decisions
are distinguished from recommended defaults and verified implementation facts.
Production code, contracts and data are unchanged.

## Confirmed decisions

| ID | User decision | Consequence |
| --- | --- | --- |
| D1 | Daily shared question; unfinished stays until done or skipped | Add authoritative daily assignment around the existing single-open Spark lifecycle; no midnight loss or question backlog |
| D2 | No streak in this release | Remove the flame/count; do not replace it with a fabricated score |
| D3 | Shared unscheduled date ideas with Me too | Add an original-couple idea lifecycle; agreement on the idea is distinct from accepting a dated Plan |

The answers were supplied explicitly in this conversation. The user subsequently
authorized Stage 2 previews using these decisions and the defaults below. They
remain subject to visual review and do not authorize production implementation.

## Inspected baseline and source limits

| Source | Branch and commit | Working state at inspection |
| --- | --- | --- |
| Frontend `/Users/srijan/studioprojects/SriSu` | `dev-core-architecture`, `c1abef982b0e3a2b87bf342d08c526bc65c25bd8` | Clean before these new documentation files |
| Default backend `../SriSu-backend` | `codex/couple-chat-rebuild`, `6e0935c1c4f24267b9a956d259db5d113e9bb903` | Existing modified/untracked work; preserved |
| Consolidated backend `../SriSu-backend-consolidated-20261029` | `dev-core-architecture`, `11891049ef323b6df3c6ee3c8f0688442b18c13f` | Existing `.DS_Store` modification; preserved |

Repository identities: frontend `SriZan12/SriSu`, backend `SrizanKhadka/SriSu`.
`tools/workspace.py status --remote` read live integration heads: frontend
`dev-new-theme` at `3fa6822ea05578d64cca8891c5e3fdb0a831897a`, backend `dev` at
`ecc8592307114e7d154bda9612e6826e2ca8ba90`. These are not the working feature
branches. No checkout was switched, reset, merged, fetched or deployed.

The default backend does not contain the notification module described by the
frontend. The consolidated checkout does, including Android Matrix push. Comparing
the inspected Spark/Plan service files confirms additive notification producers
there; the key single-open/reveal/Plan-action semantics are unchanged. Use the
consolidated code as the reference for notification-aware planning. Before Stage 3,
select and revalidate the intended frontend/backend implementation pair and runtime
target; do not silently redirect `SRISU_BACKEND_DIR` or overwrite dirty work.
This is not a claim that any current remote deployment is running these commits.

Key evidence (paths are relative to this document):

- [Home state](../../../composeApp/src/commonMain/kotlin/com/srisu/srisu/features/home/dashboard/state/HomeUiState.kt): conversation is explicitly null; current mapping uses Moments, current Challenge and nearest Plan.
- [Home ViewModel](../../../composeApp/src/commonMain/kotlin/com/srisu/srisu/features/home/dashboard/vm/HomeViewModel.kt): per-source loading, refresh and session lifecycle.
- [Home routes](../../../composeApp/src/commonMain/kotlin/com/srisu/srisu/navigation/graph/HomeGraph.kt): Ask → chat; bell → Plan invitations; typed feature destinations.
- [Matrix models and interface](../../../composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/matrix/MatrixChatModels.kt) and [repository](../../../composeApp/src/commonMain/kotlin/com/srisu/srisu/features/chat/matrix/TrixnityMatrixChatRepository.kt): owner-scoped room lifecycle, messages and read operations; no Home summary/unread-count interface yet.
- [Sparks contract](../../../../SriSu-backend-consolidated-20261029/docs/sparks.md), [API](../../../../SriSu-backend-consolidated-20261029/social/api/sparks.py), [service](../../../../SriSu-backend-consolidated-20261029/social/services/sparks.py): 24 curated questions, one open Spark, exact retries, immutable answers, withheld partner ciphertext, current audience/epoch.
- [Plans API](../../../../SriSu-backend-consolidated-20261029/social/api/plans.py) and [service](../../../../SriSu-backend-consolidated-20261029/social/services/plans.py): future-dated creation, invite/propose/accept lifecycle, current-state authorization and revisions. Home returns nearest future agreed plan plus invitation count.
- [Challenges flow](../../flows/challenges.md) and [API](../../../../SriSu-backend-consolidated-20261029/social/api/challenges.py): one live activity, each confirms own part, history for completed items, no scheduled deadlines/releases.
- [Plans reminder behavior](../../flows/plans.md): per-account/device presets, generic local notifications, no shared scheduling guarantee.
- [Notification API](../../../../SriSu-backend-consolidated-20261029/notifications/api.py), [service](../../../../SriSu-backend-consolidated-20261029/notifications/services.py), [deployment record](../../validation/notifications/matrix-push-20261030.md): existing capability-gated delivery and authorized resolution, not a user-facing historical inbox. Apple/APNs coverage is not established by Android delivery records.
- [Profile sections](../../../../SriSu-backend-consolidated-20261029/social/services/couple_profile_sections.py), [user moods](../../../../SriSu-backend-consolidated-20261029/utils/choices.py), [Moments](../../flows/moments.md), [partner linking](../../flows/partner-linking.md), [authentication](../../flows/authentication.md): identity/date/privacy and unpaired/guest routes.

## Compatibility map

| Area | Verified foundation | Required work |
| --- | --- | --- |
| Identity and duration | Authorized members/photos and optional anniversary | Home composition; unify count convention without silently changing old API meanings |
| Status speech bubbles | Existing profile mood is an optional account enum, not an expiring couple-only status | Recommended small separate private status extension; do not repurpose publicly visible profile mood |
| Daily question | Secure Spark lifecycle exists; no daily schedule | D1 requires daily assignment metadata, stable couple day/timezone, catalogue policy and concurrency protection |
| Latest partner item | Matrix messages/references and domain metadata exist | Client summary and real unread cursor, read-only Home observation, deduplication and freshness states |
| Questions/Ask | Sparks catalogue/detail/history and chat exist | Questions root navigation to existing feature; Ask opens chat composer |
| Coming up | Plan lifecycle and paginated agenda exist | Select richer states from invitations/today/upcoming data; current Home response alone is insufficient |
| Shared ideas | Plans require a future start time | D3 requires separate unscheduled idea metadata and safe link to Plan creation |
| Nudge / quiet-day chips | Existing encrypted composer/send pipeline | Recommended draft handoff; no dedicated instant-send/nudge notification product in this release |
| Reminders | Personal offsets off/15m/1h/1d exist | Correct copy and reuse picker; no promise to schedule the partner's device |
| Challenge completion | Mutual participation and history exist | Home needs latest-completed history fallback; no weekly scheduler |
| Story counters | Anniversary and some profile Plan counts exist | Define exact counters; server aggregate revealed questions/completed Plans for current audience, not page-length counts |
| Bell | Push delivery machinery and pending domain actions exist | Recommended current Requests sheet with truthful action count, not delivery history; client composition or bounded metadata projection |

Any new metadata projection is a proposal, not an endpoint currently present.
Prefer feature-owned changes. Do not create a broad Home service containing chat
plaintext, answer envelopes or copied domain state. Additive schema/contract work
and compatibility tests are required for new persistent capabilities.

## Recommended defaults

### Question cadence and one open Spark

- Use one server-authoritative shared day and a saved IANA couple timezone.
  Initially propose the first participating device's timezone and expose it in
  question details; phone travel/clock edits must not silently change the couple's
  day. Explicit timezone changes affect future assignments, not the open question.
- Treat D1 as a daily opportunity, not a daily expiry. Do not create missed-day
  backlog. An open question is the hero until both answer or either explicitly
  skips using the existing skip rule. Show “Your question” for a carried question,
  not a false “Today” label.
- Once finished/skipped today, keep that result/rest state through the shared day;
  the next automatic question arrives on the next eligible day. No automatic
  replacement immediately after finishing yesterday's carried question.
- Manual Sparks use the same single-open slot. An existing manually chosen Spark
  is the hero; a manual choice cannot overwrite an unfinished daily Spark. After
  a terminal state, users may deliberately choose another through Questions.
- Assignment is stable across partners/devices and must not depend on whichever
  response arrives first. At most one automatic opportunity per couple day, with
  original membership binding and duplicate-safe creation. GET must not send an
  invitation or publish a chat card. Exact endpoint/migration design belongs to
  Stage 3.
- Rotate the curated catalogue without repeats until exhausted; then offer a
  clearly labelled revisit from the least recently used eligible question. No
  silent AI-generated content. The exact sample question in the images is not in
  the inspected 24-question catalogue; add it as a versioned curated entry if kept.
- Daily assignment alone sends no push or chat message. Secure-room readiness,
  key recovery and expired membership retain their honest existing states.

### Shared date ideas and agreement

- Show Coffee, Walk, Movie presets first. Tapping a clearly labelled suggestion
  saves the idea for both partners; no date/time is invented. One open authored
  idea per person keeps the first release bounded; replacing it requires an
  explicit Replace action, not silent overwriting.
- Me too records only the current user's interest. It is idempotent and undoable
  while the idea is unplanned. A matching preset already suggested by the partner
  opens that idea/interest action rather than creating a duplicate.
- After both are interested, offer Pick a time. Either may propose a time using
  the existing Plan editor; the other still accepts the resulting invitation.
  “Both going” appears only after that acceptance.
- Link the idea to one Plan with concurrent/duplicate conversion protection. A
  failed chat-card publication does not undo a saved idea/Plan. A declined/cancelled
  linked Plan remains visible in details/history; it does not silently revive or
  erase the idea. “Suggest again” is explicit.
- A creator can withdraw their idea; the partner can remove their own interest.
  No timer/expiry is invented. A replacement/ended audience loses access. Author,
  interest IDs, revision, state, timestamps and linked Plan ID are private metadata.

### Latest partner item and chat continuity

- Use the newest eligible partner-authored message or Spark activity by normalized
  source timestamp, with stable ID tie-breaks. Exclude own events, typing, receipts,
  reactions, redacted/deleted content and duplicate representations of the same
  resource. Plans/Challenges have their own sections; do not surface their chat
  cards again as a competing latest item in this first release.
- If a Spark is also the hero, use a compact “sent a question” / “answered your
  question” activity row opening the same Spark. Do not display two different
  locked questions as Image 1 appears to: that conflicts with one open Spark.
- A latest message row opens that Matrix event when available. Edits update its
  preview without making it a new arrival; media uses truthful type labels.
  Decryption pending shows “Secure message” with recovery/open action, never
  empty/caught-up. Unread numbers come from the room's real read cursor and sync,
  not the bounded loaded timeline or own-message read receipts.
- Home observing a message does not mark it read or suppress notifications as if
  the conversation were open. Do not instantiate SecureChatViewModel as a Home
  shortcut: the shared repository's room-owner lifecycle needs a deliberate
  read-only observation path that cannot close/steal the conversation.
- A domain timestamp can feed the latest Spark when its optional Matrix card failed;
  deduplicate by resource identity if the card later arrives. With partial/stale
  sources, say “Last synced” instead of claiming the item is globally latest.
- “Caught up” means the relevant sources are fresh and no unread/actionable partner
  item remains. It does not mean there are no historical messages. A never-used
  conversation says “Start your conversation,” not “You're all caught up.”
- Quiet-day chips, Ask and Nudge open a draft in Pure chat. The user presses Send;
  existing drafts are preserved with an explicit replace/append choice. Nudge is
  labelled “Nudge in chat,” uses a supported Plan reference where possible, and
  does not create a second push channel or claim automatic delivery.

### Identity, statuses, Moments and navigation

- Couple title opens the existing Couple Profile. Use its actual name if present;
  otherwise a neutral “Your space,” not a fabricated relationship tier “Lovebirds.”
- Recommended speech bubbles are opt-in, author-edited, couple-only preset status
  with a 24-hour server expiry and clear/remove action. Do not infer sentiment or
  change existing account mood visibility. No partner status becomes “No status
  shared”; one's own empty status offers Set status.
- Avatars remain Moments entry points: label each, show actual unseen/active state,
  and retain an explicit own-avatar + to create. Own avatar with content opens
  own Moments; own + always creates. Partner with no content has a labelled
  “No Moments yet” state, with no invented media. Status and avatar have separate
  non-overlapping hit areas. Couple profile remains available through the title.
- Questions is a root entry to existing Sparks current/catalogue/history. Existing
  feature detail/back behavior and Pure chat remain intact. Unpaired Questions
  can show the catalogue with a contextual Connect action; it cannot create a
  couple session. Chat has an unpaired connection entry, not a fictional room.
- Bell opens “Requests for you”: current incoming partner/Plan/change/sharing
  requests with verified counts. It is not notification history. No badge from
  delivery attempts or a sample dot. The same screen has loading/error/empty
  states. Exclude routine daily-question availability from its badge.

### Time, reminders, Challenge copy and counters

- Display device-local Plan time, including a timezone label where the saved Plan
  zone differs. “Today” uses the viewer's local date; shared question day uses the
  saved couple zone. Do not infer a duration or “in progress” when no end time exists.
- Use “Remind me” and the existing personal reminder choices. Permission denial,
  an elapsed reminder time and platform delivery limits remain explicit. A partner
  cannot silently enable reminders on the other person's phone.
- Replace “Today's challenge” with “Your challenge,” “Ends Sunday” with “At your
  own pace,” and “Next drops Monday” with “Choose another.” Show the last completed
  activity until another is started, with a clear completion date when old.
- Use an inclusive relationship-day number: the selected anniversary is Day 1,
  and the same number appears in Home header/stat. No date means Set your date;
  do not substitute app-linking date. Introduce a clearly named projection if
  necessary; do not change old zero-based API fields silently.
- Questions counts revealed sessions once per question session, not submissions;
  skipped/unfinished are excluded. Plans counts completed Plans, labelled “Plans
  done.” No counters for ideas or pending invitations. Values must respect current
  original audience/epoch access and may legitimately be zero. Hide only an
  unavailable metric with an honest state, not a fabricated 0.

## Coming up selection when cases coexist

Use one primary item plus a compact “N requests need you” link when needed:

1. An agreed Plan today that has not been marked complete/cancelled (earliest
   upcoming today first; otherwise latest started today). After start, show
   “Earlier today” and Open plan; do not automatically mark it complete.
2. An incoming invitation or reschedule requiring this user's response, earliest
   proposed future date first. If another item wins, retain the request-count link.
3. Nearest agreed future Plan.
4. Outgoing invitation/change awaiting the partner, earliest proposed date first.
5. Both interested in an unscheduled idea → Pick a time; then their unresponded
   idea → Me too; then own idea → View idea. Break ties by creation time and ID.
6. Nothing eligible → Coffee / Walk / Movie suggestion controls.

View all retains the agenda, requests and ideas regardless of selection. Stale
pending requests whose time passed show “That time has passed” and Suggest a time
in Requests; they must not permanently displace a valid plan or permit acceptance.
Cancelled/declined/completed items belong in history and feedback, not the next-plan
slot. An active reschedule preserves the agreed plan until accepted.

## State and action matrix

Common mutation contract M: recheck authorization; disable only the submitted
action while pending; keep content/drafts; reuse the same request identity after
uncertain failure; update after authoritative success; refresh a conflict and ask
the user to review rather than silently replay altered intent. Independent sections
remain usable. Common return R: Back restores origin and scroll/selection; refresh
the affected section without replacing the whole Home or duplicating navigation.

| Trigger/state | Visible content and main action | Action and feedback | Return/failure |
| --- | --- | --- | --- |
| Guest/signed out | Existing public guest entry; Create account / Log in | Existing auth graph; no protected Home fetches | Preserve guest origin; no fake relationship |
| Signed in, unpaired | Self identity; “Make this space yours”; Connect with your partner; View invitations | Existing partner search/invitation routes | R; failed connection check is Retry, not unpaired |
| Outgoing partner invite | Invitation sent; View invitation | Check status/cancel in existing flow; no invented link or expiry | R + M; cancellation needs existing confirmation |
| Incoming partner invite | Named verified invitation; Review invitation | Review then accept/decline; confirmed server result updates membership | R + M; no automatic pairing from a tap on Home |
| Newly connected | Real pair, optional Set date/status, first question, no-history copy | Start first permitted shared action | Secure-room preparation stays separate from relationship success |
| Ended/blocked/replaced | Neutral “This shared space is unavailable”; connection/account action | Clear private old-audience state, cancel requests/reminders as appropriate | Late callbacks cannot restore old content; no disclosure of blocker |
| Missing avatar/name/date/status | Initial/neutral placeholder, safe name fallback, Set date/Set status | Open only the corresponding editor | No synthetic partner, inferred date or inferred emotion |
| Question, neither answered | Go first | Open answer screen; submit using M and existing encryption | R; draft retained on recoverable failure |
| Partner answered | Answer to reveal; decorative locked strip | Open same Spark; no partner ciphertext fetched | R + M; never reveal based only on optimistic UI |
| User answered | Your answer saved; Waiting for partner; Open question | Reopen existing session; no resubmission/edit promise | R; only user's permitted answer available |
| Both answered | Both answers ready; Read together | Authorized retrieve/decrypt; optional Continue in chat | R; missing keys show secure recovery, not blank answer |
| Skipped | Skipped for now; Questions catalogue available | Existing explicit skip rule; next automatic opportunity next eligible day | Skip/answer race resolves to one authoritative state |
| Question secure sync/failed | Setting up secure chat / Couldn't load question; Open chat or Retry | Existing readiness/recovery and bounded retry | Other Home sections remain usable |
| Latest incoming message | Authorized text/media label, time, real unread count | Open Pure chat at event; reading governed by chat visibility | R; deleted/missing event opens chat with availability feedback |
| Latest Spark | Sent/answered question activity, correct lock state | Open exact Spark | R; resolve current state/audience; do not show another open question |
| No conversation yet | Start your conversation; Ask or a suggested draft | Open composer, user sends explicitly | R; preserve existing drafts and failed-send recovery |
| Fresh and caught up | Caught up, optional last received item, quiet-day draft chips | Open chat with chosen draft | R; no automatic send/notification |
| Message encrypted/unavailable | Secure message / Syncing; Open chat | Recover through existing client | No unread inference from decryption failure |
| Agreed future Plan | Date/place, Both going; Open plan | Existing detail | R; cancellation/change revalidated |
| Plan today | Tonight/Today or Earlier today; Open plan, Remind me where eligible | Existing details/personal reminder chooser | R; expired offset/denied permission explained |
| They invited/proposed change | Current proposed details; I'm in; Another day secondary | Accept through M; Another day opens editor and requires a submitted time proposal | R; never treat Another day as decline or automatic acceptance |
| You invited/proposed change | Waiting on partner; Open/edit proposal; Nudge in chat secondary | View/edit uses existing Plan rules; nudge creates draft only | R + M; editing may require renewed partner response |
| Reschedule active | Last agreed plan and separate proposal clearly distinguished | Only non-proposer accepts/declines | Declining preserves old agreement; stale changes refresh |
| Expired invitation | Time passed; Suggest a time | Propose future time | M; cannot accept an expired time |
| Their idea | Partner is up for [idea]; Me too, Other ideas secondary | Save current user's interest | M + R; clear Saved feedback; no fake scheduled Plan |
| Own idea waiting | You suggested [idea]; View idea | Detail/withdraw or explicit replacement | M + R; no inferred response |
| Both interested | You're both up for [idea]; Pick a time | Plan editor then partner acceptance | M + R; concurrent conversions link one Plan |
| No ideas/Plans | Nothing planned yet; Coffee/Walk/Movie | Explicit suggestion controls save shared idea | M; unavailable service has Retry instead of this state |
| Cancelled/declined/completed Plan | Result feedback and history access | View history or explicit suggest-again | R; no automatic completion, deletion or resurrection |
| No Challenge | Nothing started yet; Choose a challenge | Existing catalogue | R |
| Challenge invited | Join/pass for recipient, waiting/withdraw for sender | Existing role-correct actions | M + R |
| Challenge active/one done | Both participants' real progress; Open challenge / Your turn | Confirm only own part; continue in feature | M + R; double taps/retries cannot complete for partner |
| Challenge completed | You both finished; Choose another; optional Share a Moment in details | Latest completed history, explicit new selection | R; no Monday promise or auto-publish |
| Challenge paused/ended | Paused/ended with correct actor permissions | Own Resume or choose another after terminal state | M + R; cannot override partner's pause |
| First load | Stable skeletons, no fake counts | Independent source loading | Bounded errors/retry; no flash of false empty |
| Refresh | Loaded content with quiet progress | Revalidate relevant sources | Preserve scroll/selection; reject stale-generation results |
| One section fails | That section's retained permitted data + stale label or Retry | Retry affected source | Other sections remain usable; no caught-up assertion |
| Offline, permitted cache | Last synced content; local read-only browsing / Retry | Follow each feature's cache/expiry rules; drafts remain local | No new domain success/claim of current presence; writes wait for explicit retry policy |
| Offline, no cache | Couldn't load your space; Retry | Connection recovery | Not No partner / Nothing planned |
| Auth expired/revoked | Existing auth recovery or login | Bounded refresh, clear state only on established revocation/logout | Reauthorize saved destination; never preserve old private UI after account switch |
| Permission loss during action | Resource unavailable; safe return | Cancel/clear affected work | No private cache fallback after known revocation |
| Rotation/process restart/keyboard | Restored allowed drafts and route, reachable controls | Existing lifecycle/storage ownership | No duplicate writes; keyboard never hides focused action |

## Intentional differences from the images

Remove flame/streak (approved). Use one valid question identity across hero/latest
activity; keep locked content decorative. Use Remind me, unscheduled idea consent,
Your challenge/At your own pace/Choose another, real status/date fallbacks and
truthful metric definitions. Keep a visible Moments add/view affordance. The bell
is current Requests, not a historical notification inbox. Quiet-day chips open a
draft; their label/arrow must communicate that. These changes keep appearance close
while correcting unsupported or ambiguous behavior.

The second and third screenshots are long, scrollable variants of the same Home;
Image 4 is a state catalogue, not a real screen that shows every case simultaneously.
Visual studies must show actual bounded phone viewports plus optional full-content
captures. Do not squeeze these full compositions into one viewport.

## Stage 2 and implementation checks

Stage 2 should show connected/latest-Spark, connected/latest-message, quiet day,
unpaired, outgoing/incoming partner invitations and new-couple states. Include
Coming up alternatives and one coexistence example (today's Plan plus incoming
request). Show the same realistic synthetic couple across variants and compact
width/large-text adaptations. Keep implementation-only labels out of product UI.

Later tests must cover shared-day/DST/timezone rollover, concurrent first-open and
answers, carried/skipped/manual questions, exhausted catalogue, idea interest/undo/
conversion races, no automatic messages, draft preservation, unread cursor/history
limits, duplicate activity cards, partial-source failure and authoritative revoke.
Include Plan reschedule/expiry and permission denial, Challenge history/pauses,
unpaired/invite transitions, and cache/session-generation boundaries.

The new Home must not steal Matrix room ownership, send receipts or suppress chat
push merely because it is visible. Native scenarios must verify return scroll,
back navigation, keyboard, screen readers, text growth, dark-theme derivations,
reduced motion and smooth scrolling; run on available Android/iOS environments
and report exact coverage. Preserve approved chat/menu/picker behavior.

Stage 1 checks actually performed: live integration-ref/status check; read-only
inspection of frontend, default and consolidated backends; comparison of relevant
service files across backend commits; four exact reference copies with hashes;
documentation/link/whitespace checks. No app, backend test suite, simulator or
live service was run. Existing test sources were inspected earlier in the audit;
their presence is not a new passing test result. Production and API compatibility
remain to be verified during implementation. No essential product answer remains
pending; the three user decisions are recorded above.
