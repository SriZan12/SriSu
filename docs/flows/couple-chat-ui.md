# Message-triggered romantic effects — 2026-10-12

Implemented on `codex/consolidate-core-20261010`, frontend baseline
`0900d3083923c088fc5feaa54e82db00b6637542`. Backend remains unchanged at
`928318f00615cc4db15d68edce6ebde550cee9fb` on `codex/couple-chat-rebuild`.
Live integration refs still match the earlier record. Figma thread `184:274`
context and screenshot requests were quota-blocked again. This requested motion
is an intentional extension of SriSu's existing Moments pink palette, not a claim
that Figma specifies these effects.

## Trigger vocabulary and visuals

`presentation/RomanticPhrases.kt` is the categorized, extensible source list. It
contains phrases, standalone keywords, emoji inputs and casual-spelling aliases;
the matcher has no Compose dependencies. Representative mappings:

| Category | Examples | Effect |
| --- | --- | --- |
| Sweet | miss you, thinking of you, wish you were here, sweet dreams, imy, xoxo | Eight translucent pink bubbles, some with tiny custom hearts; 3 seconds. |
| Affectionate | love you, my love, my heart, be mine, adore you, luv u, ily, ❤️ / 😍 / 😘 | Twenty-two small drifting hearts/bubbles emitted as a stream; 3.4 seconds. |
| Deep | I love you so much, be mine forever, marry me, always and forever, ilysm | Forty-four varied hearts/bubbles rising as a wider burst; 3.9 seconds. |

Matching ignores case, punctuation, apostrophe style and emoji variation selectors.
Common stretched spellings normalize (`loooove yooou`, `missss u`, `sooo much`).
The strongest matching category wins. Three romantic emoji or a flaming-heart
input select Deep. Broad keywords such as `forever` match only as standalone text,
not within unrelated sentences. Whole-word matching avoids `love your photo`;
URLs and simple directly negated phrases such as `I don't love you` stay quiet.
This is a bounded English phrase matcher, not general sentiment analysis.

The renderer draws original tapered heart paths with a curved highlight and thin
bubble rings/crescents. Emoji are input triggers only: no emoji glyph, stock heart
icon, font, bitmap, or third-party animation asset is rendered. All colors live in
`theme/RomanticColors.kt`, derived from Moments and the existing soft red palette.
Sizes, speeds, tilt and horizontal sway vary per message. The rise uses smooth
easing, a gentle entrance and fading during the final portion near the top.

## Ownership and behavior

- Repository emits an ephemeral message-arrival signal **after durable server
  confirmation or incoming decryption/apply**. Queued/sending/failed optimistic
  messages do not celebrate success. A successful retry can trigger once.
- Initial synchronization freezes the server high watermark; catch-up and paging
  below that watermark are excluded. Repeated acknowledgements/echoes are excluded
  by the stored SENT state. Reactions, receipts, deleted/hidden/unavailable records,
  quoted previews and feature-card text cannot trigger. Media captions can.
- ViewModel performs local matching and emits only intensity plus account/room/message
  identifiers to the renderer. No new API, persistence, analytics, logging, or server
  message fields. Private message text never enters an animation asset or log.
- Signals have no replay. Account/room checks and the resumed screen lifecycle stop
  effects on navigation/backgrounding; covered sheets and recording suppress them.
  One effect at a time: a new arrival replaces the current one, without a growing
  queue. Existing draft, scroll, playback and navigation state are unaffected.
- Canvas has no input handlers or accessible/focusable nodes. Touches pass through;
  it sits within safe areas and above the keyboard. Android duration scale / iOS
  Reduce Motion use Compose's existing MotionDurationScale support. A zero scale
  skips decorative motion entirely. Frame progress is read only during Canvas
  drawing, so it does not recompose the message list.

## Verification

- **Passed:** 87 focused chat/theme/navigation tests, zero failures/errors/skips;
  Android and iOS simulator Kotlin compilation; Android Debug APK assembly; theme
  token/contrast audit and `git diff --check`.
- New coverage: every listed phrase/category, aliases and emoji, strongest-match
  precedence, substring/negation exclusions, confirmation/retry/duplicate guards,
  initial-history eligibility, quotes/captions, non-blocking typing and physical
  taps through a live overlay, rendered particles and cleanup, hidden/foreign-room
  event dropping, normal completion, cancellation and reduced motion.
- All three styles were visually reviewed over the same synthetic chat at eight
  timeline positions. Custom art is distinct at each intensity and disappears at
  the end. Captures: `composeApp/build/reports/chat-romance/`, including
  `sweet-3.png`, `affectionate-3.png`, `deep-3.png`. Fixtures stay in tests and the
  existing opt-in evaluation source set.
- **Passed:** native animation XCTest on the dedicated SriSu iOS 26.5 simulator;
  this run finished with a clean `xcodebuild` exit. It inserts synthetic incoming
  and outgoing messages through the shared matcher/event/overlay components,
  renders all three effects and checks that no controller is presented. It does
  not substitute for a live encrypted transport test.
  Video: `composeApp/build/reports/chat-romance-ios/romantic-effects.mp4`;
  corresponding `ios-romance-sweet/affectionate/deep.png` screenshots were inspected.
- The follow-up render checks also cover dark appearance (`chat-romance/deep-dark.png`).
  Android device runtime and full two-account backend integration were not run;
  the existing Matrix/backend-versus-couple-chat-1/client mismatch is unchanged.
  Reduced motion was exercised with Compose's duration-scale override, not a
  physical iOS Settings walkthrough. No Figma pixel-match claim.
