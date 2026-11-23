# Find Your Partner

November 2026 follow-up: the partner preview has a visible return action.
Acceptance triggers an authoritative membership refresh while retaining the
first-link confirmation/chat stack. Revoking or replacing an existing membership
still clears private navigation state. The [navigation audit](../validation/navigation-flow/README.md)
records a live isolated Android acceptance and the subsequent membership poll.

Implemented on the existing shared Compose / typed Navigation architecture.

## Design references

- [FindYourPartnerScreen](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=14-220)
- [InviteSentScreen](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=5-974)

Both use the optional Instrument Serif heading pairing, Satoshi body text, warm cards, pill actions
and the existing Material 3 theme. Layouts scroll, respect system/keyboard insets, cap content width
on larger displays and allow action rows to wrap. The pending icon, back icon and landscape
illustration are exact Figma path exports converted to shared VectorDrawable resources. Replace
`partner_invite_landscape.xml` to change the illustration; avatars use live API photos with the
existing image placeholder.

## Workflow

Home → Find your partner → phone search → Connect → Invite sent. Back returns to search without
resending. A pending invitation can be reopened, its status checked, or cancelled with confirmation.
The existing received-invitations route and profile navigation are retained.

Search uses `GET api/social/find-partner/?phone_number=<prefix><number>`. The ViewModel uses the same international-number length validation as authentication and the backend,
accepts formatted input without truncating national numbers to ten digits, and uses the selected country prefix. It
validates blank/incomplete/self searches, cancels obsolete requests when input changes, and ignores
cancelled responses. A result hides received invitations until Cancel search is selected. Search,
outgoing actions and invitation-row actions have separate loading/error states.

The existing repository/API models remain in use. Initialization is deferred until entry because the
shared NavHost constructs this ViewModel before authentication. Guards prevent duplicate in-flight
searches, sends, cancellations and row mutations. Success navigation is acknowledged once; receiving
an invitation is confirmed by the server before removing its row. Failure keeps the invitation and
exposes retry instructions.

## Pagination

The received-invitations endpoint already accepts `page` and `page_size`, and returns `count`,
`next`, `previous`, and `results`. `PartnerInvitationsPagingSource` uses `next` to stop on the final
page, including a non-empty final page. A single cached Pager loads 20 records per page with a
3-item prefetch distance. Only item rendering uses indexed access; keys use `peek`, so computing
keys does not request additional pages. Compose uses Paging load states for initial
loading/error/empty, append loading/error/retry and the end-of-list message. Manual refresh is the
only UI-triggered refresh; recomposition does not restart the Pager.

## Deliberate API adaptations

- Phone-number search replaces Figma's name/username search.
- The API exposes neither an expiry timestamp nor a shareable invitation link. The success screen
  displays “Waiting for acceptance” and a functional Check status action instead of a fabricated
  countdown or Share again button.
- Non-users are directed to create an account. No unsupported SMS/share endpoint is introduced.
- Status checking uses the existing pending-connection endpoint. While Invite sent is visible, a
  lifecycle-owned poll runs at bounded backoff for a few minutes; it pauses in the background,
  never overlaps a manual/foreground check, and is cancelled when the screen leaves composition.
  There is no application-lifetime polling or new notification transport.

## Verification

`./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64`

Tests exercise the real repository and API service with a deterministic Ktor MockEngine: phone
query/validation, stale-search cancellation, duplicate guards, pending-invitation recovery, success
acknowledgement, send/cancel/action failures and retries, acceptance, and authoritative pagination
termination. `python3 tools/theme/check_theme.py` checks token regressions.

Android and iOS compilation and the automated tests are verified. Runtime screenshots, keyboard
behavior on physical devices, and authenticated live-backend integration still require device
testing; this environment has no Android emulator or iOS simulator. The configured development
backend remains unchanged.

## Connected confirmation

`ChatNav.YoureConnected` opens after the received-invitation PUT succeeds, or when a manual,
foreground, re-entry, or bounded Invite sent poll confirms `ACCEPTED`. The completed linking stack
is cleared. View our space and system Back open Home. No application-lifetime polling or new
notification transport is introduced.

