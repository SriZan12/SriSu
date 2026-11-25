# Home Stage 3 — native implementation

9 November 2026. Authorized by the user's “next step. proceed” after Stage 2.
Implementation is submitted from the two feature branches for review. The user
subsequently authorized remaining verification, fixes, pushing and linked PRs.
No merge, deployment or production database migration was performed.

| Source | Branch / starting commit |
| --- | --- |
| `SriZan12/SriSu` frontend | `codex/home-couple-space`, from `dev-core-architecture` at `c1abef982b0e3a2b87bf342d08c526bc65c25bd8` |
| `SrizanKhadka/SriSu` backend | `codex/home-couple-space`, from consolidated `dev-core-architecture` at `11891049ef323b6df3c6ee3c8f0688442b18c13f` |

The backend checkout is `/Users/srijan/studioprojects/SriSu-backend-consolidated-20261029`.
The default older `../SriSu-backend` checkout was not changed. Use
`SRISU_BACKEND_DIR` for verification; the repository map's default path is retained.
Existing frontend `docs/design/` work and the backend's pre-existing `.DS_Store`
change were preserved and excluded from this implementation commit. The PRs
record the final source commits and cross-repository dependency.

At task start the live integration refs were separately verified: frontend
`dev-new-theme` at `3fa6822ea05578d64cca8891c5e3fdb0a831897a`, backend `dev` at
`ecc8592307114e7d154bda9612e6826e2ca8ba90`. These integration branches are not the
current feature branches and were not merged or reset.

## Visual authority and deviations

Authority is the four attached designs, Stage 1 decisions and the approved
Stage 2 preview at
`/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/srisu-home-stage-two.html`.
Figma file `LztysD1YvINX7RwZpnhhTt`, page `1:3`, node `238:193` was read live
(context and screenshot); related frames `238:415`, `238:612`, `238:773` were
identified. They are older layouts and do not override the approved attachments.
No Figma file was written.

- White/lilac composition, partner pair, status bubbles, question hero, warm rows,
  pills, counters and floating tabs follow the approved preview.
- No streak. Statuses are optional/private/expiring; challenge and reminder copy
  reflect actual behavior. Unknown counts do not become demo numbers.
- Production photos come from authorized existing data. Native test renders use
  initials rather than embedding the supplied demonstration portraits.
- Question illustration is native Canvas artwork derived from the reference;
  native icons and platform shadow/font rasterization are not pixel-identical to
  the browser preview. Text wrapping was aligned across native platforms.
- Home shows **New**, not a fabricated unread count. The backend receives no
  Matrix message text. The locked strip contains no partner answer.
- Small and enlarged-text layouts reflow; the full reference is intentionally
  scrollable rather than squeezed into one viewport. At 200% text, navigation
  uses two columns. The Home white palette is intentionally scoped; it is not a
  newly designed global dark theme.

## Backend work and rollout

The necessary new capabilities are implemented in `home-1`: shared daily question
assignment, private statuses and shared unscheduled ideas. Existing Plans and
Challenges gain additive Home projections. See the backend's
`docs/home-couple-space.md` and [Home flow](../../flows/home-couple-space.md).
Both copies of contracts and the client route fixtures are synchronized.

Deployment order is additive migration `social.0023_home_couple_space`, updated
backend, then updated client. Test against the intended deployment and account
pair before release. The migration was applied only to disposable test databases.
The new client expects the new endpoints; it must not be released before them.
Older clients retain their existing endpoints and response fields. The invitations
list now also includes unresolved invitations whose proposed time has passed,
so they can be rescheduled or declined instead of disappearing.

## Review and remaining verification

See [native screenshots and verification evidence](../../validation/home-couple-space/README.md).
This phase implements the selected design, not another design exploration.
The isolated live two-account Home/Matrix flow and signed installed iOS keyboard,
draft-return, scrolling and cold-launch Dynamic Type checks now pass. Physical
devices, full screen-reader journeys and production transport remain release
checks. See the validation record for exact Android coverage and limitations.
Passing local tests does not mean the UI is bug-free or verified on every phone.