- The theme audit also caught an existing raw 8 dp corner in the edited message
  action card; it now references the identical `MaterialTheme.shapes.small` token.

Key files: `RomanticPhrases.kt`, `RomanticMessageOverlay.kt`, `RomanticColors.kt`,
`ConversationMessageArrival.kt`, `ConversationRepository.kt`,
`ConversationViewModel.kt`, `CoupleChatScreen.kt`.

---

# Photo bubble refinement, receipt ticks and new default — 2026-10-12

This focused follow-up supersedes the three-preset/default/receipt styling described
in the earlier entry below. Same feature branch and source commits; no backend,
dependency, media navigation or playback changes in this pass. Reused Figma thread
`184:274` context and the existing SriSu palette rather than repeating research.

## Findings and corrections

| Location / evidence | Problem and impact | Correction / priority | Verification |
| --- | --- | --- | --- |
| Photo/video in `chat-theme-after/redesign-portrait-video.png`; `MessageBubble` padding plus `MediaPreview` clipping | Two rounded frames and an inset make each photo feel like a card inside a card. This is a composition preference, not a functional defect. | Edge-to-edge image inside the existing grouped outer bubble; one set of corners, caption aligned to the text inset, tighter caption-to-receipt gap. Major visual refinement. | Same portrait/video and landscape/reply fixtures, light/dark and 320 dp / 1.8x text: resolved and visually verified in Android renders. |
| Same media screenshot; lower-left download chip | The large dark badge covers photo content and competes with the video play affordance. | Move the real download-needed indicator into the footer; retain centered video Play and a small badge for real duration only. Major visual refinement. | Captioned, captionless, unavailable, uploading and failed fixtures: resolved and visually verified in Android renders; upload percentage and retry remain real. |
| `MessageMetadata`; prior theme renders | Eye for read and circled check for sent break the single/double-tick vocabulary requested by the user. | Consistent Material Outlined single tick = sent, neutral double tick = delivered, accented double tick = read. Played retains its play-circle state. Minor visual refinement. | Rendered receipts in light/dark; spoken labels retained and queued/sending/failed still override optimistic receipt flags. Resolved and visually verified. |

## Appearance choice and ownership

Added **SriSu Iris** (Spark lavender → Ask mist blue with SriSu violet controls) and
**SriSu Dawn** (Reminder peach → Planner cream with warm accents). Their dark variants
use the same palette ramps against the existing dark surface. Gradients remain
confined to outgoing bubbles; incoming bubbles, header and composer stay calm.
Original, Blend and Calm remain available: five options total.

**Iris is the new default**, chosen after comparing the same content in both new
presets and light/dark appearances. It preserves SriSu's violet identity while
separating outgoing bubbles from incoming warm neutrals. The appearance sheet shows
Iris first and labels it Default; Apply/Cancel and personal account/room storage are
unchanged. A missing preference or missing legacy `chatTheme` field receives Iris.
Every explicit saved ID, including Original, is preserved. The protected serializer
already encodes defaults, so old saved Original preferences remain explicit.
Unknown IDs still render Original without overwriting the stored value.

The photo/video width remains 64% of available conversation width, capped at 260 dp.
The unchanged 0.85–1.8 preview aspect bounds keep height at most about 306 dp now
that the inset frame is removed. Images crop without stretching. Loading and
unavailable thumbnails reserve the same geometry; full-size files still open in
the existing dedicated destination. No production fixtures were added.

## Evidence

- Baseline retained in `composeApp/build/reports/chat-theme-after/`.
- New renders in `composeApp/build/reports/chat-photo-after/`: `media-iris-light.png`,
  `media-dawn-light.png`, `media-iris-dark.png`, `theme-iris-light.png`,
  `theme-iris-dark.png`, `theme-dawn-light.png`, `theme-dawn-dark.png`, and the
  `photo-large-upload/failed/no-caption.png` states. All five presets also rendered
  with the same conversation and constrained large-text composer.
- The focused correction pass tightened the caption/footer gap. The initial new
  layout assertion selected the merged accessibility group; correcting it to
  measure the footer's unmerged node confirmed that receipts are below the image.
- Passed: **77 focused chat/theme/navigation tests**, zero failures/errors/skips;
  Android and iOS simulator Kotlin compilation; Android Debug APK assembly; theme
  audit; `git diff --check`. Contrast tests cover 101 points across every outgoing
  blend in both appearances, plus incoming/background and primary controls.
- Regression coverage includes new-default migration, explicit-theme preservation,
  account/room isolation, photo footer bounds at large text, upload/failure retry,
  existing thumbnail/reading-anchor stability, drafts, replies and playback ownership.

- Passed: two native XTests on the dedicated SriSu iOS 26.5 simulator, including
  all five theme renders, photo/video destination and Back, actual video seek,
  inline voice ownership and scrolling, and Iris reply composer above the real
  keyboard. Captures: `chat-photo-ios/ios-media-scrolled.png`, `ios-theme-iris.png`,
  `ios-theme-dawn.png`, `ios-theme-selector.png`, `ios-keyboard-iris.png`.
  Both XCTest cases and the selected suite report zero failures. However,
  `xcodebuild` stalled after suite completion; its cancellation reported unfinished
  Xcode IDE operations. The owned runner was terminated, so this is **not a clean
  end-to-end xcodebuild exit**. UI captures and assertions completed before this.
  The second native fixture uses a budget-compliant synthetic thumbnail; the first
  fixture's larger image is correctly rejected by the existing thumbnail size cap
  and exercises the unavailable-preview layout. Full-size viewer data is separate.
