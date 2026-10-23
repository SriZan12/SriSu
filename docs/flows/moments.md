# Home foundation and Moments — implementation record

Work started 2026-10-22. Frontend `dev-core-architecture` starts at
`98f17e769bf4d0a43b444919f6f81aac91b76b3b`; backend
`codex/couple-chat-rebuild` at `2a7d1f7d502bbaf3fb11b2330835a0d64bbe3ea5`.
Frontend contains the uncommitted Matrix chat UI/media recovery and motion work,
patch artifacts, and notification planning. Backend has a pre-existing `.DS_Store`
modification. Preserve these; no switch, reset, commit, push, deployment, or live
data migration is authorized. Live integration refs were read: frontend
`3fa6822ea05578d64cca8891c5e3fdb0a831897a`, backend
`ecc8592307114e7d154bda9612e6826e2ca8ba90`.

## Audit and design mapping

Figma `LztysD1YvINX7RwZpnhhTt`, page `1:3`, inspected in browser on 2026-10-22.
The connector is Starter-quota limited. Visible Home variants and the four Moment
frames were inspected; individual layer IDs, component properties, and hidden
prototype connections are unavailable. Existing design-system tokens are reused.
Sample people, photos, timestamps, and counters are never production fixtures.

| Figma/action | Existing implementation | Required work |
| --- | --- | --- |
| Greeting, two Moment avatars, profile/chat entry | Home is a five-button placeholder; account/membership and shell already exist | Real identity, agreed date only, per-author seen summaries; retain existing shell |
| Camera, library, preview, caption | Existing Android/iOS native chat photo adapters | Reuse adapters; five-photo selection, reorder/removal and recoverable upload UI |
| Just you two/Public sheet, Share moment | Existing authenticated private/public Moment CRUD | Private staging, idempotent publish, ready media and publication-time expiry |
| Full-screen photo, segmented progress, caption/reply | Backend sequence and guarded photos; no complete KMP viewer | Lifecycle-aware viewer, explicit display-based views, expiry and failure states |
| Seen by partner/reacted, Open chat | Aggregate views only; retrieval records views; community notes are separate | Partner receipt/reaction projection; encrypted reference integration, no persistent preview copy |
| Creator edit/delete | Backend creator-only CRUD | Narrow caption edit, revision conflicts, delete confirmation; derived controls, not unseen Figma fidelity |

Backend reusable foundation: `social/models.py`, `services/moment_service.py`,
`api/moment_views.py`, signed sequence cursors, audience/block selectors, and
`cleanup_moments`. Gaps: storage I/O under transaction, no staged publication or
request deduplication, GET retrieval records views, no dimension/metadata policy.
KMP reusable foundation: Ktor `safeRequest`, SessionCoordinator, BaseNavigation,
entry-owned ViewModels, native photo adapters, guarded byte-loading and theme.

No existing reporting API was found. Existing public Moments require authenticated
discovery and respect blocking; this phase does not add a moderation system or
expand public content to anonymous readers. Moments are server-readable, not E2EE.

## Baseline

`python3 tools/workspace.py verify all` passed before edits (log:
`/tmp/srisu-moments-baseline.log`). Backend: 231 tests, 11 PostgreSQL-only skips.
Frontend theme, Android unit tests, Android and iOS simulator Kotlin compilation
passed. These checks do not establish native picker, camera or live Matrix behavior.

## Implementation and final evidence


The implementation reuses the existing published Moment model, private media
storage/cleanup, Global/Faves selectors, Ktor client, SessionCoordinator, secure
storage, Koin, BaseNavigation, Matrix encryption/outbox, and native photo adapters.
New `MomentDraft`/`MomentDraftPhoto` rows are upload staging, not another published
content system. Either original active partner publishes; creator-only caption
updates/deletion, partner-only hearts and separate visitor appreciation notes are
server enforced. No backend, dependency, authentication or navigation replacement.

### Implemented flow and boundaries

- Home renders real names, optional agreed anniversary, per-creator active/unseen
  summaries and missing-avatar fallback. Own Moments say Shared, not fabricated
  seen status. Stable list keys/remembered scroll state survive Home refreshes.
  Failures retain the greeting and disable stale Moment actions. Unlinked users
  retain the existing Find partner/invitation navigation.
- Creation uses the existing native picker/camera (gallery multi-selection up to
  five total photos, one camera capture at a time), stable photo IDs, local previews/reordering/removal/caption, explicit
  private-default audience sheet, measured upload progress and confirmed publish.
  Secure storage persists caption, audience, IDs/order and exact publication
  intent; unuploaded image bytes remain only in the current screen's memory.
  Leaving explains this limitation. Uploaded photos recover from the server.
  The same publication ID/payload reconciles lost responses. A photo whose upload
  response was lost is removed remotely before it disappears from the draft.