The [You'reConnectedScreen frame](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=5-1118) supplies the paired-avatar card, heading, caption and action layout. Figma rate-limited the design-context/export request after metadata retrieval. The celebration check and existing invitation landscape are explicit replaceable placeholders; exact artwork fidelity remains pending. Names and avatars use session/API data. “Together since” uses the confirmed response's updated date when available; no specimen date is hardcoded.

### Navigation migration (2026-10-01)

See [the route inventory](navigation.md). Find Partner, Invite Sent, received requests
and confirmation now share a partner-flow ViewModel rather than an account-wide one.
The old Matches tab is removed; Home's Partner invitations action retains its existing
sent/received lists. Profile previews pass a stable user ID and resolve the authorized
selection from the flow scope; the former full-profile JSON route is removed.

## Notification integration (2026-10-13)

[Notifications V1](notifications.md) adds authenticated, current-state tap routing and
privacy-safe alerts for the implemented transitions. Profile settings include device
permission, server delivery preferences/mute and separate opt-in crash diagnostics.
See that guide for contract compatibility, backend-first rollout and verification limits.

## Search-result profile preview — 19 November 2026

Source: frontend `codex/couple-profile-actions`, based on `4008707`; backend's
freshly fetched `dev-core-architecture` at `421a982`. The backend working checkout
and its unrelated changes remain untouched. Figma reference `14:220` in
`LztysD1YvINX7RwZpnhhTt`: both context and screenshot calls were quota-limited.
The preview layout is an intentional refinement using existing SriSu tokens,
not a claim of new Figma-layer fidelity.

Search → tap the result's avatar → `ChatNav.PartnerPreview(userId: Long)` → Back
returns to the same search and its separate Connect action. The avatar has a
48 dp button target and a named accessibility action. A stale or invalid result
ID cannot select a different profile; the flow ViewModel resolves the clicked ID
against its current successful search before storing the authorized snapshot.
No extra search, invitation, or arbitrary user-by-ID request is made.

Existing implementation paths:

- `features/chat/presentation/findpartner/screen/FindYourPartnerScreen.kt` and
  `PartnerLinkComponents.kt`: search result and clickable avatar.
- `features/chat/presentation/findpartner/vm/FindPartnerViewModel.kt`: selected
  snapshot and ID validation, scoped to the existing PartnerFlow owner.
- `navigation/graph/ChatNav.kt`: existing typed route and profile destination.
- `features/home/profile/presentation/screen/ProfileScreen.kt`,
  `vm/ProfileViewModel.kt`, `state/ProfileUIState.kt`: existing detail screen,
  scoped state and explicit loading/unavailable/ready rendering.

The backend `social/api/serializers.py:PartnerDiscoverySerializer` deliberately
exposes only ID, name, username, searched phone number and photo before linking.
The search mapper copies only that projection. The detail screen does not invent
age, zodiac, location, bio or interests; optional authorized snapshot content is
shown only when present. Basic-only results show a brief explanation instead of
several empty sections. A lost/restored selection offers Go back and reselect.
This is an in-memory preview, not an independently retrievable public profile.

Presentation: neutral centered toolbar, compact portrait/name/username group,
560 dp content cap, standard theme typography and spacing, 96 dp portrait,
stacked identity on narrow/large-text layouts, wrapping interest labels, and
bounded gallery tiles. Gallery loading uses the actual photo URL, filters removed
entries, and provides inline loading/error/retry. Cached identity remains readable
without the former blocking offline dialog. Backend privacy and linking behavior
are unchanged; no migration or deployment dependency.

Verification for this follow-up: Android and iOS simulator Kotlin compilation,
theme checks and diff checks passed. Selected Android tests: 45 passed (9 profile
UI/state/media cases, 26 Find Partner cases, 10 navigation-contract cases). Selected
iOS simulator tests: 40 passed (4 native Compose profile render/Back/empty cases,
26 Find Partner cases, 10 navigation-contract cases). No failures or skips.
Inspected actual renders at 390×844, 320×640 with 1.6× text/dark mode and 640×320
landscape. Android media verification decodes the existing static test image from
its file URL; its BitmapFactory decoder is test-only because Robolectric does not
support Android ImageDecoder file sources. No production image loader changed.

Render artifacts: `composeApp/build/reports/partner-profile/` (ignored build output).
The iOS tests run in the native Compose runtime on an isolated iPhone 17 / iOS 26.5
simulator. These are synthetic UI/state checks, not installed-app live-backend,
physical-device, VoiceOver/TalkBack, or system-keyboard end-to-end verification.
The detail screen has no text inputs; the existing search keyboard behavior is
unchanged. All work remains local in the requested current branch; no push, new
PR, merge, backend edits or unrelated Home edits for this task.

## Loaded individual profiles — 22 November 2026

This supersedes the 19 November cached/basic-only preview above. The user approved
opt-in sharing of bio and interests before linking, keeping extra photos private.

The avatar still opens `ChatNav.PartnerPreview(userId, accessToken?)` in the same
PartnerFlow. The backend search now issues a signed, account/target-bound read
grant; navigation retains it with the ID so restoring a destination does not
require an in-memory `User` object. No token is put in a request URL or logged.
`ProfileViewModel` loads `GET /api/social/partner-profiles/{id}/` through the existing
ConnectionRepository/service and shared session-aware HTTP client. Requests are
cancelled on selection changes; results must match the selected ID. Resuming the
screen reloads the current privacy and invitation state. A failed/revoked read
clears prior details. Expired search grants give a clear return-and-search path.

The compact, scrollable profile retains the existing theme, bounded width,
wrapping identity and accessible actions. It renders real bio/interests when
permitted, with separate private-details, genuinely empty, loading, failed-read,
retry and unavailable states. Extra album photos and location are not displayed.
Connect uses the existing FindPartnerViewModel invitation mutation/idempotency
key. Duplicate taps are disabled; search input/results remain intact. Outgoing
requests open Invite Sent; incoming requests open the existing requests screen;
connected people can continue Home. No new acceptance or chat behavior is added.

Edit Profile adds one opt-in switch, off by default. Saving uses the existing
owner-only profile update; drafts, unsaved-change detection, session persistence
and old clients omitting the field retain their expected behavior. A backend that
does not supply the preference leaves the switch unavailable rather than guessing.

Backend source/contract and deployment notes: `../SriSu-backend/docs/partner-profiles.md`.
Deploy backend with additive authentication migration 0023 before this client.
Frontend branch: `codex/couple-profile-actions`, base `4008707`. Backend branch:
`codex/couple-chat-rebuild`, base `6e0935c`; fetched integration `421a982`. No push,
PR, merge, production database migration or deployment is included in this task.

Design continuity is derived from existing PartnerLink theme tokens and the
previously reviewed profile implementation. Relevant Find Partner Figma reference
is `LztysD1YvINX7RwZpnhhTt`, node `14:220`; prior inspection was quota-blocked.
There is no verified new detail frame or claim of pixel matching to Figma.

Verification for the loaded-profile follow-up:

- Android: 95 selected tests passed, zero failures/skips, including profile
  rendering, network/state, invitation duplicate prevention, save/session
  persistence, navigation, existing couple-profile regression and route contracts.
- iOS: 47 initial selected tests passed; the final 9 UI/form tests also passed
  (52 distinct cases across both runs). Native Compose runtime on the isolated
  iPhone 17 / iOS 26.5 simulator; standard 390×844, small 320×640 at 1.6× text,
  dark mode and 640×320 landscape. Back/Connect, scrolling, switch/save reachability
  and Edit Profile Next/Done focus actions were exercised. Renders inspected.
- Real Ktor → disposable Django HTTP test passed for exact-phone search, detail
  load, owner opt-in, duplicate invite replay, incoming/outgoing status and opt-out.
  The server, temporary database/media and synthetic session fixture were removed.
- Backend maintained suite: 311 run, 286 passed, 25 skipped by the existing test
  environment; no failures. Includes 9 new privacy/authorization/compatibility
  cases. Django system, migration consistency, schema/fixture, theme and diff
  checks passed. Only the new contract additions were synchronized; unrelated
  pre-existing cross-branch contract drift was not overwritten.
- No installed-app/physical-device end-to-end run, real account, live production
  backend, native keyboard-overlay or VoiceOver/TalkBack verification is claimed.
- Hash comparison verified the 8 unrelated pre-existing frontend files (including
  Home drafts and the Xcode project edit) and 40 unrelated backend files unchanged.
  Existing edits to shared route/schema/manifest files were extended, not replaced.

Ignored render artifacts: `composeApp/build/reports/partner-profile/`. Logs for
this local run: `/tmp/srisu-partner-detail-*.log`.

Final visual refinement keeps the sharing switch beside its label and puts the
explanation underneath, reducing height at enlarged text. The affected Android
UI/save tests (20 cases), the final iOS UI/IME cases (9), and an additional Android
full-label/switch/save visibility check passed after this refinement. Both
isolated task simulators were removed; existing user simulators were preserved.

Publication follow-up, 22 November 2026: the user authorized pushing and merging
both repositories into `dev-core-architecture`. Frontend publication extends the
existing `codex/couple-profile-actions` PR #33, retaining its couple-profile menu
fix. Backend publication uses `codex/partner-profile-details`, based on the fetched
integration `e265a1d`, with only this feature's patch. The original backend
checkout remains on `codex/couple-chat-rebuild`; no local files are moved or
reset. The two complete published contract bundles match. This supersedes the
implementation-phase no-publication note above; deployment/migration remains a
separate rollout operation.
