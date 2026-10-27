# Home → full Sparks catalogue (2026-10-26)

Frontend work on `dev-core-architecture`, based on
`b6b1576b9ed26b270e9843f3ea4309a4ee3a93cb`. Backend remains unchanged by this
follow-up, at `6e0935c1c4f24267b9a956d259db5d113e9bb903` with existing local work.
No commit, push, migration, or deployment was performed.

## Change

Home's Sparks section now has **Show more** alongside the heading. It opens the
existing catalogue with Deep, Memories, Playful and Future, including the existing
recent-history list. The compact card still opens its active/latest Spark directly.
An ID-free destination no longer redirects into an active answer screen. The
catalogue offers that active Spark separately, and its main action continues it
instead of creating another. Saved drafts use the unchanged protected storage.

The layout reuses the Home section pattern and existing theme tokens. Figma source
is [Sparks 184:1634](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-1634),
with Home `1:3` and design-system `0:1`. The current metadata request was rejected
by the Starter-plan tool limit. This is an intentional navigation addition using
the established design system, not a claim of a newly inspected Figma frame.

## Checks actually run

- Focused Android Sparks tests: **36 passed**, zero failures or skips: 16 state,
  6 recovery, 14 component renders. New regressions check that a catalogue with
  an active Spark stays open on refresh, and continuing restores its draft with
  zero create requests. Existing encryption/retry/revocation tests also passed.
- `compileDebugKotlinAndroid` and `compileKotlinIosSimulatorArm64`: passed.
- Signed Xcode simulator build: passed; installed on the existing iPhone 17,
  iOS 26.5, using the local API at `127.0.0.1:8000`.
- iPhone interaction with the running local backend: Home → Show more → four
  decks; selected Memories and saw its question; scrolled into recent history;
  Back returned to Home. No answers were submitted or changed during this check.
- Theme check: passed (96 documented colours, 24 contrast pairs, minimum 4.75:1).
  `git diff --check`: passed.

Android validation used Robolectric, not an Android device. Active-Spark resume
and draft preservation were verified in shared state tests; the live iPhone
account had a completed latest Spark. The 320 × 640, 1.6× text render verifies a
readable wrapping action; native iOS large-text/VoiceOver and a fresh two-partner
encryption flow were not rerun for this navigation-only follow-up. An attempted
Robolectric semantic-scroll assertion stalled in the test framework and was
removed; native scrolling was checked on iPhone instead.

## Evidence

- [Before: only the latest card](iphone17-before.png)
- [After: Home Show more](iphone17-home.png)
- [Full catalogue on iPhone 17](iphone17-catalogue.png)
- [Recent history on iPhone 17](iphone17-recent.png)
- [Active Spark, Android component](android-active.png)
- [Active Spark, small screen with larger text](android-active-large-text.png)

Logs for this local run: `/tmp/srisu-sparks-show-more-checks.log` and
`/tmp/srisu-sparks-show-more-xcode.log`. Screenshots contain local test identities
and catalogue questions, not private answer text.
