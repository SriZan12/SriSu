# Home Stage 2 — visual review

2026-11-09. Preview work only, using the four user attachments and the approved
Stage 1 decisions/defaults. No application code, backend code, contracts, migrations,
dependencies, deployment, branch or remote changes.

## Review surface

The in-conversation preview is stored at:

`/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/srisu-home-stage-two.html`

Editable source, assembly helper, browser checks and captures are in the adjacent
`home-stage-2/` directory. These paths belong to this local workspace. Transfer
the preview and original reference directory explicitly for a different machine.
The preview embeds the reference imagery and bundled Satoshi fonts; reference
photos remain demonstration assets and must not become production fixtures.

The picker contains ten initial states:

| State | Review focus |
| --- | --- |
| Latest Spark | White Home, paired avatars, lilac question, newest partner question activity |
| Latest message | Same hierarchy with message preview and unread count |
| Quiet day | Go first, caught-up message area, draft nudges, unscheduled ideas, completed challenge |
| No partner | Own identity and connection action; no fictional partner, unread count, couple counters or message history |
| Invitation sent | Named pending invitation with status/cancellation path |
| Invitation received | Review before acceptance |
| Newly connected | Empty history, missing anniversary/status, first question and suggested activity |
| Small screen | 320 × 700, today’s Plan with a separate pending request |
| Larger text | 320 × 700 at 140% text, wrapping labels and stacked statistics |
| Coming up cases | Six alternative Plan/idea states from the fourth reference, shown as a review catalogue |

Standard screens are 390 × 844 CSS pixels and scroll internally. The long reference
compositions are not squeezed into one viewport. The optional preview controls
allow text scaling through 200% and switching to a compact phone. They are design
review controls, not proposed SriSu settings.

## Intentional adaptations

- Preserve the reference composition, white/lilac/warm surfaces, Satoshi type,
  paired avatar emphasis, small question illustration, pills and floating tabs.
  Use content-driven heights and balanced question wrapping.
- Remove the streak. Use a synthetic saved couple name, not a relationship tier.
- Give Moments visible avatar labels and an explicit own-avatar add action. The
  quiet-day state has no unseen Moment ring.
- The latest Spark row refers to the same question as the hero. It changes after
  mutual reveal; it does not suggest a second open question.
- Apply the approved truthful copy: Your challenge, At your own pace, Choose
  another, Remind me, Plans done, Nudge in chat. Demo dates are internally aligned
  with November 2026 rather than copied as contradictory weekday/date pairs.
- The locked strip is a fixed decorative placeholder. It contains no partner
  answer text, length or ciphertext. The separate revealed demo is synthetic.
- Increase small-text contrast and darken the unread badge for legibility.
  Larger text may hide decorative artwork, stack statistics/actions and reflow
  navigation. Exact pixel identity at every text size is not the objective.
- Icons use the available preview icon family. Final native icons/assets, exact
  raster crops, safe areas and platform chrome need production-stage comparison.

## Representative local interactions

All actions are simulated locally. No messages, invitations, reminders or ideas
are sent to a service, and no real device permissions are requested.

- Question → answer/reveal → contextual conversation → question → Home.
- Message/Open chat → conversation → Home with preserved scroll.
- Quiet-day chip → prefilled draft; only Send adds a local message bubble.
- Coffee/Walk/Movie → saved unscheduled idea; Me too → mutual interest; Pick a
  time → a separate Plan invitation that still waits for acceptance. Interest can
  be undone. Incoming invitation acceptance is shown independently.
- Unpaired Chat leads to connection; sent/cancelled and received/accepted
  invitation paths demonstrate their Home states.
- Missing date → inclusive Day 1 in both avatar connector and statistic.
- Personal reminder picker and challenge completion show local feedback.

Feature destinations are illustrative previews, not replacements for existing
production flows. Photo picker, catalogue/history, request composition, draft
conflict handling, complete error/permission states and async race recovery are
not implemented here. Pure chat is represented only enough to assess continuity;
its production screen, illustrated Quick Share and Palette picker are untouched.

## Checks actually performed

Rendered in headless Google Chrome through the visualization renderer; inspected
screenshots of the standard, quiet, unpaired, compact, enlarged-text, Plan-case and
chat states. Corrected inherited-font leakage, question wrapping, narrow tab label
wrapping, stale local reveal/request feedback and return-context issues.

The local browser check records twelve passing groups in
`home-stage-2/verification.json`, with no JavaScript runtime errors:

- Eight representative interaction groups covering reveal/chat/return, scroll,
  quiet drafts/ideas, idea-to-invitation, connection/cancellation/acceptance,
  missing date, personal reminders and request-badge updates.
- Layout bounds at widths 320, 360, 390 and 430 with 100%, 140% and 200% text.
- Reduced-motion preference applied.
- Keyboard input and composer visibility in a 470px-high browser approximation.
- Runtime-error check.

These are browser preview checks, not native validation. No Android emulator,
iOS simulator, hardware keyboard/IME, VoiceOver/TalkBack, native Dynamic Type,
network, live backend, migration or authorization tests were run. No Gradle,
backend runner or theme check was needed for unchanged production code. A bound
check is not a complete accessibility audit or proof of every phone’s behavior.

## Implementation boundary

Stage 1 remains authoritative for compatibility. Daily assignment, private
expiring statuses, shared unscheduled ideas, client-owned Matrix summaries and
some counters/projections still require the specified implementation work. A
working local preview does not establish that these capabilities are deployed.

Frontend stays at `dev-core-architecture`,
`c1abef982b0e3a2b87bf342d08c526bc65c25bd8`. Both backend checkouts are untouched.
The four attachments remain visual authority; no new live Figma node was supplied
or written. Native implementation and Android/iOS verification await the next
authorized stage after visual feedback.
