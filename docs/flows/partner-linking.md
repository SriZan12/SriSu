# Find Your Partner

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

Search uses `GET api/social/find-partner/?phone_number=<prefix><number>`. The ViewModel retains the
existing 10-digit local-number contract, accepts formatted input, uses the selected country prefix,
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
- Status checking uses the existing pending-connection endpoint; there is no background polling.
  Notification delivery remains the existing backend/app responsibility.

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

`ChatNav.YoureConnected` opens after the received-invitation PUT succeeds, or when the existing Check status request confirms `ACCEPTED` for a sent invitation. The completed linking stack is cleared. View our space and system Back open Home. No polling or new notification transport is introduced.

The [You'reConnectedScreen frame](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=5-1118) supplies the paired-avatar card, heading, caption and action layout. Figma rate-limited the design-context/export request after metadata retrieval. The celebration check and existing invitation landscape are explicit replaceable placeholders; exact artwork fidelity remains pending. Names and avatars use session/API data. “Together since” uses the confirmed response's updated date when available; no specimen date is hardcoded.
