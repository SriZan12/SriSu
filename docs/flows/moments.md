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

### Integrated QA follow-up (2026-10-24)

Private appreciation replies preserve local IME selection/composition while
keeping text in the existing ViewModel state, fixing a reproduced iOS caret jump
that reordered typed characters. Reply permission, sending, retry and persistence
contracts are unchanged. Both partners replied in the installed iPhone app;
multi-photo publication, private media and cross-feature chat were also checked.
See [integrated QA evidence and remaining gaps](../validation/full-app-qa/README.md).

## Explore and personal Faves — 26 October 2026

Explore is now a root destination in the existing shell: **Home → Explore → Chat →
Profile**. `HomeNavigation.Explore` remains the stable route owned by the existing graph;
Home's discovery shortcut selects the same saved tab. Chat stays full screen and Back
returns to its originating root. No extra NavController, viewer or profile was added.

`features/explore` owns the two retained Global/Faves lists and paginated Manage Faves
sheet. Global cards use the current public identity consent and eligible Moment preview.
Identity opens `CoupleProfileDestination(id)`; media opens `MomentViewerDestination(coupleId)`
with no forced preview ID, preserving the viewer's chronological/first-unseen behavior.
Bookmark actions clearly save the **couple**, separately from a Moment heart.

Faves is personal to the signed-in account. The active feed never includes unsaved couples;
the saved library remains available without active Moments and permits removal of
unavailable entries without explaining private reasons. No count, public membership list,
partner notification, reciprocal relationship or additional media access is introduced.
No-Faves, no-active-content, all-seen, offline/error, loading and exhausted states are distinct.

`FaveStore` is the one application-lifetime source for Explore and Couple Profile. Only
server-confirmed state displays as Faved; an in-flight operation says Saving, and uncertainty
shows Retry. Writes are serialized, opposite desires are coalesced, and every write uses
the account revision in `If-Match`, then confirms through GET. Backend conditional no-ops
advance the revision too, preventing an old delayed opposite operation from committing.
There is no unsafe offline queue. Session changes cancel writers and clear private state.

Lists have independent scroll state. A mutation invalidates continuation cursors without
reordering visible Global cards; the next Faves request uses current membership. A newer
mutation cancels an obsolete in-flight Faves/library load. On returning from a viewer or
background, protected pixels/actions/semantics stay hidden until a bounded visible-ID
revalidation completes; keyed geometry stays in layout to preserve reading position.
Every 30 seconds the visible feed is reauthorized, and server-time/monotonic deadlines
remove expired previews each second. Existing detail/photo endpoints remain authoritative.
These controls cannot recall bytes already delivered to a device.

Follow-up review: switching back to a loaded Explore tab reauthorizes its retained IDs;
an empty feed performs a fresh request on return and periodic recheck. If revalidation
removes every card, a fresh request obtains the correct empty reason or new supply.
Manage Faves refreshes on reopening and Retry, and restarts pagination if the server
revision changed between pages. Cancelled requests cannot overwrite newer scope results.
Saving a different city/country/interest set causes a new Global ranking on return;
the backend also rejects continuation of a Global session with changed preferences.

The existing rules ranker now retrieves/composes selected-city, country and broader tiers
before its 500-couple cap. Explicit broader discovery changes scope. Faves has no geography
filter or Global cap. Available signals remain interest overlap, coarse locality, own Fave
membership and freshness; the weights are configurable hypotheses, not proven optimal.
See the updated [backend recommendation contract](../../../SriSu-backend/docs/couple_recommendations.md).

Design: existing system `0:1`, Home `1:3`, bottom pill `34:1008`, Figma file
`LztysD1YvINX7RwZpnhhTt`. Live Home metadata was accessible but screenshot/design-context
calls hit the connector's Starter-plan quota. No Explore frame was found in that metadata;
this is an intentional extension of existing tokens/components, not claimed exact frame
fidelity. The earlier recommendation PDF was unavailable in the workspace.

Migration `social.0021_couplefaveversion` is additive and preserves Faves. Deploy backend
migration/endpoints before the new client; existing unconditional PUT/DELETE contracts
remain compatible. No dependency, production configuration or Figma changes.
See [render and validation evidence](../validation/explore/README.md).


## Home integration — 9 November 2026

Home’s two avatar entries open authorized existing Moment viewers, and the own-avatar plus opens creation. Private expiring status bubbles are separate from public profile mood. See [Home couple space](home-couple-space.md).


## Compact Moment composer — 19 November 2026

Frontend branch `codex/moment-upload-polish`, based on `dev-core-architecture`
`bcb11630782ed978bd377da5697f6dfd069af8ac`, in the original project folder.
Backend inspected at `6e0935c1c4f24267b9a956d259db5d113e9bb903`; no backend,
API, migration, deployment, or upload-state changes. Existing local Home documents
are preserved. No publication is part of this UI task.

