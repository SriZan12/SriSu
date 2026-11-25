# Onboarding, interests, navigation controls and Home loading

Implementation and local verification, 2026-11-22. Publication branches are listed below; no merge, deployment or migration.

## Source and scope

- Frontend: existing primary checkout, `codex/notification-rollout`, starting at `5f22621b5ef23cce158e2d1b676e203818eff641`.
- Backend: existing sibling checkout, `codex/couple-chat-rebuild`, starting at `6e0935c1c4f24267b9a956d259db5d113e9bb903`.
- These are the current feature branches, not the integration branches in `docs/integration/project-map.json`. Neither checkout was switched or reset.
- Figma: `LztysD1YvINX7RwZpnhhTt`, auth page `1:2`, Home page `1:3`, design system `0:1`. The live read hit the Figma MCP quota. Existing approved implementation and documented tokens were used; this is not a newly verified pixel match to live Figma.
- Preserved the pre-existing Xcode project changes, Home design documents and unrelated backend work. Before-work patches/hashes were saved outside both repositories.

## PR isolation

The paired repair branches are `codex/onboarding-interests-home-fixes` (frontend) and `codex/profile-interest-fixes` (backend), both targeting the fetched `dev-core-architecture`. Only this task's patch is applied to those bases. The working checkouts remain on the feature branches above with their local changes intact.

Unpublished notification features are excluded from the frontend PR. Their local Back-control updates and notification-key test adaptations remain in the working folder for that feature's later publication. The counts below describe local verification with that existing work present; hosted CI verifies the isolated PR trees independently.

## Photo flow

Choose photo → platform picker → bounded native decode/orientation normalization → prepared JPEG preview → multipart upload → confirmed startup progress. Cancel leaves the current selection unchanged. Invalid files and failed uploads retain the previous prepared photo and offer retry; removal restores the explicit Skip action. Duplicate submit taps are blocked by the existing operation gate.

Previously camera originals were rejected before downsampling, iOS header checks rejected HEIC, and upload reread the temporary picker URI. The client now retains the prepared bytes, normalizes to at most 1600 pixels on the longest edge and sends `image/jpeg`. Input reads are bounded to 40 MiB; output remains subject to the existing 5 MiB server contract. Native decoding removes orientation problems without expanding the backend's allowed formats. Android also bounds decoded source dimensions. Server-side image validation, ownership and onboarding completion remain authoritative.

iOS picker copies have unique paths and checked copy results. Concurrent provider callbacks retain selection order and deliver one completion on the main queue. Picker/read failures are distinct from cancellation. Camera access uses the existing permission-aware camera manager.

The photo step has a compact circular preview and explicit Choose/Change, optional Take photo and Remove actions. Its existing onboarding container keeps Back, progress and completion. Replacing the container's full-screen semantic click target with a focus-dismiss gesture restores the scroll region's correct accessibility bounds.

## Interests

The picker provides search, readable category headings, wrapping chips, visible selection count and Done. Loading, empty search, empty catalogue, offline cached selections and retry are explicit. Back cancels the local draft. Existing selections survive refresh; duplicate display labels share one chip while retaining legacy identifiers. Name-only legacy interests are preserved in saves.

`GET /api/auth/interests/` retains its existing response shape and IDs. Presentation trims whitespace, sorts category/name consistently, supplies category fallback labels and omits unnamed rows without deleting database data. Profile writes use validated catalogue IDs and canonical server labels rather than client display names. All entries validate before any write, within the existing actor-locked profile transaction. Foreign selection IDs are rejected. Duplicate/replayed saves are idempotent; removing and reselecting work. Existing duplicate user rows are marked removed, not deleted. Legacy name-only writes remain supported.

No schema migration or new endpoint is needed. Backend-first rollout is recommended for the stricter validation and stable-ID behavior; old clients retain their existing payload shape. Catalogue cache refresh follows the existing invalidation/TTL policy.

## Back controls and Home

Screen-level back actions share the approved Pure chevron, a 48 dp touch target, mirroring and the accessible label “Go back”. Existing callbacks, system-back behavior, editor discard prompts and scroll restoration remain intact. Modal Close controls and previous-photo arrows retain their distinct meanings.

Home first load now mirrors its actual hierarchy: heading, partner portraits/statuses, question, partner update/actions, plan, challenge and story counters. Section loading uses the matching section shape. Confirmed content remains visible during refresh; empty, unlinked and error states remain distinct from loading. One draw-time shimmer runs only while needed and foregrounded, with a static skeleton for reduced motion. Decorative placeholders are hidden from accessibility; the initial loading header announces one state.

## Verification

Final checks actually run:

| Check | Result |
| --- | --- |
| Android/JVM unit, behavior and render suite | 628 tests, 0 failures, 4 conditional integration skips |
| Android and iOS simulator production compilation | Passed |
| Selected iOS simulator tests | 13 passed: responsive UI/back actions, HEIC, EXIF orientation, native downsampling, skeleton transition and reduced motion |
| Android emulator instrumentation | 2 passed on SriSuHomeQA, Android 15: native picker open/cancel and keyboard-safe interest selection/save/back |
| Disposable client ↔ Django HTTP | Prepared photo upload/reload and interest save/replay/removal/reload passed |
| Backend maintained suite | 318 tests, 0 failures, 25 environment-dependent skips |
| Backend system/migration checks | No issues; no model changes detected |
| Theme and diff checks | Passed |

The iOS test runtime used the booted iPhone 17 Pro / iOS 26.5 simulator with synthetic 390×844, 320×640 and 640×320 viewports, including 180% text and dark mode. Android render coverage also includes compact/large-text and landscape layouts. The back-control regression tests still exercise discard decisions and return-position restoration; selectors were updated to the consistent accessible label. Two pre-existing iOS live-test calls were updated for the current notification-key argument so the test target compiles; those live Matrix suites were not run.

Screenshots are in [the validation directory](../validation/onboarding-interests-loading/README.md). Repeatable coverage lives in `OnboardingPhotoTest`, `InterestChoicesTest`, `ProfileRepairUiTest`, `ProfilePhotoNativeTest`, `ProfileRepairDeviceTest`, `ProfileRepairIosTest`, `ProfilePhotoIosTest`, `ProfileRepairTransportTest`, existing navigation/Home render tests and backend `InterestRepairTests`.

Device coverage distinguishes installed Android composables with a native picker/IME from iOS simulator Compose interaction/render tests and native ImageIO tests. HTTP verification uses synthetic accounts, a disposable Django database/media directory and the real Kotlin services. It does not publish real-user data or verify production networking. Real-device iOS picker interaction, iCloud-only assets, real camera hardware, VoiceOver/TalkBack interaction and low-memory devices remain manual follow-ups.