- Not verified: Android device runtime (no connected device), physical-device or
  interactive back-gesture/screen-reader behavior, Figma pixel matching (screenshot
  quota unavailable), or live encrypted backend integration. The existing backend
  Matrix/client couple-chat-1 mismatch remains outside this UI task. All screenshots
  use synthetic test content; no production messages or accounts were changed.

Key implementation files: `theme/ChatTheme.kt`, `ConversationModels.kt`,
`ChatAppearanceSheet.kt`, `MessageBubble.kt`, `ChatMediaComponents.kt`.

---

# Chat palettes and dedicated media destinations — 2026-10-12

Implemented on the same current feature branch and source commits recorded below;
existing staged/unstaged work is preserved. Backend and dependencies are unchanged.
Live upstream refs were verified again and match the earlier record. Reused Figma
thread `184:274` context and theme tokens; no new screenshot access is claimed.

## Decisions and behavior

- **Original SriSu:** warm neutral surfaces, clearer soft-violet outgoing bubbles
  and violet controls. **SriSu Blend:** the sole gradient treatment is an outgoing
  violet-to-peach bubble (muted violet-to-warm-brown in dark appearance).
  **SriSu Calm:** quiet blue bubbles and accents from the existing Ask ramp.
  Header, background and composer stay flat. `theme/ChatTheme.kt` owns all blends,
  semantic bubble/text/metadata/receipt colors and the scoped Material scheme.
  Typography, shapes and spacing stay in the existing design system.
- Conversation options → **Chat appearance** presents three miniature conversations
  with replies and real vector status examples. Selection is a temporary preview;
  Apply saves, Cancel/swipe dismissal preserves the current preference. The sheet
  opens expanded and keeps Apply/Cancel outside its scrolling list.
- `ConversationPreferences.chatTheme` is a personal preference in the existing
  encrypted, account-separated `app/preferences/<roomId>` record. It survives
  reopening/offline use and is also scoped to that conversation. It is not sent
  to the partner or server. Old records default to Original; unknown IDs render
  Original without overwriting the stored value. The dedicated local setter
  updates only preferences, without network work, presence publication or a
  timeline rebuild. Light/dark appearance remains inherited from the app.
- One Material Outlined family now represents status: clock = queued, cloud upload
  = sending, check circle = sent, double check = delivered, eye = read, play circle
  = played, error circle = failed. Text stays on pending/failure states; complete
  spoken descriptions accompany all statuses. Optimistic states override receipt
  flags. Read/played have different shapes as well as an accent color.
- Photo/video bubbles use 64% of the available conversation width, capped at
  260 dp. Preview aspect is bounded to 0.85–1.8 with crop, never stretch; with the
  inner padding the tallest preview is under 300 dp. Geometry no longer depends
  on thumbnail bytes being present. The encrypted thumbnail budget is unchanged.
  Voice and text retain their separate sizing rules.
- `ChatNav.MediaViewer(roomId, messageId, attachmentId, draft)` is a typed route in
  BaseNavigation's existing chat graph. `ChatMediaViewModel` resolves the existing
  authorized protected attachment pipeline, bound to its opening account and room.
  Route arguments contain only stable IDs and draft provenance. Deleted, unavailable,
  malformed and failed content exposes an error/retry/back state. Photos fit the
  screen with bounded zoom/pan; captions remain separate from media.
- Video embeds the existing native player in the destination (`SurfaceView` on
  Android, child `AVPlayerViewController` on iOS). No Compose/native video dialog
  or native presentation remains. Shared controls expose actual play/pause, seek,
  duration, buffering and retry. Explicit export retains the existing consent flow.
- Chat explicitly hands off media work before navigation, stops voice and retains
  local history, reply, draft and saved reading position. Returning also handles a
  quick Back that retains the original composition during a transition. Viewer
  stop/disposal cancels loading and releases playback. iOS creates separate native
  owners and only deactivates the audio session when that owner held active media.
  Voice playback remains inline. Android 24–25 retains the supported legacy seek;
  26+ and iOS request precise positions.

## Render review and verification

Baseline: prior `chat-review-after/` and `chat-review-ios-after/` renders, including
`conversation-light.png` and `redesign-portrait-video.png`. Updated Android renders:
`composeApp/build/reports/chat-theme-after/`. Native captures:
`composeApp/build/reports/chat-theme-ios/`. All fixtures are synthetic and confined
to tests/the existing opt-in evaluation source set.

All three presets were inspected with identical text/reply/reaction/voice content
in both appearances. Media comparisons, portrait/landscape containers, errors,
selector previews, full-screen photos and the 320 dp / 1.8x / constrained-height
composer were also inspected. The focused correction pass made the voice icon's
foreground explicit, expanded the selector to keep its actions reachable, and
fixed native seeking after the first iOS runtime assertion caught keyframe snapping.
The final iOS capture shows the Blend reply composer above the actual keyboard.

- **Passed:** 74 focused chat/theme/navigation tests, zero failures/errors/skips.
  Includes account/room-isolated preference reopening, old-record defaults,
  selection/apply/cancel, optimistic receipt guards, draft/reply/focus retention,
  native-owner stale callback rejection, video buffering, stable thumbnail layout,
  route argument round-trip and original navigation-entry restoration.
- **Passed:** contrast samples at 101 positions across each outgoing gradient in
  light/dark, plus incoming/background and primary controls (at least 4.5:1 for
  text, metadata and receipt colors); existing theme audit; `git diff --check`.
- **Passed:** Android and iOS simulator Kotlin compilation, Android instrumentation
  compilation, Android Debug APK assembly and Android lint (zero errors).
