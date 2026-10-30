# Challenges: experiences with mutual agreement

Implemented locally on 2026-10-24. Frontend base `f0969a00c2329fd34187ef802390ef4a5ab4f01e`
on `dev-core-architecture`; backend base `a542c87053b3dd52551da8c794367b8d97666fb5`
on `codex/couple-chat-rebuild`. No branch switch, commit, push or deployment in this task.

## Experience

Home → **Your challenge** → Browse, or Chat → attachment sheet → Challenges.
The Home/Chat/Profile bottom navigation stays unchanged. An encrypted invitation
card in the existing Matrix conversation also opens the authorized activity.

Four collections each contain four versioned activities: **Little gestures**,
**Quality time**, **Play together**, **Try something new**. Filters select activities
that work apart or take 15 minutes or less. Details include an estimated duration,
cost, simple steps and an accessible alternative. There are no deadlines, streaks,
scores or penalties. The catalogue is backend-owned; production never uses test fixtures.

Inviting counts as the sender's acceptance. The recipient can join or pass; an
activity becomes active only after both agree. A couple has at most one pending,
active or paused Challenge. The sender can withdraw a pending invitation; either
partner can end an active activity after confirmation.

Each person confirms their own part. One confirmation leaves the activity active;
both complete it. A person can undo their own confirmation before completion.
Either can pause. Each owns their own pause: resuming one does not override the
other person's pause. Progress remains saved. Completion is unavailable while paused.

History contains completed, declined and cancelled activities, with pagination.
It remains private to the original, currently authorized couple membership. A
completed activity offers **Share a Moment** (opens the existing composer without
publishing) or **Continue in chat**. No private journal/answer field is introduced.

## State, privacy and retries

Activity choices and progress are server-readable couple metadata. Personal
reflections belong in the existing encrypted chat. The optional invitation card
is Megolm-encrypted using the existing Matrix SDK and room/membership checks.
The card carries a bounded reference, not authoritative progress; opening it loads
current state through Django authorization. Matrix failure never rolls back an
accepted invitation. Card publication has a separate retry and stable transaction ID.

Before each mutation, the exact request ID, action and expected revision are saved
in existing protected platform storage, scoped to account and API origin. An
uncertain failure retains that intent across screen/app recreation. Retry reuses
the request; durable server receipts return current state without replaying an old
pause or completion. Conflicts refresh current state and require a deliberate new
action. Foreground polling is 8 seconds on the feature and 15 seconds on Home;
background stops polling/card publication; account changes cancel work and clear visible private state. Quiet refresh preserves loaded history pages.
Loading, empty filters/history, unpaired, failed action, unavailable relationship,
expired session and failed chat publication have explicit states.

## Figma and design system

References: [Home title layer 193:110](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=193-110),
[shared-space variant 158:13166](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=158-13166),
design-system page `0:1`. The prior discussion inspected the live browser reference:
a warm rounded activity card, orange sun, cooking activity and overlapping avatars.
The connector's design-context and screenshot calls were blocked by the Starter
quota during implementation. No exact fidelity claim is made for inaccessible assets.
No complete acceptance/progress/history flow was found among inspected references.

Derived screens use current Satoshi typography, warm neutral surfaces, charcoal
primary actions, secondary selected chips, semantic reminder/success colors,
shared avatars, spacing and shapes. The existing sun icon replaces inaccessible
artwork. Dark mode inherits the established derived theme. The Figma “Ends Sunday”
example becomes “At your own pace”: no invented deadline or automatic expiry.

The first render put actions too far below instructions. The correction pass
reduced the activity heading, removed repeated artwork for an active session and
moved actions immediately after progress. Larger text wraps inside a scrolling
layout; button touch targets remain intact. Completion uses a short fade respecting
the platform's reduced-motion scale.

## Implementation map

- `features/challenges/data`: DTOs, intent/reference validation, Ktor source.
- `features/challenges/presentation`: Home card, catalogue/detail/history, lifecycle and retries.
- `features/challenges/navigation`: typed IDs and existing `AppNavigator`.
- `features/chat/matrix/MatrixChallengeCard.kt`: existing SDK encryption adapter.
- Matrix mapping/repository, chat attachment sheet and message bubble: invitation integration.
- Backend `social/services/challenges.py`, `social/api/challenges.py`, models and migrations.
- Mirrored `contracts/challenges-1`: catalogue and OpenAPI response contract.

See [backend deployment/authorization details](../../../SriSu-backend/docs/challenges.md)
and [actual verification and screenshots](../validation/challenges/README.md).

## QA follow-up

The follow-up [QA record](../validation/challenges/qa.md) covers regression fixes:
failed chat-card loads keep their requested ID for retry; leaving detail cancels
its pending background read; successful refresh clears stale connection errors;
ended/accepted Challenges cannot initiate a new invitation-card publication.
These changes preserve the API and stored activity state.