- Viewer uses ordered photos, fit sizing, real caption/identity, segment progress,
  pause/hold, reduced-motion manual navigation, lifecycle revalidation, actual
  presentation-based views, partner heart/receipt, creator caption edit/delete,
  and explicit loading/error/empty/expired/deleted states. Decode failure stops
  automatic progression. No prefetch records a view.
- Server time plus conservative request elapsed time defines expiry deadlines;
  changing the phone clock cannot extend visibility. Expiry clears visible bytes.
  Resume refetches eligibility; account switching rejects late images/callbacks.
- Global/Faves uses the existing feed and personal Faves API. Visitor notes stay
  in the existing authorized notes pathway. The viewer shows paginated notes and
  private partner replies; it does not create another inbox or notification system.
- Reply in chat opens the existing secure conversation with cancellable Moment
  context. Sending validates eligibility and room relationship, then encrypts the
  text plus an ID-only `external_url` through Matrix. Typed reference cards resolve
  current availability and expire; no Moment photo/caption is copied permanently
  into chat. Deleting a reference cannot delete the Moment. Other-device deletion
  is discovered on revalidation/open, not through a new realtime event service.

### Principal files

Frontend: `features/moments/{data,presentation,navigation}`, existing
`EntertainmentScreen.kt`, Home/Chat/Profile graphs and Koin modules;
`MatrixMessageMapping.kt`, `SecureChatViewModel.kt`, `SecureChatScreen.kt`,
`TrixnityMatrixChatRepository.kt` and the existing `MessageBubble` typed-card slot.
Focused tests are in `features/moments`, `ChatRecoveryTest`, route-contract tests
and the existing `CoreLocalTransportIntegrationTest`.

Backend: `social/services/moment_publication.py`, `moment_service.py`,
`api/moment_drafts.py`, `moment_views.py`, `moment_serializers.py`,
`couple_views.py`, existing models/signals/cleanup, migration 0016 and
`social/test_moment_publication.py`. Backend-owned core-1 contracts/fixtures and
frontend snapshot are updated together. See
[backend lifecycle and API guide](../../../SriSu-backend/docs/couple_moments.md).

### Compatibility and rollout

Deploy backend plus additive `social.0016_couplemomentmodel_revision_and_more`
before this client. Existing records/audiences remain; no live migration or
release was performed. Legacy detail GET still counts views; this client opts
out with `record_view=false`, then POSTs `viewed` only after presentation. Legacy
CRUD remains available. New revisioned caption updates are narrow and cannot
renew expiry. Photo staging uses JPEG/PNG/WebP content validation, 10 MiB/40MP/
12000px input bounds, 2560px output and metadata stripping. Storage processing
happens outside application transactions; row locks serialize count/publication.

Unpublished staging lasts seven days. Existing published-file cleanup remains
30 days after expiry. Lightweight published replay tombstones retain only IDs,
audience and payload hash until account/couple deletion. Private media URLs
require current authentication and eligibility, with no-store caching; exported
copies cannot be revoked. Embedded notes/replies are bounded to 50, with existing
paginated endpoints retained for older entries. The new viewer exposes active
Moment notes, not a separate historical inbox.

### Visual review and intentional adaptations

[29 individual Android/iOS captures and metadata](../validation/moments/README.md)
include the actual previous Home, empty/active Home, selection/preview, audience,
upload/failure, partner/public/creator viewer, caption editing, expiry, dark theme
and 320dp/1.6× text. These render production composables with test-only synthetic
content. They are not live account sessions or generated screen mockups.

The focused correction pass shortened the Home Add action to prevent heading
wrap, reduced the preview height, consolidated removal into a selected-photo
control, made sheets scrollable, and handled image decode failure explicitly.
The Android software-canvas capture needs a software-bitmap test loader; this
setting is confined to tests. Verified photo rendering is present in final files.

Derived controls: explicit previous/next and pause buttons accompany gestures;
private replies continue in the existing chat composer rather than duplicating
its encrypted send logic in the viewer. Caption editing and deletion use standard
sheet/confirmation components because their Figma detail was unavailable.
The serif greeting, neutral surfaces, Satoshi text and existing Moments accent
reuse established tokens. No sample Spark/Challenge/Plan cards were added.

### Verification and remaining limits

Reproducible commands (logs are local, not committed):