- **Passed:** two native XTests on the dedicated SriSu iOS 26.5 simulator. These
  verify theme renders, photo/video routes without a presented controller, actual
  video position, pause and precise seek, voice-to-video handoff, cleanup on Back,
  original list index restoration, inline voice while scrolling, and reply keyboard.
  The first native run failed its seek assertion; the corrected rerun passed.
- **Not established:** physical-device/audio-route behavior, Android runtime
  (`adb devices` is empty), interactive/predictive back-gesture walkthroughs,
  screen-reader walkthroughs, live account/server replacement or encrypted media
  integration. Native navigation tests use test-only media resolution. The existing
  Matrix/backend-versus-couple-chat-1/frontend mismatch still blocks live integration;
  unchanged backend suites were not repeated. No Figma pixel-match claim.

Important files: `theme/ChatTheme.kt`, `ChatAppearanceSheet.kt`, `MessageBubble.kt`,
`ConversationChrome.kt`, `ChatMediaScreen.kt`, `ChatNav.kt`, `ConversationRepository.kt`,
`ChatMediaController.kt`, `NativeChatMedia.android.kt`, `NativeChatMedia.ios.kt`,
`iosApp/ChatMediaDriver.swift` and `iosApp/iOSApp.swift`.

---

# Empty chat / device replacement correction — 2026-10-12

The user's iOS screenshot shows `REPLACE_DEVICE`, not a ready empty conversation:
the header says chat is active on another device, the invitation says to start
chatting, the suggestion is disabled, and the composer and recovery action are
absent. `ConversationRepository.setup()` intentionally clears `error` when device
ownership differs; `ConversationNotice` previously returned early for a null error,
so the existing replacement confirmation could not be reached from this state.

An empty device-blocked conversation now explains the one-device limit, states
that previous messages will not transfer, and offers **Use this device**. This
opens the existing explicit confirmation; it does not authorize replacement
on the first tap. A conversation with saved history retains its messages and gets
the same action in a notice even without an API error. The misleading start-chat
invitation and disabled suggestion are not shown while replacement is required.
The composer still requires an available protected local room; empty ready/setup
conversations continue to allow a first draft and send. No transport, ownership,
key replacement, stored-draft or server behavior changed.

This is a targeted usability correction to the existing Figma-derived thread
(`184:274`), based on the user's actual screenshot; no new Figma claim or research.
Source commits, working branches and backend mismatch are unchanged from below.
Synthetic regression fixtures stay in tests and the opt-in evaluation harness.

Verification: **24 focused tests passed** (20 conversation UI / 4 presentation,
zero failures or skips), including the null-error device blocker, confirmation
and cancellation, saved draft, first-message composition, saved-history notice,
and reachable action at 320 dp / 1.8x text. Android and iOS simulator Kotlin
compilation passed, as did theme validation and `git diff --check`.
`testEmptyConversationStatesAreRendered` passed on the dedicated SriSu iOS 26.5
simulator. Both native screenshots were inspected: the blocked state has the
setup action; the ready empty state has its composer. Captures and logs are in
`composeApp/build/reports/chat-device-setup/`. Enlarged text plus an error requires
scrolling the central content; the action remains reachable without reducing text.
This verifies UI state rendering and action wiring, not live server replacement.
The user's real account/device registration was not changed; backend is untouched.

---

# Critical review follow-up — 2026-10-12

Same frontend/backend commits and live integration refs as the redesign record
below; pre-existing staged and unstaged work is retained. Backend remains untouched.
Baseline renders were rerun from the current checkout, then preserved under
`composeApp/build/reports/chat-review-before/`; existing native iOS captures are
under `chat-review-ios-before/`. These are synthetic test conversations.

Live Figma context for thread `184:274` was readable in this follow-up: Satoshi
16/12 header, 12 dp thread gaps, 4 dp group gaps, 240 dp photo group and secondary
12 dp timestamps. The separate screenshot request hit the Starter quota; direct
Figma visual comparison is unverified. This is a deliberate usability refinement,
not a pixel-identical port: keep genuine presence only, protected participant
images, 48 dp targets and existing feature cards. Earlier benchmark research is
reused without a new research round.

## Prioritized critique and implementation plan

1. **Header / composer — blocking accessibility issue.** In
   `small-large-text-reply.png` (320 dp, 1.8x text), the subtitle wraps, two initials
   crowd the avatar, and external actions leave a narrow three-line input.
   `ConversationHeader`, `ParticipantAvatar`, and `ConversationComposer` enforce
   those allocations. This consumes conversation space and makes drafting harder.
   Keep scalable type; ellipsize the secondary header to one line, fit initials,
   and use a full-width text row above the actions at large type. Verify control
   bounds, input width, draft retention and a constrained-height render.
2. **Replies — major clarity/interaction refinement.**
   `redesign-landscape-reply.png` shows a bright nested quote nearly as prominent
   as the reply. The composer uses the same two-line quote; source inspection
   also shows no focus request when selecting a reply. Use a quieter rail and
   caption-sized summary, one preview line in the composer, explicit “Replying
   to”, and focus on newly selected reply IDs. Verify cancellation, unchanged
   draft, original navigation, and actual iOS keyboard appearance.
3. **Receipts — major hierarchy refinement (design judgment).**
   `conversation-light.png` repeats full Sent/Delivered/Read labels, widening
   short bubbles; delivered/read use the same colour. `MessageMetadata` renders
   each label. Use compact success icons with distinct confirmed read/played
   colour and complete accessibility labels; retain pending/failure words.
   Reduce vertical body padding while retaining body type/line height. Verify
   that queued/failed states never inherit successful receipt semantics.
