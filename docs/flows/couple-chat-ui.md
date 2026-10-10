# Conversation UI refinement — 2026-10-10

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