The user's two Android screenshots are examples of the same oversized composer.
This is an intentional redesign: neutral safe-area shell, bounded photo preview,
compact caption and audience rows, and a persistent publication action. Existing
Satoshi typography, theme colors, shapes and spacing are reused. Live Figma access
for Home/Moments page `1:3` in `LztysD1YvINX7RwZpnhhTt` was attempted but blocked
by the Starter-plan tool limit; this is not a claim of a new live Figma match.

- The photo uses `Crop` to fill its rounded preview edge to edge without solid
  side/top bars. This is a display crop only; the viewer still shows the complete
  photo, and uploaded bytes are not resized or recompressed. Add, camera, selection,
  removal and ordering retain their existing callbacks and five-photo limit.
- Portrait uses a single scrollable body, a 16 dp gutter, 14/12 sp audience text
  from theme roles, and accessible controls. Share has a 48 dp minimum height and
  remains outside that scroll area, above safe drawing and keyboard insets.
- At widths of at least 600 dp when wider than tall, media and form sit side by
  side. Content width is capped at 960 dp. Small screens and larger text scroll
  instead of shrinking type or clipping controls.
- The pinned action says “Share with partner” or “Share publicly”; privacy remains
  explicit even when audience options are below the fold. Private is still the
  default. The public-discovery explanation and 24-hour expiry remain visible in
  the form/footer. A pending publication still exposes “Check publication”.
- Caption editing uses the existing IME-aware draft editor, preserving selection
  and composition while the model saves text. Done dismisses editing. When less
  than 300 dp of vertical space is available, editing gets its own focused area
  with a visible Done action. It stays there until Done/Back, so dismissing the
  keyboard does not remount the field and reopen it. The form's scroll position
  and draft survive this transition; sharing returns after editing.
- Empty creation opens the complete composer with a tappable image-plus placeholder
  in the preview area and a camera action below it. Caption and audience are
  available before selection; Share is disabled until at least one photo exists.
  Cancelling selection or removing the last photo keeps this screen and its draft.
  The picker opens only after an explicit photo action. Loading, upload progress,
  publication freezing, errors, retry, discard and leaving-with-a-draft still use
  the existing state and guards. Moment viewer and approved Chat UI are unchanged.

Verification and screenshots are generated by `MomentRenderTest`,
`MomentIosRenderTest`, `MomentStateTest`, and `MomentComposerDeviceTest`.
Render fixtures are synthetic and test-only. Phone cases include 390×844,
320×640, 320×480, 640×320 landscape, dark mode, 160–200% text, public/private,
empty capture, progress/failure, and a short 640×240 caption-editing viewport.
Images are written under `composeApp/build/reports/moments/` (iOS test images are
also emitted with `MOMENT_RENDER` paths). They are not production assets.

Scope of verification: Android native keyboard/touch/system-bar checks use the
isolated Pixel 4a API 35 emulator and synthetic state, not a signed-in account.
iOS tests run the native Compose simulator runtime and editor actions; they do
not exercise the system keyboard or camera/library picker. Live backend upload,
permissions and physical iPhone behavior were not re-tested in this UI-only task.

Final checks passed: theme audit; 44 Android Moment render/state tests; 35 iOS
simulator Moment render/state tests; Android debug/test APK assembly and iOS
simulator compilation. The two Android native keyboard/touch tests passed in
both portrait and landscape (four executions), including 200% text, rapid
multiline input, keyboard dismissal, privacy selection and the Share callback.
An initial landscape/large-text keyboard failure was corrected with the focused
caption layout described above and reverified on both orientations.

Commands used (JDK: Android Studio bundled JBR):

```sh
python3 tools/theme/check_theme.py
./gradlew :composeApp:testDebugUnitTest --tests '*MomentRenderTest*' --tests '*MomentStateTest*'
./gradlew :composeApp:iosSimulatorArm64Test --tests '*MomentIosRenderTest*' --tests '*MomentStateTest*' -I /tmp/srisu-navigation-ios-tests.gradle
./gradlew :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest
```

The temporary iOS init script selects the existing “SriSu Couple Chat Evaluation”
iPhone 17 / iOS 26.5 simulator (`0055683D-7DBA-44C7-855D-4AA64123C522`). Android
instrumentation class: `com.srisu.srisu.features.moments.MomentComposerDeviceTest`;
run using `connectedDebugAndroidTest` with that class filter, or install the debug
and test APKs on an isolated emulator and invoke AndroidJUnitRunner directly.
No personal photo or account was used. Test emulators/simulator were stopped
again after verification; the connected physical Android phone was untouched.