4. **Reactions — major target defect plus minor spacing refinement.**
   `redesign-landscape-reply.png` shows a long gap after the reaction. The chip is
   top-aligned inside a 48 dp-high box with no minimum width. Single reactions can
   have targets narrower than 48 dp. Center the compact visual inside a 48-by-48
   minimum target and remove redundant following group separation. Verify target
   dimensions, next-message separation, selected state, and reading position.
5. **Media — major proportion refinement (design judgment).**
   `redesign-portrait-video.png` devotes most of the visible thread to one portrait.
   Every attachment expands to the same maximum as text. Use Figma's 240 dp photo
   width cap with responsive bounds and stable aspect ratio; retain captions and
   receipts below images. Source inspection also finds “Preview unavailable” shown
   during async thumbnail decode. Show a labelled loading state until decoding
   completes, then the genuine fallback. Verify portrait/video comparison,
   missing/corrupt thumbnails, and unchanged row bounds as pixels arrive.
6. **Voice — major affordance refinement.**
   `voice-playback-unavailable.png` shows a seek thumb although seeking is disabled.
   `InlineVoicePlayer` always renders a Slider. Keep the real waveform/progress
   track; expose the thumb only with a playable native duration, with explicit
   loading/error feedback. Verify inactive/loading/error/playing states, seeking,
   single-player ownership and native playback while scrolling; no modal player.

## Follow-up verification

After images are preserved in `composeApp/build/reports/chat-review-after/`.
The same conversation, dimensions and font scales were inspected before/after;
additional constrained-height, thumbnail-transition and voice-loading fixtures
exercise the changed behavior. One correction pass simplified the empty state
while composing, removed redundant separation after reactions, and restored
contrast for the disabled-but-loading voice button.

| Finding | Result |
| --- | --- |
| 1. Header / composer | Resolved and visually verified in Android renders at 320 dp / 1.8x text, including 420 dp available height. Full-width input, one-line secondary header, single initial, all actions inside the viewport. |
| 2. Replies | Quote hierarchy resolved and visually verified. Focus/cancellation/draft retention pass Compose tests; native keyboard result recorded below. |
| 3. Receipts | Resolved and visually verified. Confirmed success uses icons and spoken status; pending/failure remains explicit. No body type or line-height reduction. |
| 4. Reactions | Resolved and visually verified. Minimum 48-by-48 dp targets tested; compact chip centered and following gap reduced. |
| 5. Media | Proportions/fallback resolved and visually verified. Decode completion preserves subsequent content bounds in a regression test; actual network download remains unverified. |
| 6. Voice | Inactive, error and loading affordances resolved and visually verified. Thumb dragging seeks without triggering reply; native playback result recorded below. |

Final focused frontend run: **51 tests, 0 failures/errors/skips**. Includes
playback ownership/lifecycle, reaction reconciliation, protected drafts, stable
pagination, input focus, touch targets and thumbnail layout stability.
Android Kotlin, iOS simulator Kotlin, APK assembly and Android instrumentation
test compilation passed. Theme checks passed (96 colours, 24 role pairs, minimum
4.75:1), as did `git diff --check`. Logs: `chat-review-verification/frontend.log`.
The unchanged backend suite was not repeated in this presentation-only follow-up.
The dedicated **SriSu Couple Chat Evaluation / iOS 26.5 simulator** passed both
selected XTests: native audio/image decoding and inline voice playback continuing
while scrolling, with no modal player. Selecting a reply presents the real iOS
keyboard; the final system screenshot verifies reply cancellation, multiline
input, attachment and send controls remain above it. Native captures are under
`chat-review-ios-after/`, including `ios-keyboard-reply.png`; log:
`chat-review-verification/ios.log`. Finding 2 is resolved and visually verified
for keyboard-open composition as well as the Android focus/draft checks.

The first shared-simulator attempt timed out while the device showed the app
switcher; it is not counted as a pass. The isolated run passed. The keyboard's
first-use introduction appeared in the initial isolated screenshot; a final
rerun after inspecting the system keyboard captured the actual keys and passed
both tests again. No production data or keyboard preferences were changed.

Android native runtime remains unavailable (`adb devices` returned no devices).
Existing backend transport mismatch still prevents live chat E2E. Physical audio
routes, native back gestures and screen-reader walkthroughs are not established
by these checks. Figma screenshot matching remains unverified.

---

# Conversation redesign — 2026-10-12

Implemented locally on `codex/consolidate-core-20261010`, starting at frontend
`0900d3083923c088fc5feaa54e82db00b6637542`. The inspected sibling backend is
`codex/couple-chat-rebuild` at `928318f00615cc4db15d68edce6ebde550cee9fb`.
Live integration refs were frontend `dev-new-theme` at
`3fa6822ea05578d64cca8891c5e3fdb0a831897a` and backend `dev` at
`ecc8592307114e7d154bda9612e6826e2ca8ba90`. Existing changes were preserved;
no branches were switched and no commits, pushes, migrations or deployments ran.

**Integration limitation:** the current sibling backend has retired the
`couple-chat-1` transport in favour of Matrix. This frontend still uses
`couple-chat-1`. This UI task preserves its encrypted transport, protected
storage, queue, authorization, and feature cards. The two selected checkouts
cannot establish a live chat integration result. No backend code changed.

## Direction and inspected references

