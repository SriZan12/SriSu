# Plans — implementation and design decisions

Implemented locally on 2026-10-24, without switching branches or committing.
Frontend `SriZan12/SriSu`, `dev-core-architecture`, base
`93fff640dd180614e78d9185355fbb287b2dc43a`.
Backend `SrizanKhadka/SriSu`, `codex/couple-chat-rebuild`, base
`04f5652fd843daf1dc653f08a19c10f149b219fb`.
Integration branches remain frontend `dev-new-theme`, backend `dev`.

## Reference and visual decisions

Figma [Planner 185:781](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=185-781),
with foundations on page `0:1`. The five-screen planner flow was inspected in the
browser during discussion: idea chips and editor, waiting chat invitation,
Yes/Another time/Can’t response, accepted state, detail date tile/avatars,
location/reminder rows and Bring checklist. The connector's current design-context
and screenshot requests were quota-blocked; exact inaccessible asset fidelity is
not claimed. No Figma file was changed.

Reuse Satoshi, existing spacing/shapes, warm neutral surfaces, planner amber date
accents, purple selection and charcoal primary buttons. Agenda, explicit proposal
comparison, cancellation/history and failure states extend the reference using
existing components. Upcoming summaries retain the agreed date during a proposal.
No gradients, animated decoration or alternative architecture. The runtime check
exposed an existing dependency-resolution conflict: Lognity selects datetime 0.8.0,
but Compose 1.8.2 date pickers still call the removed 0.6 Instant ABI. Pin the
0.8.0 compatibility artifact (the same effective API version, with that ABI restored).
The old 0.7.1 compatibility declaration was silently overridden. A native editor
regression reproduces the linkage failure and verifies date/time controls after
the pin; the encrypted Matrix test is rerun to cover the transitive consumer.
See [Kotlin’s compatibility guidance](https://github.com/Kotlin/kotlinx-datetime#deprecation-of-instant).

## Flow

- Home → Coming up → nearest agreed future plan. Invitations have their own count.
- Plans agenda: Upcoming / Invitations / Past, chronological cursor pagination.
  Updates reconcile loaded pages and stable IDs without resetting the scroll position.
- Plan something: idea shortcuts; title, date/time, optional place or HTTPS call link.
  Account/origin-scoped protected drafts survive closing and recreation. An existing
  unfinished draft is opened before another edit can overwrite it.
- Creator invites. Other partner accepts, declines or proposes a time/place/title.
  A proposal requires the other person's response; the proposer cannot self-accept.
  Agreed fields remain authoritative until acceptance. Rejecting or withdrawing a
  change retains the prior agreement. Initial counterproposals reverse the responder.
- Checklist opens after agreement. Either partner adds, checks/unchecks or removes;
  assignment is either person or either-of-us. A remote addition does not erase the
  text the other person is typing. Max 30 items; server validates assignments.
- Either partner can cancel with a confirmation; cancelled plans remain in Past.
  An agreed elapsed plan can be marked done or undone by either partner, with
  attribution. Merely passing the date does not mark it done. Share a Moment opens
  the existing composer without publishing or copying private plan details.
- Chat invitation is a Matrix-encrypted reference using a stable transaction ID.
  Tapping it opens the latest server-authorized plan. Failure to publish the card
  leaves the saved plan intact and offers retry. Personal replies use existing chat.
- Existing Couple Profile plan navigation opens the same feature; old NEW_PLAN
  entries still open an editor. Notification taps use the existing authenticated
  external-entry pipeline and fetch authorization afresh.

## Reliability and privacy

Django is authoritative for plan fields, membership, responses and revisions.
Plan title/time/place/checklist are **private couple data stored by SriSu, not E2EE**.
Only the chat reference card and chat conversation use Matrix E2EE. No online,
delivery or completion status is fabricated. Plans stay bound to their original
membership IDs; blocking, unpairing and membership replacement remove access.

Writes persist their intent before HTTP. Transactional operation receipts allow
exact retry after lost acknowledgements, including after ViewModel recreation.
A conflicting revision reloads current state and retains the edit for review.
Permission/session loss clears visible private state and device reminders.
Networking, scheduling and media remain outside composables.

## Reminders and calendar

Personal opt-in reminders are per account/origin/device: off, 15 minutes, 1 hour,
1 day. Request notification permission only on explicit selection. A complete
server snapshot prunes cancelled, elapsed and inaccessible plans; accepted changes
reschedule them. Pending changes keep the last agreed reminder. Normal sync keeps
unchanged alarms and already delivered notifications; cancellation/logout clears them.
Notification title/body are generic and contain no plan title or location.

The app reconciles on foreground and while active. **It cannot learn a partner's
changes while terminated/offline.** A previously scheduled generic reminder can
therefore still arrive; tapping always retrieves current authorized state. This
release does not add server push or promise exact Android delivery.
Android uses inexact AlarmManager alarms, notification channel permission and
reboot restoration; no exact-alarm privilege. iOS uses local notifications.
Max 32 selected reminders per device. Past reminder times are labelled honestly.

Calendar is an explicit system editor handoff, initially one hour and editable by
the user. SriSu does not write directly or request broad calendar access. The saved
calendar copy does not follow later changes automatically; the UI explains this.
iOS requires 17+ for this handoff; older systems receive an explanatory message.
See [Apple EventKit guidance](https://developer.apple.com/documentation/technotes/tn3153-adopting-api-changes-for-eventkit-in-ios-17-macos-14-and-watchos-10)
and [Android alarm timing](https://developer.android.com/develop/background-work/services/alarms).

## Deployment and preservation

Deploy backend migration `social.0020_coupleplanmodel_cancelled_at_and_more` before
shipping this client. It adds fields and durable operation receipts to the existing
CouplePlanModel; it does not replace IDs, audience, dates, response notes or records.
Legacy routes remain available, with guards against overwriting a new pending change
or cancelled plan. Unknown time zones on legacy records remain blank; the client
shows the stored instant in the viewer's local time. No backend rollback that drops
new columns should run after new clients write their data.

The mirrored [plans-1 contract](../../contracts/plans-1/openapi.json) is owned by
backend routes/validation. See backend `docs/plans.md` and
[executed checks and screenshots](../validation/plans/README.md).


### Follow-up device QA: editor and calendar feedback

The Plans editor uses a full-height dialog with the existing SriSu surfaces and
spacing. Its header/close action stays fixed while the form scrolls. Title uses
Next to focus place; place and checklist use Done to dismiss the keyboard without
submitting. The detail scaffold consumes IME insets so checklist actions remain
reachable. This replaces the original sheet after actual iPhone QA exposed an
agenda strip beneath the keyboard-open sheet.

iOS calendar completion distinguishes saved and cancelled copies and shows its
outcome immediately. Android reports only the external editor handoff. Copies
remain independent of later plan edits. See the follow-up section of
`docs/validation/plans/README.md` for actual OS delivery/calendar evidence and the
separate unresolved Compose accessibility crash found during inspection.

A missing individual plan clears only its own saved edit/pending operation.
Unrelated drafts survive a stale detail link or refresh; the user can return to
the agenda. Authentication or couple permission loss still clears private state.