Follow-up — composer opens before photo selection: the user-requested image-plus
placeholder replaces the separate chooser stage. The existing native library and
camera callbacks remain explicit actions; no picker opens on entering the route.
Android render/flow/state checks: 48 passed. iOS simulator render/flow/state checks:
39 passed. Theme audit and diff whitespace check passed. Standard, small/200%-text
and landscape empty-state previews were inspected. Tests cover adding a photo,
removing the last one, retaining caption/audience, cancelling selection, and
preventing Share without a photo. Native picker/camera integration is unchanged
and was not rerun for this follow-up. No backend changes or publication.


Follow-up — filled photo preview: the selected image now fills its rounded frame
using a centered display crop, including restored draft media. This removes solid
letterbox bands while keeping the form on a readable theme surface. The complete
photo remains in the viewer and upload bytes are unchanged. No new controls or
backend behavior. Existing image-plus entry, keyboard handling and sharing guards
remain. Theme validation, 33 Android and 24 iOS simulator render/interaction checks
passed; these cover standard, small/large-text, dark, and landscape layouts.
Native picker/keyboard and live upload were not rerun for this display-only change.

### Per-photo framing — 19 November 2026

The user's composer screenshot and request to recover clipped faces define this
iteration. This is a derived interaction, not a new Figma crop-editor specimen;
Moments remains associated with Figma page `1:3` in file
`LztysD1YvINX7RwZpnhhTt`. No new live layer fidelity is claimed.

- Full is the default and shows all source pixels. Fill selects a crop matching
  the current preview. Crop opens a dedicated editor with drag in both axes,
  focal pinch zoom, a zoom slider, Original/1:1/4:5/16:9 ratios, Reset, Cancel and
  Apply. The surrounding image is dimmed outside the crop so clipped subjects
  remain discoverable. Screen-reader custom actions can move the photo.
- Each crop uses normalized source coordinates keyed by the photo UUID. Applying
  it changes the actual published photo. Rotation/viewport changes fit that same
  crop without choosing another center; reordering cannot move it to another
  photo. Full/Reset restores the source until publication. Cancel leaves the
  previous crop intact. Crops survive secure draft recovery for uploaded photos;
  unuploaded photo bytes still follow the existing memory-only limitation.
- The publication intent freezes the crops along with caption, audience and
  order. Lost responses retry that same intent. Backend processing crops staged,
  orientation-corrected photos outside locks, then revalidates ownership,
  membership and revision in the publication transaction. It saves only the
  chosen pixels into published photo records; original staging is cleaned up.
  A failed or duplicate publication cleans up unused derivatives. Media keeps
  existing authorization, metadata stripping and 24-hour visibility.
- No endpoint, dependency or database migration was added. Deploy the additive
  backend `crops` support before this client; existing clients omitting `crops`
  and existing no-crop replay hashes retain their behavior. Do not roll the
  backend back while crop-enabled clients are publishing.

Sources: frontend `codex/moment-upload-polish` at base
`bcb11630782ed978bd377da5697f6dfd069af8ac`; backend
`codex/moment-upload-crop` at integration base `1594c3b`.
Both PRs target `dev-core-architecture` by explicit user decision. Backend PR:
https://github.com/SrizanKhadka/SriSu/pull/42 (head `a13e770`).
The complete core-1 snapshots and generated fixtures now match across these
branches; the earlier drift came from the older backend working branch.

Framing verification (local synthetic fixtures, final implementation):

- Theme checker and `git diff --check` pass. Core-1 fixture generation is
  consistent; backend schema validation and whole-bundle comparison pass.
- `:composeApp:testDebugUnitTest --tests '*Moment*Test*'`: 60 passing tests,
  including crop geometry, secure restore, reorder/remove, immutable retry intent,
  dark theme, 320dp/2× text, landscape, apply/cancel/reset and production renders.
- `:composeApp:iosSimulatorArm64Test --tests '*Moment*Test*'`: 50 passing tests
  on iPhone 17 / iOS 26.5 simulator. These exercise native Compose rendering,
  crop gestures and the same state logic; they do not exercise the iOS system
  keyboard, camera, gallery picker or a live partner account.
- Android API 35 `SriSuNavigationQA`: all three
  `MomentComposerDeviceTest` tests pass in portrait and again in landscape.
  Real touch injection verifies a continuous drag reaches the source's top edge,
  pinch zoom changes the crop, Apply/Cancel/Reset, native keyboard typing/dismissal,
  scrolling at 2× text and actions above system bars. A per-frame stale-state bug
  found by the full-distance drag test was corrected by applying transforms to
  the latest state at pointer-event dispatch.
- Backend disposable PostgreSQL runner on the selected integration base: 367 tests pass, including crop pixel
  output and dimensions, partner/private access, invalid and foreign crop IDs,
  rollback cleanup, revision changes during processing, duplicate concurrent
  publication, and post-commit cleanup failure without deleting published media.
  No migration is introduced. No request was sent to a deployed partner account.

