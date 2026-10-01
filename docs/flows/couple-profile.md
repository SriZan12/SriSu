# Couple Profile implementation record

Working baseline: frontend `dev-core-architecture` at 7c3f1ed; backend same-named
branch at 9303cdd. Integration branches remain frontend dev-new-theme and backend
dev. Backend draft social/models.py was explicitly taken over on 2026-10-01; an
unaltered inspection backup is in /tmp/srisu-couple-models-takeover.py.

## Design evidence and screen-to-data mapping

Figma file LztysD1YvINX7RwZpnhhTt, page 1:4, **Couple UI final**. Connector access
was quota-limited. The user supplied Couple Profile UI Final.png (11661×23196).
All 17 visible screens/states were inspected in full-resolution crops. Individual
frame IDs, actual component properties and hidden prototype states are unavailable.
The screenshot defines presentation; photos, names, answers and counts are samples.

| Screen/state | Data/action and source | Backend change / permission | KMP behavior and empty/error state |
| --- | --- | --- | --- |
| Day one | Members, connected date, cover, story/song/interests | Active server membership; derive counts | Owner profile; meaningful empty section prompts |
| Just connected / cover added | Existing anniversary and selected cover | Guarded cover delivery and scoped date update | Profile with real metrics; date picker is a derived interaction |
| Write how it started | Three author-specific answers | Unique couple/author/prompt; never edit partner answers | Separate story editor; isolated draft, max 240 per answer |
| Your side done | Own answers, partner contributions | Scoped story read, current partner attribution | Partial state, invitation action |
| Asked → their chat | Question invitation | Existing private couple room only | Existing chat transport; profile destination |
| Partner's side | Partner answers and own response/confirmation | Author derived from authentication | Separate draft, confirmation copies only explicitly selected answer |
| Both done | Combined contributions, song, common interests, plans | Derived intersection and aggregates | Completed profile, real links |
| Story skipped | Incomplete author contribution | No fabricated content | Write prompt; skip leaves persisted data unchanged |
| Change an answer | One prompt | Narrow author update and expected revision | Bottom sheet with draft and removal |
| Take an answer off | Remove only own answer | Partner answer preserved | Destructive confirmation; failure retains sheet |
| Full story | Answers and change history | History member-only, paginated | Read screen and separate Edit action |
| Our song | Title, artist, **band**, optional note | Shared section, optimistic concurrency | Separate form/removal; metadata only, no promised audio playback |
| Your interests | Actor's personal interests; partner suggestions | Reuse UserInterestModel; only actor writes | Search, catalogue/custom choices; shared intersection derived |
| Plans | Private couple plan list and responses | New bounded domain needed; no existing Plans feature | Upcoming/past, empty states and real detail/save flow |
| New plan | Title, proposed date/time and response | Private plan persistence and existing couple chat | Sheet; server confirmation before success |
| Cover photo | Upload/library and eligible Moment photos | Decode/size validation, guarded association and delivery | Pick then preview; cancellation retains previous cover |
| Position it | Vertical focal point | Narrow cover revision update | Drag/accessible slider; confirm save |

Existing landscape drawables and shared tokens/icons are reused. Galaxy/sample
portraits are user data, not bundled assets. Privacy controls are an explicitly
requested additional interaction with derived styling, not an inspected Figma frame.
No visitor design was supplied; visitor presentation reuses only permitted sections.

## Decisions and permission matrix

User selected publication of selected sections with **both partners' consent**.
Proposals bind current membership and exact section content. Edits invalidate consent
for changed content. Either current member may immediately remove publication.
Private account attributes (phone/email/location/auth settings), change history,
private chat and plans are never visitor fields. A visitor message action cannot use
the private partner room. Personal Faves remain personal. Moment creator-only edits,
expiry, blocking and original-audience checks remain authoritative.

| Action | First/second active partner | Unrelated signed-in viewer | Former member | Blocked/inactive | Anonymous |
| --- | --- | --- | --- | --- | --- |
| Read private profile/history/plans | Yes, equally | No | No | No | No |
| Edit shared song/date/cover | Yes, equally, with revision | No | No | No | No |
| Edit answers/interests | Own contribution only | No | No | No | No |
| Propose/approve publication | Both current partners required | No | No | No | No |
| Revoke publication | Either current partner | No | No | No | No |
| Read approved sections | Yes | Approved subset only | Same restricted visitor policy | No | No |
| View Moments | Existing audience/expiry policy | Existing public policy | Existing policy | No | No |
| Fave another couple | Own personal preference | Own personal preference | Own preference if eligible | No | No |

## Incremental implementation plan

1. Extend existing social models/services with answer/song/history, section revisions,
   current-membership-bound publication and private plan records; additive migrations.