```sh
python3 tools/theme/check_theme.py
python3 tools/core_contracts.py --backend ../SriSu-backend
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64
python3 tools/core_integration.py --backend ../SriSu-backend
./gradlew :composeApp:iosSimulatorArm64Test --device 0055683D-7DBA-44C7-855D-4AA64123C522 --tests 'com.srisu.srisu.features.moments.*' --tests 'com.srisu.srisu.features.chat.ChatRecoveryTest'
# From the backend checkout:
.venv/bin/python tools/workspace.py test
.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin
.venv/bin/python -m django makemigrations --check --dry-run --settings=srisu.workspace_test_settings
```

Native app: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp
-configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build` (local derived-data
path used). Final logs: `/tmp/srisu-moments-final-targeted.log`,
`/tmp/srisu-moments-backend-final.log`, `/tmp/srisu-moments-postgres-2.log`,
`/tmp/srisu-moments-http-1.log`, `/tmp/srisu-moments-xcode-final.log`.

- Initial cross-repo baseline passed before edits: backend 231 tests (11 skips),
  Android tests/compilation, iOS shared compilation and theme audit.
- Isolated PostgreSQL: 245 tests passed with no skips, including simultaneous
  uploads and duplicate publication. SQLite runner: 245 tests, 12 PostgreSQL-only
  skips. Migration drift check uses isolated workspace test settings.
- Real KMP ↔ disposable Django HTTP integration passed with synthetic creator,
  partner and visitor accounts: photo upload/publish/replay, private media denial,
  views/reactions, creator edit denial/acceptance, notes/replies and deletion.
  The temporary server, DB, media and credentials were removed by its runner.
- Android full suite: 254 tests, 253 passed/1 opt-in skip, APK assembly passed; the opt-in HTTP test is
  skipped in the default suite and passes in its separate run.
- iOS simulator: 31 tests passed (including 11 UI renders), executing shared state, chat-reference recovery and actual
  Compose/Skia screen renders. Native Xcode Debug/arm64 simulator build passes
  with signing disabled. No app was installed over the user's existing app.
- Theme audit passes (96 source colors; 24 M3 role pairs >=4.5:1, minimum 4.75:1);
  no new raw UI colors/typefaces/corner values. Both repositories pass diff checks.

No reporting workflow exists to reuse: this remains a public-release gap.
Figma component properties/prototype edges were quota-blocked; visible frames
were inspected in the browser, so exact component-level fidelity is not claimed.
Physical-device camera/picker permission flows, real keyboard transitions,
iOS interactive back gestures, and a live two-device Matrix exchange were not
validated in this task. The preserved native adapters and encryption pipeline
compile, but that is not runtime proof. Changes remain uncommitted in both
original branches; no commit, push, Figma write, production migration or deployment.

### Photo picker correction (2026-10-23)

The native Android and Swift camera/library adapters emit `kind: "photo"`.
The composer incorrectly expected `"image"` and reported every mismatch, including
unreadable bytes, as a 10 MiB size error. The earlier state tests used the same
incorrect event value and therefore missed the broken native boundary.

The composer now accepts the real native event, distinguishes unsupported media
and unreadable data from oversized payloads, and preserves selected photos and
captions after rejection. The limit remains inclusive at 10 MiB (10,485,760 bytes),
matching backend validation; no backend or upload-limit change is needed.
State tests now use the native callback shape and cover small-photo publication,
the exact byte boundary, an oversized rejection followed by a successful retry,
unsupported/missing data, cancellation, and the existing draft/session safeguards.

Verification: the new small-photo regression failed before the fix; after it,
all 12 Moments state tests passed on Android and iOS simulator, and all 13 isolated
backend publication tests passed. The native arm64 simulator build passed.
The old installed app also reproduced the false warning with a 296-byte synthetic
PNG selected through the iPhone 17 / iOS 26.5 photo picker. Updated builds were
installed on the local iPhone 17 and iPhone 17 Pro simulators.
Both updated app instances returned to sign-in. The post-fix native picker preview
could not yet be checked in the accessible simulator window; the passing iOS
state tests exercise the real callback shape, not PHPicker itself. Simulator
camera capture and physical-device photo selection remain unverified.


### Gallery multi-selection and bottom navigation (2026-10-23)

Frontend remains on `dev-core-architecture` at base
`98f17e769bf4d0a43b444919f6f81aac91b76b3b`; backend remains on
`codex/couple-chat-rebuild` at `2a7d1f7d502bbaf3fb11b2330835a0d64bbe3ea5`.
No backend changes or migration are required for this follow-up.

The existing general-purpose `GalleryManager` supports multiple files, but its
legacy iOS path copies originals into cache and accumulates asynchronous results
without preserving order. Moments instead extends the already-used native media
adapter with `pickPhotos(limit, result)` and a typed `NativePhotoBatch`. Both
platforms reuse their bounded photo preparation; images are processed sequentially
outside the UI thread, with one ordered result. Swift uses PHPicker ordered
selection, Android uses PickMultipleVisualMedia (single selection for one remaining
slot). Provider results are bounded again before decoding. Close/cancellation and
session-generation checks prevent results from entering a disposed or different
account's draft. Existing single-photo chat and camera callbacks remain available.

The five-photo total and inclusive 10 MiB per-prepared-photo limit are unchanged.
A new batch appends to the existing draft; successful files survive partial
failures, with explicit counts for unreadable/oversized files. Cancellation keeps
the draft unchanged. Order, removal, upload retry and publication use existing
Moment state/repository logic. Add-photo/camera controls wrap at larger text sizes.

Bottom navigation follows the visible Home frames on Figma page `1:3`: a floating
warm-neutral pill with Home, Chat and Profile. It reuses theme shapes, colors,
spacing, typography and the installed Material icon family. The connector returned
its Starter quota limit, so browser-visible layout is the reference; exact Figma
layer metrics and original icon assets were unavailable. Icon substitutions,
accessibility-sized controls, opaque surfaces and derived dark mode are intentional
adaptations. No sample notification badge is shown. Home's existing Explore Moments
action retains discovery, now with a back button; Chat opens the existing full-screen
conversation and returns to its originating root. The bottom bar remains hidden in
chat, Moment composition/viewing and discovery.

Checks: 31 targeted Android tests passed (15 Moments state, 9 navigation ownership,
4 chat playback ownership and 3 navigation renders); 17 iOS simulator tests passed
(15 Moments state, 2 navigation renders). Selection, preview and small-screen
composer renders also passed. The final small-screen correction was re-rendered;
iOS shared compilation, the native Xcode arm64 simulator build, theme audit and
`git diff --check` passed. Logs: `/tmp/srisu-multi-navigation-checks.log`,
`/tmp/srisu-multi-layout-check.log`, `/tmp/srisu-multi-navigation-xcode-final.log`.
See [follow-up captures](../validation/moments/multi-select-navigation/README.md).

Native-gallery verification was prepared in a separate temporary UIKit test app
that compiles the production Swift adapter and Kotlin framework. The Mac locked
before interactive selection could be checked; no native multi-selection success
is claimed. Android native gallery UI and physical-device camera behavior also
remain unverified. The app builds and simulator Compose renders are independent
from that limitation. Large photo byte conversion stays on the iOS preparation
worker queue; only its completed callback is dispatched to the main queue.

Follow-up after unlock: the user selected iPhone 17 only and confirmed the
Home / Chat / Profile navigation. The temporary UIKit test app was rebuilt with
the current production adapter and KMP framework. On iPhone 17 / iOS 26.5, native
PHPicker returned five synthetic photos in the selected order with zero failures;
the three-slot cap rejected a fourth selection, the one-slot case succeeded, and
cancellation returned an empty, successful batch. This supersedes the earlier
native-gallery lock limitation. The main app still showed sign-in, so the complete
authenticated composer/publication path is not claimed as interactively verified.
See the follow-up capture and details in the linked validation record.

### Authenticated iPhone 17 verification (2026-10-23)

The main app was subsequently verified against the local backend on iPhone 17 /
iOS 26.5. This supersedes the sign-in limitation above for multi-selection,
ordering/removal, private/public publication, caption editing, draft restoration,
software-keyboard layout, viewer controls, Global/Faves, both sides of private
appreciation, and Home/Chat/Profile routing. Expiring a synthetic Moment while
backgrounded correctly cleared its photo on resume. Approved QA deletion showed
the deleted state. See the [full coverage matrix and actual app captures](../validation/moments/full-flow/README.md)
for the distinction between native, automated and still-unverified paths.

Opening chat from a Moment exposed a stuck-verification case. A regression test
confirmed that nested Matrix bootstrap timeouts left availability at Loading.
Open/foreground verification now handles these as retryable errors while preserving
parent cancellation, room/account ownership checks and the closed sender gate.
73 targeted client tests, the real client-to-disposable-server integration, 245
isolated PostgreSQL tests, iOS shared compilation and native Xcode build passed.
No backend source/API/migration change was needed for this verification follow-up.