Reproduce mobile checks with the project's Android Studio JDK and selected iOS
simulator (`/tmp/srisu-navigation-ios-tests.gradle` only selects its device UUID):

```sh
./gradlew :composeApp:testDebugUnitTest --tests '*Moment*Test*' \
  :composeApp:iosSimulatorArm64Test --tests '*Moment*Test*' \
  :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest \
  -I /tmp/srisu-navigation-ios-tests.gradle
```

Backend: `.venv/bin/python tools/auth_postgres_tests.py --postgres-bin
/opt/homebrew/opt/postgresql@17/bin` (disposable database, not development data).
Production composable captures are generated locally under
`composeApp/build/reports/moments/`, including `android-device-crop-full.png`,
`android-device-crop-editor.png`, `android-device-crop-applied.png`,
`android-create-crop-small.png`, `android-create-crop-dark.png`,
`android-create-crop-landscape.png` and corresponding `ios-create-crop*.png`.
These are synthetic captures, not the user's photos. They were visually inspected.
Existing Home design documents remain byte-identical. Backend changes outside
Moments were preserved separately and excluded from this PR.

### Native iOS and live publication follow-up

The actual debug application was built and installed under an isolated verification
bundle ID on iPhone 17 / iOS 26.5, using the disposable loopback backend and
synthetic linked accounts. Native photo selection (two photos), empty-first entry,
Full/Fill, manual drag to recover the top of a portrait, Apply, software-keyboard
caption editing/Done, scrolling and landscape layout were exercised. Relaunch
correctly preserved caption and requested re-selection of not-yet-uploaded photos;
this is the existing memory-only photo limitation, not durable offline media.

Two issues discovered in this pass were fixed: reveal the full caption and counter
when keyboard insets resize the scroll viewport; fit the final photo in the viewer
so it does not discard the user's crop by center-cropping again.

The iOS app published two private photos. An independently authenticated synthetic
partner downloaded the exact 800×605 chosen crop and 1200×800 full landscape;
pixel inspection confirmed the retained top of the portrait. An unrelated user
received 404 for the Moment and was denied both photos. The partner view receipt
was accepted. These were real app → HTTP → storage → partner HTTP requests,
not a deployed-server or real-account exercise.

`python3 tools/core_integration.py --backend ../SriSu-backend` now includes
`MomentCropTransportIntegrationTest`: two photos reordered at publication, exact
cropped pixels/dimensions, same-intent replay, changed-intent 409, private-media
denial and partner receipt. The paired suite passed. The full Android unit run
passed 587 tests (two opt-in live suites skipped), with Android/iOS compilation,
theme and contract checks passing. Backend PostgreSQL: 367 passed; no migration.

Native simulator captures: `/tmp/srisu-moment-live/ios-keyboard.png`,
`ios-landscape.png`, `ios-published-fit.png`; these use synthetic assets only.
The simulator's live Text Size setting did not visibly change Compose text in
this session; 320dp/2× text remains covered by the production-composable tests.
Physical iOS hardware, camera capture, deployed infrastructure and a measured
frame-time/performance benchmark were not exercised. Do not describe those as
verified.


## Notification rollout — 22 November 2026

