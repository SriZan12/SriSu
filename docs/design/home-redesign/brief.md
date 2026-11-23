# SriSu Home redesign master brief

Status: Stage 2 previews prepared for review on 2026-11-09. Behavior decisions,
visual previews and implementation boundaries only; no production implementation,
API or migration change. See [Stage 2 review and verification](stage-2.md).
The detailed user prompt and four supplied designs govern this brief. The
[Stage 1 decisions and state matrix](stage-1.md) records approved choices,
recommended defaults, source evidence, remaining engineering work and validation.

## Product outcome

Create a romantic, private, minimal Home that makes the couple, their next shared
action and their conversation understandable. Follow the supplied composition;
do not reopen the rejected Home explorations. Polish comes from typography,
proportions, personal content, clear feedback and reliable transitions.

The user approved these three decisions on 2026-11-09:

1. One shared daily question; an unfinished question stays until answered by both
   or skipped. Do not replace an answer in progress at midnight.
2. Omit the flame/streak in this release.
3. Coffee, Walk and Movie create a shared, unscheduled idea. “Me too” records
   interest; the couple agrees a time separately.

The user subsequently authorized Stage 2 using these choices and the Stage 1
defaults. Those defaults govern the previews and remain reviewable; this does not
authorize production implementation. No unanswered essential question blocks the
visual studies.

## Visual authority

The four user attachments are the authority for this new Home. They supersede
earlier Home previews, including the earlier Moments → Sparks → Challenges →
Plans order. They do not supersede the approved Pure chat, illustrated Quick
Share or Palette picker. The long phone frames are compositions of scrollable
content, not a requirement to fit everything into one viewport.

Exact, unmodified copies live outside source control in this thread's durable
reference directory. These reference people/photos are not production fixtures.

| Reference | Source copy | Original size |
| --- | --- | --- |
| Home, latest Spark | [Image 1](/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/home-stage-1/references/home-latest-spark.png) | 826 × 2771 |
| Home, latest message | [Image 2](/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/home-stage-1/references/home-latest-message.png) | 930 × 2863 |
| Home, quiet day | [Image 3](/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/home-stage-1/references/home-quiet-day.png) | 930 × 3043 |
| Coming up, alternative states | [Image 4](/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/home-stage-1/references/coming-up-cases.png) | 930 × 2492 |

The adjacent manifest records SHA-256 hashes. The local image links require this
workspace; a future handoff must transfer these references explicitly. No new
Figma frame was supplied. The established Figma file is
`LztysD1YvINX7RwZpnhhTt`, design-system page `0:1`, Home page `1:3`, historical
Home title node `193:110`, Sparks `184:1634`, Planner `185:781`. No live Figma
inspection was performed in Stage 1; do not claim these new attachments are
synchronized to Figma.

## Composition and routes

Keep this sequence: couple identity and two avatars; question hero; From partner;
Ask and Plan a date; Coming up; current Challenge; Your story so far. The floating
bottom bar contains Home, Questions, Chat, Profile. Questions reuses Sparks;
it is a root entry to current question, catalogue and history, not a parallel
answer system.

Preserve the white ground, lilac question surface, warm neutral rows, charcoal
primary actions, rounded geometry, paired avatars, small illustration and restrained
color accents. Use bundled Satoshi and the established tokens. Match proportions
at a normal phone width before adaptive changes; do not substitute a different
font, equal-weight dashboard, oversized headline or new decorative theme.

Each card opens its own authorized resource. A message opens the existing Pure
conversation at the relevant event; a Spark opens its question; a Plan or
Challenge opens its detail. Open chat always opens the shared conversation.
Avatar/photo access retains Moments, with visible labels and an own-avatar add
control. Couple title opens Couple Profile; own status opens its editor. Essential
actions never depend on a long press or unexplained gesture.

Preserve Home scroll position, selected resource, navigation origin and feature
drafts on return. Opening Home must not send messages, mark chat read, publish
Moments, accept invitations, or mark a Challenge completed.

## Functional requirements

- Use real authorized identities, statuses, dates, unread state and activity.
  Synthetic content belongs only in previews/tests. Do not promote source image
  names, counters, dates or blurred strings into production defaults.
- Distinguish guest, unpaired, sent/received invitation, newly paired, active,
  ended/blocked/replaced relationship and authentication recovery.
- Distinguish loading, refreshing, stale, offline, failed and genuinely empty
  data per section. One failed feature must not blank the whole Home.
- Resolve all question, latest-item, Plan/idea and Challenge states in Stage 1.
  Choose a deterministic priority when multiple states coexist, with View all
  retaining access to the others.