Warm neutral incoming bubbles, quiet violet outgoing bubbles, Satoshi type,
participant identity, compact quotes and media, and inline voice playback.
The existing theme supplies colours, spacing and corners; responsive bubble
width (82%, capped at 360 dp), 28 dp group avatars and 48 dp controls are derived
presentation decisions. Incoming avatars appear at group starts; outgoing
identity is conveyed by alignment. Grouped sides use the 4/16 dp theme corners.

- [SriSu messaging board, 184:2](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-2):
  live structure identified the 393 dp thread `184:274`, 32 dp header avatar,
  composer, photo viewer and hold-to-react variants on Message page `1:5`.
  The next detailed context and screenshot requests hit the Starter quota.
  No Figma screenshot or high-fidelity child style inspection is claimed.
  The header avatar is deliberately enlarged to 40 dp for readability.
- [WhatsApp voice guidance](https://blog.whatsapp.com/making-voice-messages-better):
  playback continuity and recorded waveform/progress informed the inline player.
  Article read; its linked image timed out. No screenshot claim.
- [Viber chat help](https://help.viber.com/hc/en-us/articles/9423180947869-Chat-knowledge-base):
  quoted original navigation, contextual reactions and actual receipt states.
- [Public Chateo implementation screenshot](https://dev.to/swimmingkiim/i-made-a-free-ui-template-with-free-figma-design-feat-messenger-app-flutter-15ii):
  visually inspected in the browser; compact quotes and photo placement informed
  composition. No third-party image, icon or code asset was copied.

## Implemented behaviour

- `ConversationViewModel` loads the current relationship profile through the
  existing repository, matches the owner and partner IDs, and retrieves only
  the partner's guarded `photo_url`. Neither a cover nor the owner's photo is
  substituted. Photos are memory-only and cleared on leave/account/background
  state changes; initials/person icons cover absent, denied or failed photos.
- `MessageBubble` shares geometry across message types, removes nested borders,
  keeps metadata below content, supports link annotations, groups corners and
  avatars, anchors selected reaction chips, and highlights quoted originals.
  Deleted/unavailable originals still use the existing sanitized quote path.
  Reaction chips show confirmed state; queued/failed changes cannot manufacture
  a selection. There is no edited-message field in this contract.
- `ChatPlaybackOwner`, owned by the existing media controller, tracks a stable
  message AND attachment ID. Native progress is read only by the active bubble.
  A generation guard rejects replaced-player callbacks and stale UI playback
  requests. Native pause/seek/replay, errors, screen exit, backgrounding and
  account changes release the same player. Voice playback and download loading
  never open another surface, including draft previews. Explicit voice export
  remains in message options for the opened voice attachment, with confirmation.
- Photos/video share bounded aspect ratios, captions, metadata and retry states.
  Missing thumbnails use a compact fallback; no placeholder photo or duration
  is invented. New platform thumbnails adapt resolution/quality within the
  existing 6 KiB encrypted payload limit. List rendering decodes the thumbnail,
  not the full attachment. Upload percentages are real; download waiting is
  indeterminate because this transport does not report byte progress to the UI.
- The multiline composer retains protected drafts, recording gestures and
  explicit recording controls, provides recording feedback and a 4,000-character
  validation state, and retains keyboard/safe-area handling. Stable list keys,
  existing history loading, latest-message affordance and feature cards remain.

## Evidence and limits

Baseline and after images use the same synthetic Android conversation:
`composeApp/build/reports/chat-ui-before/` and `composeApp/build/reports/chat-ui/`.
Additional renders cover landscape/portrait media, video, reactions, replies,
queued/failed/deleted/unavailable messages, 320 dp width at 1.8x type and dark mode.
The deliberate visual correction pass tightened the audio seek thumb and
reaction placement. Synthetic images and audio exist only in tests/evaluation.

iPhone 17 Pro / iOS 26.5 simulator renders are under
`composeApp/build/reports/chat-ui-ios/`. The opt-in `ChatUiEvaluation` harness
uses the production Compose components, playback owner and native iOS driver.
Its XCTest verifies native progress while the voice row scrolls offscreen and
asserts that no player is presented modally. It also captures reply composition
and media with iOS safe areas. The repository's setup script restored the missing
test target referenced by the existing `SriSuChatTests` scheme.

Android native device/emulator playback, physical-device audio routes and
interruptions, real keyboard-open interaction, and live two-account chat remain
unverified. No Android device or emulator runtime was available. Figma screenshot
fidelity remains unverified. Backend offline checks are separate from chat E2E.

Checks actually run:

- Full frontend unit/render suite: 178 tests, 177 passed, 1 skipped, no failures.
  After the final lifecycle and quote-navigation changes, all 48 focused chat
  tests passed. This includes playback replacement/stale callbacks, account
  reset, duplicate/failing reactions, and list stability.
- `:composeApp:compileDebugKotlinAndroid`, `:composeApp:compileKotlinIosSimulatorArm64`,
  `:composeApp:assembleDebug` and `:composeApp:compileDebugAndroidTestKotlinAndroid`
  passed on the final implementation.
- Final `SriSuChatTests` run on iPhone 17 Pro / iOS 26.5: both selected XTests
  passed (native audio/image decoding and inline voice while scrolling; 0 failures).
  The final result is saved in `ios-final.log`.
- `python3 tools/theme/check_theme.py` passed: 96 documented colours retained,
  24 role pairs at or above 4.5:1 (minimum 4.75:1).
- `python3 tools/workspace.py verify backend` passed its isolated offline checks:
  231 tests, 11 skipped, no failures. This does not establish compatibility with
  this frontend's retired transport.
- Verification logs are saved locally under
  `composeApp/build/reports/chat-redesign-verification/` (ignored build output).

## Previous refinement record — 2026-10-10

Work is on the user-selected `codex/consolidate-core-20261010` checkout, based on
`d642a4408af6732a7ebd8f670b838ca223fb2dca`. Live frontend core ref was
`9f1a7708f1aa9785fb6e046227e134f23e1ec9f1`; `dev-new-theme` was
`3fa6822ea05578d64cca8891c5e3fdb0a831897a`. Backend core is
`2c4f312b3cb0327d7d298615028370514db2feb7` (`dev` live ref
`ecc8592307114e7d154bda9612e6826e2ca8ba90`). No branch was switched, merged or
reset for this task. The existing uncommitted backend compatibility/Redis repair
and user media were left intact. No new backend change or deployment order is
required; the `couple-chat-1` wire contract and encrypted storage format remain.

## Assessment and visual direction

The earlier conversation used a generic app bar without an avatar, an outlined
form field as composer, text-only media/actions, inconsistent reply styling,
and dense menus. Grouping depended on matching minute strings. The result felt
like separate controls rather than one conversation.

The refinement uses SriSu's existing Satoshi typography, warm neutral surfaces,
soft purple outgoing bubbles, semantic spacing, shapes, and activity colors.
The partner header, bubbles, reply strip, composer, cards, action sheets and
media controls now share those foundations. No decorative background or fake
profile photo was introduced. The room contract has no avatar URL; initials
are a deliberate fallback, with the existing private nickname respected.

### Design access limitation

Requested Figma source: [Message page 1:5](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5).
Known frame references remain thread `184:2`, voice `184:1121`, Spark `184:1634`,
Ask `185:2`, Planner `185:781`, options `185:1624`, and design system `0:1`.
Live page metadata, thread design context/screenshot, and design-system metadata
were rejected by the Starter-plan MCP quota. Account inspection confirmed a
Starter/View seat. The browser fallback failed during initialization.
**No live frame or surrounding page was visually reviewed in this task.**
The existing [design-system specification](../design-system/README.md) is the
available visual basis; exact Figma fidelity remains unverified. Known frame
IDs are references, not a claim to have inspected their contents.

Interaction benchmarks were read from primary sources:

- [WhatsApp voice-message design](https://blog.whatsapp.com/making-voice-messages-better):
  review before sending, real waveform data, and explicit playback controls.
- [Viber chat knowledge base](https://help.viber.com/hc/en-us/articles/9423180947869-Chat-knowledge-base):
  quoted replies, long-press actions, reactions, distinct delivery states and
  recording cancellation/preview. SriSu keeps its own visual system and scope.

## Implementation

- `ConversationChrome.kt`: partner header, actual connection/activity labels,
  multiline composer, shared quote previews, error notice, date/empty states.
  Preview sections scroll independently when the keyboard or large type reduces
  available height. The composer accounts for system navigation and IME insets.
- `CoupleChatScreen.kt`: state-only `ConversationSurface` and existing repository
  orchestration. Stable message IDs remain list keys. Receipt/reaction updates
  do not trigger tail scrolling. Viewport resizing follows the tail only while
  at latest. Read receipts still depend on visible message IDs.
- `MessageBubble.kt`: responsive width cap, sender/time grouping, theme-based
  bubble shapes, shared quotes for text/media, timestamp/status icons, reaction
  chips and accessible contextual actions. Long-press and swipe-to-reply remain;
  private copying uses the existing protected clipboard. Both deletion scopes
  now have an explicit confirmation (queued-send cancellation stays separate).
- `ChatMediaComponents.kt` / `ChatMediaController.kt`: real thumbnail previews,
  video affordances, native upload progress, attachment draft removal, proper
  microphone controls, and a compact voice playback sheet. Tap starts a locked
  recording; hold retains release-to-send, slide-to-cancel and slide-to-lock.
  Preview/discard/send use the existing native drivers and protected drafts.
  Waveforms contain recorded samples only; absent samples show an unavailable
  label and neutral track. Playback loading/errors are visible, and replay resets
  an ended player. Missing video-preview text sits below the image area so it
  cannot overlap the play button or duration badge. `played` is still acknowledged
  only by the native playback event.
- `ChatCardsUi.kt`: one card header/shell for Ask, Sparks, Plans, Moments and
  unavailable Challenge references. Existing source authorization, expiry,
  hidden-answer reveal, response queue and typed navigation remain intact.
- The repository/ViewModel expose the existing socket state as a read-only flow
  to distinguish connecting/reconnecting/paused updates. This introduces no
  networking, authentication, encryption, persistence or server behavior change.
- [Bundled Material vectors](../design-system/chat-icons.md) replace new reliance
  on Android-only extended icons. No new dependency, call control, push service,
  notification setting, or Challenge domain was added.

## Validation record

Synthetic render/interaction tests cover normal and wide/dark screens, 320 dp
with 180% text, multiline composition, reply cancellation, failed-send retry,
empty-state drafting, long-press/swipe, redacted deletion content, unavailable
waveforms and stable reading position on reaction updates and older-history
insertion. Presentation tests
check receipt precedence, actual-time grouping and privacy-aware activity labels.

Local commands use JDK 21 (`D:/Android Studio/jbr`), the existing Android SDK,
and ignored D:-based Gradle/test/temp directories. Gradle uses two workers,
`-Pkotlin.compiler.execution.strategy=in-process`, and the existing
`.cache/chat-test-init.gradle`. No dependency version was changed.

The initial test attempt failed because Robolectric could not write its default
home lock file; the existing test-cache configuration corrected that. A Kotlin
daemon sandbox access failure was avoided with authorized execution and in-process
compilation. An early lazy-list test used `performScrollTo` on an uncomposed item;
it now scrolls the list by index before checking the visible anchor.

Commands run from the frontend, with the cache settings above:

```powershell
& 'D:\Djangos\SriSu\.cache\couple-chat-venv\Scripts\python.exe' tools/theme/check_theme.py
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest --offline --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx3072m -XX:ActiveProcessorCount=4' -I D:/Djangos/SriSu/.cache/chat-test-init.gradle
git diff --check
```

- Theme check passed: 96 documented colors retained and 24 checked Material role
  pairs meet 4.5:1. A separate review found the small failed-send label used the
  error foreground on a secondary container (3.78:1 in light mode); it now uses
  the paired error-container roles. No global theme token was changed.
- Full Android unit suite passed: **168 passed, one skipped**. The skipped
  `CoreLocalTransportIntegrationTest` requires its own disposable-backend runner;
  it is not counted as passing. Debug app and instrumentation APKs built.
  Logs: ignored `build-chat-ui-validation.log` and `build-chat-ui-final.log`.
- The final focused rerun passed all **16 tests**, and rebuilt both APKs after
  the video-placeholder correction (`build-chat-ui-verified.log`). Command:
  the same Gradle invocation above, with `--tests '*ConversationRedesignTest'
  --tests '*ChatSurfaceTest' --tests '*ChatPresentationTest'` after the unit-test
  task. Render review confirmed that the corrected video controls no longer overlap.
- Whitespace check passed. Existing Kotlin expect/actual beta and Gradle
  deprecation warnings remain; these checks are not a release lint audit.

The synthetic `SriSuSignalSpike` Android API 35 emulator (`emulator-5580`) used
the built APKs, existing native libsignal/Keystore/protected SQLite, and a
disposable PostgreSQL database on port 55449. Commands run from the backend:

```powershell
docker compose -p srisu-couple-chat-test -f compose.couple-chat-test.yaml up -d --wait db
.\.cache\couple-chat-venv\Scripts\python.exe tools/chat_app_integration.py --adb 'C:\Users\acer\AppData\Local\Android\Sdk\platform-tools\adb.exe' --serial emulator-5580
.\.cache\couple-chat-venv\Scripts\python.exe tools/chat_app_integration.py --adb 'C:\Users\acer\AppData\Local\Android\Sdk\platform-tools\adb.exe' --serial emulator-5580 --ui
```

- Actual repository integration **passed**: encrypted text/replies/reactions,
  offline send and draft persistence across reopening, duplicate prevention,
  nickname privacy, disabled read receipts, Ask response, mutual Spark reveal,
  deletion and quote invalidation after history reconciliation. Evidence:
  backend `.cache/chat-app-integration/cc_app_d935680c33d846dd871ffaf5016db523`.
- Visible UI integration on the final APK **passed**, including an assertion that
  the real Android IME is visible and the send control remains displayed, secure
  window protection, sending/reply/reaction, private nickname, Ask voting,
  unavailable Challenge references, and deletion with removed quote text. Evidence:
  backend `.cache/chat-app-integration/cc_app_54c5bd4ff820422db4b6cdbedccdf290`.
  Its first run reached deletion after passing the preceding steps, then selected
  the quoted copy of the original message. The test now scrolls to the original
  and excludes quote previews when finding its action menu. No deletion or
  authorization condition was weakened. A subsequent real-keyboard assertion
  was blocked by a system-wide Android System UI ANR dialog, confirmed with
  `dumpsys window`. Restarting exposed a boot-time Keyguard service ANR; its
  system dialog was dismissed with Wait. Normal activity focus and IME visibility
  were confirmed before the successful final run. No application test assertion
  was removed to accommodate that environment failure.
- `adb -s emulator-5580 shell am instrument -w -r -e class
  com.srisu.srisu.features.couplechat.presentation.ChatMediaNativeTest
  com.srisu.srisu.test/androidx.test.runner.AndroidJUnitRunner` **passed all three**:
  native QR round-trip, photo orientation/GPS stripping, and decoding/remuxing
  existing synthetic MP4/WAV fixtures. Evidence: backend
  `.cache/chat-ui-native-media.log`. This is Android validation; using a
  previously generated iOS media fixture does not validate the new iOS UI.

The Mac was unreachable; the user explicitly chose to continue with Android
because the Mac is unavailable. iOS compilation, iOS UI/safe-area/keyboard
behavior, physical-device permissions/audio routes and VoiceOver/TalkBack traversal
are not verified by Android compilation or synthetic renders.

The disposable database runner removed its generated databases and credential
fixtures. The test PostgreSQL service and emulator were stopped afterward. The
normal Docker app/database/Redis services were left running, and no application
data or existing backend changes were modified. The user subsequently requested
publication of the frontend redesign on `codex/consolidate-core-20261010`.

## Render evidence

These are synthetic Android Robolectric renders of the shared production
components, not Figma screenshots or a real couple's conversation:

- [Light conversation](../validation/chat-ui/conversation-light.png)
- [Wide dark conversation](../validation/chat-ui/conversation-wide-dark.png)
- [320 dp / 180% text with reply and multiline draft](../validation/chat-ui/small-large-text-reply.png)
- [Offline notice and failed-send retry](../validation/chat-ui/conversation-offline-retry.png)
- [Video reply, missing thumbnail and upload progress](../validation/chat-ui/mixed-media-compact.png)

They were visually reviewed alongside the empty, deleted-message, large-text
reply and voice-playback-error renders generated under `build/reports/chat-ui/`.