See the [notification rollout](notifications.md#catalogue-rollout--22-november-2026) for current triggers, category controls, private tap destinations and mixed-client compatibility. Apple/APNs remains skipped.

## Video Moments — 24 November 2026

Implementation brief used for this task: extend the approved Moment composer and
viewer to support mixed photos and short videos. Audit the existing frontend,
backend and privacy contract first. Use Instagram's documented video-delivery
principles as a benchmark, then choose a proportionate architecture for SriSu.
Keep memory bounded, prepare files off the main thread, resume interrupted uploads,
process video asynchronously, use native adaptive playback, preserve existing photo
crop/order/caption/audience behavior, and test success, cancellation, retries,
invalid media, expired sessions and unauthorized access. Verify Android and iOS
separately; record measured results and limitations. Preserve local work and do
not push, create PRs, merge or deploy in this phase.

### Implemented behavior

- The composer still opens before the system picker. Its library-video action
  accepts one clip at a time, up to five mixed photos/videos, 60 seconds and
  100 MiB per source. Camera capture remains photo-only. Video previews preserve
  the full frame with play/pause, duration and sound controls; photo crops remain
  unchanged and are unavailable for videos.
- Android uses Media3 Transformer; iOS uses AVAssetExportSession. Preparation is
  file-backed, bounded to roughly 720p, and performed away from the UI thread.
  Unsupported encoder paths can fall back to a bounded original for server
  normalization. Private scratch files are excluded from iOS backup/protected;
  only owned paths can be read for upload or deleted. Stale files age out.
- Uploads use stable UUIDs and 2 MiB chunks. Confirmed server offsets allow retry
  after a lost response. Uploading subsequent clips overlaps queued encoding.
  Pause cancels client work without losing confirmed chunks; Share resumes.
  The app stores pending local-video paths, order, caption and publication intent.
  If the OS evicts a cached source, the user is asked to remove/reselect it.
  This is resumable foreground work, not a guaranteed background upload service.
- The backend publishes only ready media. The existing 24-hour clock starts at
  publication. Its H.264/AAC HLS ladder uses approximately 360p/720p long-edge
  bounds, skips duplicate higher renditions for small sources, and supplies a
  poster for older photo-only clients.
- Android uses ExoPlayer; iOS uses AVPlayer. Only the current item owns a player;
  buffers are bounded (Android target 8 MiB / 10 seconds, iOS preferred six seconds).
  These are player targets, not whole-process memory guarantees. No persistent
  private playback cache or global cookie storage is introduced.
- Playback starts muted. Video progress follows the media clock, buffering holds
  progression, completion advances only when another item exists, and reduced
  motion starts paused. Holding, app backgrounding, reply/options/editing states
  pause playback. Interruptions and removed headphones stop playback. The poster
  does not submit a video view receipt; actual first-frame presentation does.
- Short-lived asset cookies are supplied through supported native APIs. The
  backend rechecks current access on every segment. Retry refreshes expired
  credentials after a long pause. The existing Moment reply-to-Chat path stays
  intact; video assets are server-processed private media, not Matrix E2EE.

### Research and decisions

Meta describes adaptive bitrate delivery as Instagram's main video path and
progressive output as a compatibility option in its
[video-processing engineering article](https://engineering.fb.com/2022/11/04/video-engineering/instagram-video-processing-encoding-reduction/).
Its [Reels/AV1 article](https://engineering.fb.com/2023/02/21/video-engineering/av1-codec-facebook-instagram-reels/)
explains the quality/bandwidth/compute tradeoff. SriSu uses a small H.264 ladder
for current device compatibility instead of copying Meta's larger infrastructure.
These were documentation observations, not hands-on Instagram measurements.

Native choices follow [Android Transformer](https://developer.android.com/media/media3/transformer/transformations),
[Apple's HLS cookie API](https://developer.apple.com/documentation/avfoundation/avurlassethttpcookieskey),
and [AVPlayer's forward-buffer guidance](https://developer.apple.com/documentation/avfoundation/avplayeritem/preferredforwardbufferduration).
Native verification caught Apple's cookie `secure` behavior: supplying even
`"FALSE"` enables Secure, so development HTTP origins omit that property while
HTTPS retains it. See [Apple's definition](https://developer.apple.com/documentation/foundation/httpcookiepropertykey/secure).

### Source and rollout record

Implementation began in the existing dirty checkouts listed below. For the PR,
only the video delta is assembled onto current `dev-core-architecture`, using
`codex/video-moments` in both repositories. The original checkouts and unrelated
local edits are retained; no worktree was created. Starting snapshots:

- Frontend `codex/notification-previews`, base `fc0e72c9eae725c2f4cea9035e4513f4a03803c3`.
- Backend `codex/couple-chat-rebuild`, base `6e0935c1c4f24267b9a956d259db5d113e9bb903`.
- Figma file `LztysD1YvINX7RwZpnhhTt`, Moments page `1:3`. Live metadata access
  returned a Starter-plan quota limit. Controls derive from the approved existing
  composer/viewer; no claim of a newly inspected video-specific Figma design.
- Backend API, worker, storage, PR migration **0024** (after current remote **0023**)
  and deployment order: `../SriSu-backend/docs/couple_moments.md`,
  “Video Moments — 24 November 2026”. No live migrations/deployment were run.

The initial cross-repository verification stopped on pre-existing `core-1`
contract drift (`schema.json`). That existing work was not regenerated or reset.
Video transport is additionally covered by real client/server tests; a successful
subset does not mean the combined workspace gate or remote CI passed.

### Verification actually performed

- Android full unit run: **669 tests, zero failures, five opt-in transport tests
  skipped** (664 passed). The separately run `MomentVideoTransportIntegrationTest`
  passed against a fresh disposable Django server with real Ktor binary bodies,
  FFmpeg processing, idempotent chunks/publication, poster reads and partner
  playback credentials. `tools/core_integration.py --moment-video --test
  com.srisu.srisu.features.moments.MomentVideoTransportIntegrationTest` reproduces
  this without production accounts. Do not reuse its short-lived fixture token
  across long-running checks; an expired fixture caused an earlier wide test run
  to fail authentication, and fresh isolated runs passed.
- Backend: **330 tests passed on disposable PostgreSQL**, including concurrent
  duplicate chunk upload, losing-file cleanup, real two-rendition FFmpeg output,
  owner/unrelated-user boundaries, revoked device sessions, ended memberships,
  blocking, range/HEAD responses, expiry and deletion. Django configuration,
  migration consistency and backend core fixture validation passed. The separate
  earlier SQLite run completed 329 tests with 26 skips before the PostgreSQL-specific
  video concurrency test was added.
- Native Android 15/API 35 (`SriSuNavigationQA` emulator): system Photo Picker →
  private prepared file → bounded chunk read; native HLS first frame, actual
  colored pixels, pause/resume and end; composer at 320×640 logical pixels and
  1.5× text; caption keyboard/Done, scrolling and 640×320 landscape. Existing
  photo crop and keyboard instrumentation also passed. All assets were synthetic.
  The Activity fixture initializes only its test Koin Context, without changing
  real account storage.
- Native iOS 26.5 / iPhone 17 Pro simulator: full app Xcode build passed. A separate
  QA bundle compiled the production AVPlayer driver and passed authenticated HLS
  first frame, pause/resume and completion. Reproduce with
  `tools/verify_moment_video_ios.py --fixture <private-loopback-fixture> --device
  <booted-arm64-simulator>`. The helper removes its QA app/credentials afterward.
- Theme validation and both Kotlin platform targets passed. Existing unrelated
  dirty files, including the Xcode project changes, were checked against baseline
  SHA-256 hashes and remain unchanged. Existing changes in overlapping backend
  Moment files were retained.

Pixel inspection exposed an initially black, paused Android preview. The composer
now displays its poster and allocates a decoder only after Play. Embedded playback
uses a TextureView for Compose clipping; full-screen playback retains SurfaceView
with its Compose synchronization option. Native screenshot assertions check
presented colored pixels with a bounded wait for SurfaceFlinger, rather than
assuming a decoder callback proves visible content. This follows Android's
[surface interoperability guidance](https://developer.android.com/media/media3/ui/surface).
A separate source-size guard avoids uploading a larger transcode when the original
is already smaller; the server still normalizes the selected source.

Illustrative measurements, **not a performance SLA or cellular benchmark**:
8-second synthetic 360×640 H.264/AAC clip; 951,807-byte input; 885,225 bytes of HLS
plus poster/manifest output; one rendition (no duplicate upscale); encoding took
0.359 seconds on this development host. Warm loopback native checks observed
roughly 0.2–1.0 seconds to first frame on Android and 0.24–0.62 seconds on iOS.
These single-run timings include test setup variability and establish no p95,
low-end hardware, sustained frame-rate, battery or production-load claim.

Local, ignored evidence is in `build/reports/moments/video-*.json` and
`video-android-small.png`, `video-android-keyboard.png`, `video-android-landscape.png`.
Physical-device video codecs/HDR, real cellular interruptions, thermal behavior,
iOS Photo Picker/export through the complete app flow, and deployed worker/CDN
capacity remain unverified. Camera video recording, trimming and guaranteed
background uploads are outside this implementation. No remote CI run, push, PR,
merge or deployment was performed during initial implementation. The original
local backend still has unrelated contract drift; the PR uses current upstream
backend contracts without importing those edits.

### PR QA follow-up — 24 November 2026

- Target `dev-core-architecture` explicitly reconfirmed for both repositories.
  Frontend base `fc0e72c9eae725c2f4cea9035e4513f4a03803c3`; backend base
  `9e2f5b8` (current notification-compatible integration branch).
- Rebased video migration to `social.0024_moment_video`, depending on
  `0023_home_couple_space`; this prevents a divergent migration graph on merge.
  The original local checkout's `0022` was never applied to an application DB.
- Exact backend PR source: **459 PostgreSQL tests passed**, zero skips; Django
  configuration, migration consistency and authoritative contracts passed.
- Exact paired PR source: real Ktor chunk upload → FFmpeg processing → publish
  → partner playback credentials passed against the current backend integration
  source. Pinned frontend contracts and theme validation also passed.
- Android native video class rerun together: **3/3 passed** on API 35, including
  system selection/preparation, 320×640 composer at 1.5× text and keyboard, and
  authenticated HLS with actual pixels, pause/resume and completion.
- iOS production AVPlayer driver rerun: passed, 386 ms to first frame on the
  iPhone 17 Pro/iOS 26.5 simulator, pause/resume and eight-second completion.
  Full iOS picker interaction remains unverified: the available computer-use
  surface could not access Simulator. The temporary manual-picker harness was
  discarded; a timeout from that blocked attempt is not a product QA pass.
- Source exports used only for isolated PR validation, not new Git worktrees.
  Neither the existing Xcode project edits nor unrelated backend profile,
  interest, discovery, contract or media changes are included in these PRs.

GitHub PR descriptions record the final commits, paired integration result and
CI links. No merge or production deployment is part of this QA/PR request.

CI initially failed twice on Maven Central HTTP 429 responses: first Robolectric's
runtime JAR, then Gradle plugin transitive dependencies. CI now opts into the
[Google-hosted Maven Central mirror](https://docs.cloud.google.com/docs/buildpacks/java#google_cloud_hosted_maven_central_mirror)
for Gradle and [Robolectric runtime resolution](https://robolectric.org/configuring/).
The normal local repository selection, dependency versions and test coverage are
unchanged. Both failing artifact URLs returned 200 from the mirror before use.

### Android selection failure follow-up — 24 November 2026

A valid video could fail immediately after selection, with a misleading
60-second / 100-MiB warning. The Android scratch-file helper used global Koin,
while production uses `KoinIsolatedContext`. The resulting
`KoinApplication has not been started` exception was caught as a media-limit
failure. The old picker instrumentation fixture started global Koin itself and
therefore hid this mismatch.

- Scratch-file creation, upload reads and deletion now use the application
  context initialized by `MainApplication`; canonical owned-path checks remain.
  The picker test no longer creates an artificial global DI container.
- Selection distinguishes duration, size, unreadable media, resolution, access
  and storage/preparation failures. The shared composer also reports the actual
  failed limit. The limits remain 60 seconds and 104,857,600 bytes (100 MiB,
  approximately 105 decimal MB); 17 MB is well within the size limit.
- Missing provider SIZE metadata falls back to bounded file checks. Failure to
  generate a local poster no longer rejects otherwise valid media; the server
  still generates the published poster. Android metadata cleanup uses `release`
  for compatibility with the app's older supported Android versions.

Verification for these uncommitted frontend changes on `codex/video-moments`
(base `c06fc02`, paired unchanged backend `1ea9eacd`):

- New storage regression tests failed before the fix with the Koin exception;
  afterward, all **10** focused file/state tests passed, including 17 MB,
  exact-limit acceptance, distinct size/duration failures, retry and owned-path
  protection.
- **3/3 Android API 35 emulator tests passed**: native picker/preparation using
  an eight-second synthetic MP4 padded with a valid MP4 `free` atom to exactly
  17,000,000 bytes, composer/keyboard checks, and authenticated HLS playback.
  The picker successfully read prepared upload chunks without global Koin.
- The real Ktor → disposable Django → FFmpeg → publication → partner-playback
  integration passed. Android app/test APKs and iOS simulator Kotlin compiled.
- The user's original clip/physical phone and the native iOS picker were not
  tested in this follow-up. No backend source, migrations, limit changes,
  production database changes, push or merge are required/performed here.

### Server processing wait follow-up — 24 November 2026

“Preparing video 1 of 1” is reached after upload completion. The user's other
laptop had no running `moment_video_worker`; the backend's local launcher omitted
that service. Backend startup now includes media-tool preflight, the worker and
its health check. Encoder renditions/poster share one decode pass. See the paired
backend `docs/couple_moments.md` latency follow-up and
`docs/local-server-startup.md` recovery instructions.

The shared Android/iOS composer distinguishes “Uploaded · waiting to process”
from “Processing video”. A 30-second queued wait or a 90-second processing wait
returns control with an explicit saved-draft message; slow status requests are
included in the deadline. These waits do not cancel the server job, delete media,
or publish in the background. Share safely resumes the same draft and reuses
confirmed uploaded bytes. Success, pause, missing worker, stalled status request,
and retry without duplicate upload/publication are regression-tested.

Verification: 12 focused video file/state tests passed; Android/iOS Kotlin and
native artifacts built; real Ktor → Django → FFmpeg → publish → partner playback
passed; 3/3 Android API 35 native checks and the production iOS AVPlayer simulator
check passed against disposable media from the updated encoder. Backend checks
passed with 35 PostgreSQL-only tests skipped, and 20 local startup tests passed.
The actual other-laptop worker cannot be started or timed from this checkout.
No new migrations or API fields are introduced. These local changes build on
frontend `c06fc02` and backend `1ea9eacd`, both on `codex/video-moments`; nothing
was pushed or merged. Earlier approved UI and local Xcode/environment edits are
preserved.

Final full frontend unit run: 676 tests, zero failures/errors, five opt-in
transport checks skipped. The video transport check was run separately and
passed. Local evidence is retained under the ignored
`build/reports/moments/video-processing-follow-up/` directory.

### In-app video recording — 24 November 2026

The composer camera action now opens two explicit choices: **Take photo** and
**Record video · up to 60s**, both before and after media has been added. Library
selection is unchanged. A recording returns to the existing composer preview;
caption, audience, retry, five-media limit and explicit Share remain authoritative.
This extends the approved Moment composer using existing theme tokens; no new
Figma screen or backend contract is introduced.

- Shared `MomentVideoPicker.record` returns the same `MomentVideoSelection` as
  library selection, including the ViewModel's account/draft stamp checks.
- Android uses in-app CameraX, front/back cameras, audio and a visible timer.
  Both camera and microphone permission are required. Denial returns an
  actionable error and releases the operation so the user can try again.
  Capture is 720p (lower-quality fallback), targeting 3 Mbps. Native duration
  and size limits leave a little headroom under 60 seconds / 100 MiB for final
  frames and container metadata. Limit completion keeps a valid recorded clip.
- Android output stays in private cache, never the gallery. A validated capture
  is moved into the existing upload cache without another local encode pass.
  Cancel, leaving the app, disposal and recording errors remove partial files
  after the native writer closes. Orphan partial files from process death are
  pruned on a later recording. Controls use the existing safe-area/theme layout.
- iOS uses the in-app native camera with camera/microphone permissions, its
  Retake/Use Video review and a duration limit below 60 seconds. The picker
  requests a 720p export; the existing protected file preparation, size validation
  and encoder handle the returned movie. Camera unavailable, denial,
  interruption, backgrounding and cancellation do not publish a Moment.
  Permission descriptions now include video and recorded sound.
- Both platforms use the existing resumable upload, private/public audience,
  server validation, FFmpeg processing, playback and media-aware notifications.
  No backend changes or migrations are required for recording itself. Earlier
  local worker/notification fixes remain in this branch.

Native API references: [CameraX video capture](https://developer.android.com/media/camera/camerax/video-capture),
[CameraX output limits](https://developer.android.com/reference/androidx/camera/video/FileOutputOptions.Builder),
[Apple camera controller](https://developer.apple.com/documentation/uikit/uiimagepickercontroller),
[Apple export preset](https://developer.apple.com/documentation/uikit/uiimagepickercontroller/videoexportpreset).

QA adds emulator-only camera tests and a disposable iOS camera-availability
harness (`tools/verify_moment_video_ios.py --capture-only --device <simulator>`).
The paired HTTP runner accepts `--moment-video-file <synthetic-camera-file>` to
verify the actual captured format through the real client and disposable server.
Never use real account media or credentials for these checks.

Verification for this local recording addition (frontend `c06fc02`, backend
`1ea9eacd`, both `codex/video-moments`, plus the preserved local follow-ups):

- Android/iOS Kotlin compile, Android app/test builds and native Swift harness
  compile passed. Theme validation and whitespace checks passed.
- The Moments unit/render suite passed: **78 tests, zero failures, two opt-in
  transport tests skipped**. The new camera-choice renders also passed separately
  after their alignment polish, including 320dp width, 150% text and dark mode.
- Android API 35 emulator checks passed for recording with an audio track and
  camera switching; cancellation/cleanup/retry; automatic duration finalization;
  small-screen camera choices; permission denial/retry; and leaving the app
  during recording. These tests never use the connected physical phone.
- The actual synthetic CameraX recording (H.264 video plus the emulator's AMR
  audio) passed real Ktor upload, the disposable Django worker, publication,
  partner playback access and the correct “Video moment” inbox label. No real
  couple media or push delivery was used.
- Native iOS simulator camera-unavailable/retry checks passed on iPhone 17 Pro,
  iOS 26.5. Actual recording, permissions, sound and performance on a physical
  iPhone remain unverified; the simulator has no video capture source.
- Xcode project edits, local.properties and backend .env match their preserved
  baseline hashes. No push, PR update or merge was performed. Local evidence is
  in ignored `build/reports/moments/video-capture/`.

The final Android duration check also passed with screen timeout reduced to
15 seconds and the emulator's charging/stay-awake override disabled: the camera
kept the display awake and finalized the clip automatically below 60 seconds.
Both emulator settings were restored after the test.

### Publication review — 24 November 2026

Combined the local follow-ups with backend `041fe7c` (the other laptop's worker
startup fix), preserving its migration guards and environment symlink handling.
A failed local preview now recreates its native player on Retry; a real Android
emulator regression replaces an unavailable file at the same path and verifies
that the recovered player renders frames. The first-party video route catalogue
is pinned from the backend; Ktor checks cover upload/status/retry/playback calls,
while Media3/AVPlayer streaming GET/HEAD remain native-player operations.

The final full frontend suite passed 680 tests with five opt-in transport skips;
Android/iOS compilation and APK builds passed. The paired video transport check
is run separately. Backend review/verification and initial `social.0024` rollout
requirements are documented in the companion PR. Physical iPhone recording
remains unverified. Publication uses frontend PR #36 and backend PR #45, both
into `dev-core-architecture`, with unrelated Xcode/environment/media work excluded.