- Handle missing photos/date/status, long names/questions, multiple events,
  expiry, deletions, time-zone/day changes and partner actions on another device.
- Every action needs an exact destination or mutation, pending state, success
  confirmation, failure recovery, duplicate protection and return path.
- Do not promise delivery, read status, weekly releases, shared reminders,
  deadlines or counters that the actual implementation cannot support.

## Architecture and privacy requirements

Follow both repositories' AGENTS.md and existing feature repositories, ViewModels,
Ktor services, Koin wiring, session coordinator, typed navigation, shared components
and platform source sets. Use the current tested backend contract as the authority
for permissions and data, and surface discrepancies before making changes.

Matrix remains the sole messaging transport. Home obtains decrypted previews and
unread state locally from the authorized client; Django must not receive message
plaintext for Home aggregation. Preserve encryption, replies, reactions, uploads,
playback, receipts, outbox/retries, error handling and approved chat UI.

Spark answers remain protected by existing encryption and server-enforced mutual
release. Never fetch an unrevealed partner answer for a visual blur, accessibility
label, analytics, notification or log. The locked strip is decorative and must not
reveal answer length. Preserve original-audience and room-epoch checks.

Plan/Challenge/status/idea metadata is private server-held couple data unless an
explicit new encryption design is approved; do not label all Home content E2EE.
Private defaults must not change existing Moment publication controls.

Revalidate actions and references against current membership. Logout, account
switch, breakup, block, membership replacement and known permission revocation
clear affected private content and reject late callbacks. A network failure alone
does not mean a relationship ended. Retain only cache allowed by each feature's
privacy/expiry rules; never claim offline data is current.

Backend extensions must be additive where possible, bounded, authorized,
idempotent and concurrency-safe. Update contracts, callers, relevant tests and
deployment/migration documentation together. Do not invent routes as existing
facts or silently replace shared architecture. Review old clients before altering
response semantics. Do not apply migrations or deploy as part of these stages
without the relevant authorization.

## Adaptive design and interaction requirements

Use content-driven heights, wrapping text and flexible constraints. Preserve
visual hierarchy at narrow, standard and large widths; use a bounded content
column on wider windows. Never scale the full mockup or disable text scaling.

Verify approximately 320, 360, 390 and 430 logical-unit phone widths, short
screens, landscape and wider/resizable windows. Cover 100%, 140% and 200% text
plus supported platform accessibility sizes, long localization, emoji and missing
images. Logical units are dp/pt as appropriate, not raw screenshot pixels.

Respect safe areas, cutouts, system/gesture bars, keyboard insets, Android Back,
iOS navigation and screen-reader order. Controls need readable contrast, explicit
labels and non-overlapping targets (at least 48 dp Android and 44 pt iOS). Let
action rows stack instead of truncating primary labels or compressing targets.

Use subtle motion only to explain changes; respect reduced motion. Avoid heavy
shadows, repeated ornament, reflow jumps, unbounded image decoding and work on the
UI thread. Quiet refresh should not move the item under the user's finger.

## Stages and acceptance

| Stage | Deliverable and completion condition | Status |
| --- | --- | --- |
| 1. Behavior | Verified compatibility map, confirmed essential decisions, explicit defaults, state/action matrix, source baseline and risks | Complete; see stage-1.md |
| 2. Visual refinement | Faithful previews for representative connected/quiet/unpaired states and Coming up alternatives; standard and compact/large-text views; visible deviations explained | Previews ready for review; see stage-2.md |
| 3. Implementation | Approved composition connected to real data and actions, necessary authorized backend extensions, state transitions and feature regressions tested as work proceeds | Not authorized; Stage 2 explicitly excludes production changes |
| 4. Verification | Native Android/iOS runs, screenshot correction, edge/accessibility/keyboard/scrolling checks, remaining limitations and deployment requirements reported | Not started |

Question essential behavior, not every reversible implementation choice. Record
decisions instead of repeatedly reopening them. Do not request a new complete
prompt for each stage. A later stage must read this brief and stage-1.md together.

Before completion of implementation: compare actual native screenshots at matching
viewport dimensions and full-content captures with the references, fix visible
differences, and test two synthetic partners plus an unrelated account. Run the
documented theme/frontend checks and backend allowlisted isolated runner; use
disposable PostgreSQL for relevant concurrency checks. A compile is not a live
integration test. Report builds, unit tests, simulator/device runs and live-server
coverage separately, with remaining defects/limits. Never promise universal
device compatibility or a bug-free result. No push, merge or deployment is implied.