2. Add explicit profile detail/section/media endpoints next to legacy couple-profile.
   Validate current membership and blocking on every read/write. Reject stale same-
   section edits with 409, preserve unrelated fields, reject unknown fields.
3. Extend existing injected Ktor service/repository, session generation checks and
   account-scoped ViewModels; do not persist private profile data in Room.
4. Compose distinct profile/story/song/interests/plans/cover editors and privacy
   controls. Stable IDs in navigation, confirmed saves, draft retention, unsaved-back
   confirmation, refresh/re-entry synchronization and permission-loss clearing.
5. Paired contracts, permission/concurrency/media/state tests, builds and rendered
   verification where available; review diffs, publish backend then frontend.

## Baseline evidence

- `python3 tools/workspace.py status --remote`: passed with network access; both
  working branches preserved, integration refs differ intentionally.
- `python3 tools/workspace.py verify frontend`: passed (existing Gradle tasks were
  up-to-date), contract check and theme check passed, Android tests/compilation and
  iOS simulator compilation passed. Initial sandbox-only attempt was blocked by
  Gradle cache permissions; rerun succeeded.
- Backend committed baseline exported with `git archive HEAD` to a temporary
  directory, leaving the uncommitted draft untouched. `.venv/bin/python
  tools/workspace.py test`: 136 tests, passed, 10 PostgreSQL-only skips.

This document is an implementation record; later sections must state actual outcomes,
not treat the plan or successful compilation as device/integration validation.

## Implemented ownership, navigation and synchronization

`features/coupleprofile/data/CoupleProfileRepository` uses the existing injected
Ktor client, ApiEnvironment, SessionCoordinator and typed results. A route-scoped
CoupleProfileViewModel owns the current profile and isolated editor draft; no
private Room cache, state singleton or second HTTP client was introduced. Koin and
existing account-scoped navigation supply the ViewModel. Home exposes Our profile
and Explore couples. Explore reuses the existing authenticated public-Moment feed;
it does not make unpublished profiles anonymously discoverable or alter ranking.
Expired preview rows are removed while foregrounded. All route arguments are IDs
and small destination keys, not serialized profile snapshots.

Implemented editors: full story, one-answer sheet and removal confirmation,
partner contribution confirmation, song/removal, personal interests/custom choices,
cover camera/library/Moment selection and positioning, new plan/date/time and plan
response. The shared date and publication editors are documented derived additions.
Existing private chat displays server-authored question/plan cards with profile
navigation. No visitor action opens the couple's private partner chat. Fave writes
use the viewer's existing personal Fave endpoint. Moment counts are informational;
this task does not replace the existing Moment viewer or creator-only permissions.

Confirmed saves update the profile immediately and leave the editor once. Failures
retain drafts; stale section revisions return409 with refresh/reload choices.
Back prompts before discarding a dirty draft. Duplicate taps are gated. Request,
page and session generations reject late responses after switching accounts or
profiles; permission loss clears protected drafts and media. Foreground refresh
runs every30 seconds and on re-entry, refreshing visible story/plan lists too.
Remote edits can therefore take up to30 seconds to appear without manual refresh.
No new WebSocket subsystem was added. Private media uses authenticated first-party
bytes with bounded in-memory retention and Coil memory/disk caches disabled.
Underlying private HTTP body logging was disabled in the shared factory; existing
content-free diagnostics remain available.

See backend `docs/couple-profile.md` for exact field limits, API routes, authorization,
consent fingerprints, transactions, replay rules and migration/rollout requirements.
The backend owns contracts/core-1; the client-generated snapshot is checked rather
than independently edited. Current digest:
`31ad507155ad95072d8e71d06e8a0bbb5566179e845f0533c5d693f3574a1047`.

## Visual evidence and intentional deviations

Reused asset/component map:

| Reference | Implementation |
| --- | --- |
| Empty landscape/cover | Existing onboarding_landscape drawable; no duplicate asset |
| Serif names/headings and body | Existing SriSuPartnerLinkTypography and Satoshi resources |
| Cards, buttons, form states | Existing theme tokens/SriSuButton, Material3 shared controls |
| Pencil, music, calendar, person, camera, arrows | Existing Material vector icon library |
| User portraits, covers, song title | Backend data, not bundled Figma sample content |
| Publication controls / visitor shell / discovery | Derived UI for explicit consent/access policy |

No Figma image exports were added. The source board's child node IDs are unknown;
page reference is [Couple UI final,1:4](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-4).
The supplied PNG is the inspected visual source, not live component metadata.

Actual Android Robolectric/native-graphics renders with **synthetic test data**:

- [Day-one profile](couple-profile-validation/android-owner-day-one.png)
- [Populated profile](couple-profile-validation/android-owner-populated.png)
- [Single-answer sheet](couple-profile-validation/android-answer-sheet.png)
- [Small song editor](couple-profile-validation/android-song-small.png)
- [Dark song editor,1.6× font scale](couple-profile-validation/android-song-dark-large-text.png)

The implementation preserves section hierarchy, typography family, warm surfaces,
overlapping portraits and separate save flows. It is **not pixel-exact**: the cover
uses an inset card instead of a full-bleed header; minimum touch targets and larger
readable fields make the page taller. Date/time input uses accessible Material
pickers; new-plan creation is a separate editor instead of a chat-overlay sheet.
Generic suggestion imagery from the plan mockup was not invented/bundled. Empty
media uses an existing vector placeholder. Dark mode and publication controls are
derived from the established theme. Modal captures include the dialog surface but
not the underlying activity composition, a Robolectric capture limitation.

## Final verification (2026-10-01)

| Command/check | Outcome and limits |
| --- | --- |
| python3 tools/theme/check_theme.py | Passed:96 retained Figma colors;24 contrast pairs; no raw style violations |
| python3 tools/core_contracts.py --backend ../SriSu-backend | Passed:paired contract digest and generated fixtures |
| JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 :composeApp:assembleDebug | Passed; Android compilation included;13 rendering tests plus7 profile-state tests; existing optional live test skipped in ordinary run |
| JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' python3 tools/core_integration.py --backend ../SriSu-backend | Passed separately with actual disposable HTTP/WebSocket backend and synthetic partners/visitor |
| DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/srisu-couple-xcode CODE_SIGNING_ALLOWED=NO build | Passed:actual simulator app/framework linking, not just Kotlin compilation |
| Backend workspace check/migrations | Passed; no missing migration changes |
| Backend disposable PostgreSQL selected suites |158 tests passed, including real same-section concurrency, membership, publication, media and auth regressions |
| Physical Android/iOS, camera capture, screen-reader traversal, keyboard/device safe-area testing | Not run; native camera code compiled on both platforms but needs device validation |
| iOS rendered Couple Profile comparison | Not run; simulator app build is not visual validation |

During development, compilation caught a missing time API opt-in and field name;
these were corrected. Render harness failures (multiple modal roots, looper progress)
and an incorrectly enveloped mock discovery response were corrected without
relaxing production permissions or tests. Final listed checks passed. Existing
Gradle deprecation/expect-actual warnings and Xcode's Info.Debug.plist resource-phase
warning remain; no successful device or pixel-accuracy claim is made.

## Deployment and remaining limits

Apply backend social0011–0013/chat0003 migrations and ship server support before
the client. All new tables/fields are additive; users and relationship IDs remain.
Legacy JSON routes remain, but cover image URLs now require authentication. Older
clients using unauthenticated image loaders need this client update. Production
proxies/buckets must deny public direct access to private Moment and both old/new
cover prefixes; Django cannot configure an external CDN. Run existing orphan cleanup
for abandoned uploads. See backend rollout notes before deployment.

No production server was restarted and no database was migrated outside disposable
tests. Repository pushes are not deployment. Content already downloaded cannot be
retracted. There is no licensed song playback or new visitor messaging feature.
No policy decision remains unresolved; physical-device and exact Figma-property
validation are the outstanding verification limits.

## Publication dependency

Backend published commit: `6433d60556d0c55e1ae06a1848e788c0fa487126` on
SrizanKhadka/SriSu `dev-core-architecture`. Its tree exactly matches the locally
validated backend tree `87cd7add9824ec1950fe2aeb2b68c889034bb2b7`. Terminal Git lacked
HTTPS credentials, so the connected GitHub integration performed a normal
(non-force) fast-forward publication. This frontend change requires that backend
commit or a descendant containing its migrations/API contract. Neither integration
branch was modified. The final delivery records the frontend publication SHA.

## Interest visibility and profile preview follow-up (2026-10-01)

The original overview rendered only `interests.shared`, which is the intersection
of both partners' selections. A correctly saved individual selection therefore
looked missing until the partner selected the same interest. The member overview
now also renders `interests.mine` under Your interests and `interests.partner`
under the partner's interests. The You both love section retains its shared meaning.
Visitor rendering still excludes both personal lists; publication consent and the
backend response contract are unchanged.

Home now labels the entry View profile. Inside the member profile, View profile
opens a read-only overview with cover/date/section-edit/sharing controls hidden.
It retains the member's permitted content and plan summary; it is a private member
preview, not a simulation of an unrelated viewer or an action that publishes data.
Back returns to the normal member profile. No duplicated profile snapshot, new API,
persistence layer or fake preview content was added.

Regression rendering checks assert that a selected interest appears with an empty
shared list, and that the real View profile button opens an overview without Edit
or Save controls. Android tests/build, iOS simulator Kotlin compilation, paired
contract consistency and theme checks passed for this frontend-only change. Native
physical-device verification was not run; no backend migration is needed.
