# Couple Chat UI revisit — 2026-10-17

## Recovery on dev-core-architecture — 2026-10-19

This section supersedes the text-only presentation scope below. The user explicitly
authorized switching to `dev-core-architecture`, keeping Matrix, and recovering
our previous chat UI/features. No reset, commit, push or deployment was performed.

### Git evidence and selected sources

- Current frontend base: `98f17e769bf4d0a43b444919f6f81aac91b76b3b`,
  `dev-core-architecture`; fetched before recovery.
- PR #24 / merge `ccdf4cb` included the earlier chat implementation on October 10.
- Later refinements `0900d30` and `b0421d47691f741a92ded9b1db41531d5de89227`
  were retained on `dev-core-architecture-chat-system` and
  `codex/consolidate-core-20261010`, but never merged into this branch. Both remote
  refs were fetched and verified. `b0421d4` is the presentation recovery source.
- The October 18 Matrix merge `b0e335e89cd9a884fd1cdd5d4ebe229ccf5f7944`
  deleted `features/couplechat` and replaced its route with the narrower Matrix
  screen. This was a route/implementation replacement, not lost Git history.
- Backend inspected read-only: `codex/couple-chat-rebuild`,
  `2a7d1f7d502bbaf3fb11b2330835a0d64bbe3ea5`. No backend files changed.
- Live Figma context and screenshot: thread
  [184:274](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=184-274).
  Iris/Dawn, compact media and custom romantic effects are intentional recovered
  product refinements, not claims of exact Figma fidelity. Existing Satoshi,
  tokens and vector resources are reused.

The original-source ZIP and unapplied reference patch remain under the ignored
`composeApp/build/reports/chat-recovery/` directory. Existing unrelated patches,
notification planning notes and backend `.DS_Store` were preserved. The Xcode
project also contains nonfunctional synchronized-folder metadata changes observed
during this session; they were not reset.

### Findings, corrections and review

| Finding and evidence | Impact / priority | Correction | Verification status |
| --- | --- | --- | --- |
| The active route reached the replacement text screen, while the requested design survived only on an unmerged branch; Git diff and baseline render | Major functional regression: themes, message actions and media entry points were inaccessible | Reconnect recovered presentation to the existing Matrix repository and `ChatNav.CoupleConversation`; keep its authorization/navigation fixes | Route/code verified; restored screen rendered on Android and iOS test runtimes |
| Replacement screen omitted the recovered Iris palette and component family; same-fixture before/after renders | Aesthetic/product continuity, major refinement | Restore five appearance choices, Iris default, grouped avatars/corners, compact media, replies/reactions and original composer | Visually verified light/dark and 160% text; physical keyboard/safe-area behavior is not established by these renders |
| Photo/video/voice and message actions were absent from the Matrix presentation; source and baseline | Blocking the previously requested workflows | SDK encrypted file events and thumbnails; bounded native preparation; inline voice owner; full-screen photo/video viewer; reply, reaction and own-message deletion | UI layouts and ownership/mapping tests pass; live two-client encrypted media and native capture/playback are unverified |
| Original missing-composer report showed a disabled legacy device state; empty-state action was unreachable in that screenshot | Blocking conversation start | Composer remains present and editable; send/attachment actions follow real Matrix readiness. Empty ready state can type and send | Visually verified on Android/iOS; Android interaction test sends the first message |
| A naive restoration would revive a 4,000-character UI limit, silently hide failed relation events, and follow own messages from another device | Usability and recovery defects, major | Use existing Matrix input validation; explicit retry for failed reaction/deletion outbox events; auto-follow only near latest or for this device’s transaction | Regression coverage for input/failed changes/identity/scroll decision; live concurrent pagination still unverified |
| Draft restoration could overwrite text typed during opening; account switch could retain prepared bytes until async cleanup | Draft loss/privacy, blocking | Preserve typing during opening, persist text/reply/theme in existing secure storage, immediately clear private UI on account change, sanitize deleted reply previews | Focused VM tests; failures preserve editable text, accepted outbox sends clear only unchanged drafts |

The correction pass checked photo/video captions, reaction spacing, voice controls,
dark contrast and 160% text. Async image renders now wait for real decode completion;
container dimensions depend on metadata rather than decode, avoiding a height jump.
No new decorative redesign was introduced.

### Transport and lifecycle boundaries

Recovered `features/couplechat` files are presentation models/components and native
media adapters only. There is no recovered legacy repository, database, websocket,
API client, custom encryption key store or operation endpoint. Trixnity remains the
only timeline, outbox and encrypted media transport.

- Replies use Matrix reply relations. Photos/videos/audio use encrypted file
  descriptors and encrypted thumbnails; a plaintext attachment URL is unavailable.
  Private bytes and file keys never enter navigation arguments.
- Trixnity 5.9.2 deliberately sends `m.reaction` unencrypted. SriSu therefore sends
  the annotation relation inside a Megolm-encrypted `m.room.message`, with readable
  fallback body `Reacted …` / `Removed a reaction`. Latest annotation per sender
  wins; an empty key removes it. Old text-only clients display the fallback. This
  is a SriSu client convention using Matrix relations, not generic Matrix-client
  reaction interoperability. No new backend endpoint or crypto is introduced.
- Read double checks require a real partner receipt whose event is known. Receipts
  advance only after visible incoming content and do not move backward. Single
  checks mean accepted by the server; independent delivery/played claims are absent.
- Voice playback stays in its bubble with one owner, actual duration/progress,
  cancellation and stale-callback guards. No synthetic waveform is sent. Viewer
  handoff, backgrounding and account changes relinquish playback/private bytes.
- Text, reply context and appearance use the existing KVault-backed SessionStorage,
  scoped by account and room. Logout clears that storage. Unsent attachment bytes
  are memory-only until accepted by the SDK outbox; process-durable media drafts
  are not claimed. Selection is one attachment per message, bounded to 16 MiB/120 s.
- Romantic matching and drawing are recovered separately. History, incremental
  initial sync, pagination and duplicate acknowledgements cannot replay effects.
  Custom paths provide hearts/bubbles without emoji particles; reduced motion
  disables the overlay and it has no input handlers.

### Evidence and remaining scope

Screenshots in `docs/validation/chat-recovery/` use synthetic fixtures only. Android
renders are Robolectric native graphics; iOS renders run Compose/Skia on the dedicated
`SriSu Couple Chat Evaluation` simulator, with a layout-only media driver. These
are not a signed-in app session, camera permission test or live Matrix exchange.

| Final check | Actual result |
| --- | --- |
| `testDebugUnitTest` | 219 discovered: 218 passed, 0 failures/errors, 1 skipped. The existing `CoreLocalTransportIntegrationTest.pairedHttpDatabaseAndLogout` requires its live local services and was not exercised |
| `compileDebugKotlinAndroid`, `compileKotlinIosSimulatorArm64` | Passed |
| `assembleDebug` | Passed; Android debug APK built |
| Xcode `iosApp` Debug build, Apple-silicon simulator, signing disabled | Passed against the final source, including `ChatMediaDriver.swift` and `ChatVideoPreparation.swift`; app was not installed over the user's existing app |
| `iosSimulatorArm64Test --device 0055683D-7DBA-44C7-855D-4AA64123C522` with chat render/recovery/playback filters | 24 passed, 0 failures/errors/skips; includes 11 UI fixture renders |
| `python3 tools/theme/check_theme.py` | Passed: 96 documented colors, 24 M3 role pairs at least 4.5:1, minimum 4.75:1, token audit passed |
| `git diff --check` | Passed |

The native Xcode build uses `ARCHS=arm64 ONLY_ACTIVE_ARCH=YES`, matching the
Matrix branch's supported simulator target. An earlier generic simulator build
requested the removed Intel target and failed in `syncComposeResourcesForIos`;
this did not require changing dependencies or the project build script.

Evidence: [before conversation](../validation/chat-recovery/before-conversation.png),
[recovered conversation](../validation/chat-recovery/after-conversation.png),
[photo, voice and reply](../validation/chat-recovery/after-media.png),
[video](../validation/chat-recovery/after-video.png),
[dark media](../validation/chat-recovery/after-media-dark.png),
[iOS empty with composer](../validation/chat-recovery/ios-empty.png),
[iOS media](../validation/chat-recovery/ios-media.png),
[iOS 160% text](../validation/chat-recovery/ios-large-text.png).

Still outside this recovery: Spark/Ask/polls, Plan/Moment/story cards, nickname and
presence/privacy settings, message editing, played receipts and independent delivery
receipts. Those need compatible Matrix/domain contracts; invoking the retired Django
message system to make them appear functional would violate the selected architecture.
Two-device encrypted sends, attachment retries after process death, live pagination,
native recorder/player output, keyboard transitions and platform back gestures remain
release checks. No deployment or migration was performed and no old message history
was imported.

---

## Scope and baseline

Frontend: SriZan12/SriSu, branch `codex/couple-chat-rebuild` (the user chose to work on
the current branch), starting HEAD `33a5a892bf529408a0be0c41e6417923ca48b4ce`. The checkout
had untracked `docs/architecture/notifications-crashlytics-plan.md`, `iosApp/Pods/` and
`test__auth_flow_and_partner_linking_testing_.patch`; they were left untouched. Nothing was
committed, pushed, stashed or reset. Figma was viewed read-only and not modified.

`origin/dev-core-architecture` is 9 commits ahead and 6 behind this branch. It still contains
the legacy `couplechat` client (voice, media, reactions), which the Matrix cutover on this
branch deliberately removed. The user chose to redesign the **Matrix text chat** only;
nothing from the legacy branch was merged or cherry-picked.

Backend (`../SriSu-backend`, branch `codex/couple-chat-rebuild`) was read only, to confirm
the partner fields used by the header: `have-couple-connection-requested/` returns
`connection.partner` through `CoupleConnectionPartnerSerializer`
(`id`, `full_name`, `username`, `profile_photo`) and `connection.chat_room_id`.

### What the Matrix client actually supports (and therefore what the UI shows)

| Capability | Supported | UI treatment |
| --- | --- | --- |
| Encrypted text, timeline of newest 100 events | Yes | Redesigned |
| Pending / sent-to-server / failed + retry | Yes (`MatrixMessageDelivery`) | Clock / single check / alert icon; "Not sent · Tap to retry" |
| Delivered / read receipts | Not exposed | Not shown (no double ticks) |
| Undecryptable event | Yes (`unavailable`) | Outlined, italic, lock icon |
| Offline (sending paused), loading, error, room unavailable | Yes | Redesigned, consistent status component |
| Partner name/photo | Django connection API | Header + empty state, initials fallback |
| Presence, typing, "Active now" | No | Not shown; header subtitle states E2EE instead |
| Replies, reactions, edit, delete, message actions | No (deferred in `couple-chat.md`) | Absent |
| Voice, photo/video, viewers, attachments | No | Absent — no inert `+`, camera or mic controls |
| Older-history pagination | No (newest 100 only) | No history loader; jump-to-latest only |

## Critique of the baseline (rendered, Android Robolectric)

Evidence: `artifacts/chat-ui-revist/before/android/*.png`.

1. **Sender is indistinguishable.** Both sides used the same warm fill
   (`primaryContainer` and `surfaceVariant` are both `#EFE9E6`). Users rely on alignment
   alone, which fails at a glance and for short messages. → Outgoing uses the Figma thread's
   ink (`primary`/`onPrimary`); incoming keeps the warm surface (`surfaceContainerHigh`).
   *Component:* `ChatMessageBubble`.
2. **No time information at all.** There were no timestamps or day boundaries, so a
   conversation spanning days read as one block. → Day headers (Today / Yesterday /
   "Fri, 16 Oct") and a time on the last bubble of each group, placed inline at the end of
   the last text line when it fits. *Components:* `ChatTimeline`, `TextWithMeta`.
3. **Delivery state was textual and clumsy.** "Sending…" used the letter-spaced eyebrow
   style; "Not sent  Retry" was crammed inside the bubble and inflated it; sent had no
   signal. → Compact status icons for your messages only (clock, single check meaning
   "reached the server", alert), and a separate full-width 48 dp "Not sent · Tap to retry"
   target under the failed bubble. No delivered/read claim. *Component:* `MessageMeta`.
4. **No grouping rhythm.** Uniform 8 dp gaps and uniform pill corners made every message a
   separate island. → 2 dp inside a group and 12 dp between groups (same sender, same day,
   ≤ 5 min, not after a failure), with 6 dp joined corners on the sender side.
   *Components:* `buildChatTimeline`, `ChatTokens.bubbleShape`.
5. **Anonymous header.** "Conversation" with no partner identity. → Partner avatar
   (photo, initials fallback) + name, and an honest "End-to-end encrypted" line.
   *Components:* `ChatHeader`, `ConnectionChatPartnerSource`.
6. **Composer friction.** An outlined rectangle plus a wide text "Send" button; the field
   was disabled during each send (which can dismiss the keyboard) and while offline (which
   blocked drafting); the IME action sent the message, so Enter could not insert a newline.
   → Rounded field matching the Figma composer, 44 dp icon send button with in-flight
   progress, the draft stays editable in every chat state, Enter inserts a newline, and
   only *sending* is gated on `Ready`. *Component:* `ChatComposer`.
7. **Unfinished-looking states.** Plain centred text for empty/loading/error; the offline
   strip sat between list and composer. → Figma-derived empty state with starters, skeleton
   bubbles while opening, a shared icon/title/body/action status layout, and an offline
   banner under the header. *Component:* `ChatChrome`.
8. **Scrolling did not behave like a messenger.** Messages were top-anchored and there was
   no way back to the latest after reading history. → Bottom-anchored `reverseLayout` list,
   auto-follow only when already at the latest or when you sent, a jump-to-latest button
   with a local "N new" count, and stable keys across outbox acknowledgement.

Kept as-is because they already worked: the ViewModel/repository contract and draft
ownership, room-operation guards, theme roles and typography scale, bubble text size
(`bodyLarge`), and the retry semantics.

## References and what each informed

| Source (actually inspected) | Decision it informed |
| --- | --- |
| Figma `184:142` No messages yet (read-only web view) | Empty state copy, partner avatar, right-aligned starter chips |
| Figma `184:274` The thread | Ink outgoing / warm incoming bubbles, ~20 dp radii, soft day label, rounded composer with circular action |
| Figma `184:218` First message sent | Status line under the newest outgoing message (adapted to an inline icon) |
| [Jetchat `Conversation.kt`](https://github.com/android/compose-samples/blob/main/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/Conversation.kt) (official Compose sample) | `reverseLayout`, first/last-in-group flags, tighter spacing inside groups, jump-to-bottom threshold |
| [WhatsApp read-receipt help](https://faq.whatsapp.com/665923838265756) (page truncated; meanings from the widely documented convention) | Clock = pending, single check = reached server; double ticks omitted because SriSu cannot report them |
| [Viber delivery-icon redesign notes](https://www.viber.com/?p=21995) via search summary | Same icon semantics; confirms status icons, not words |
| [MUI X chat message grouping docs](https://mui.com/x/react-chat/display/message-appearance/) | 5-minute grouping window; one timestamp per group |
| [CometChat chat design practices](https://www.cometchat.com/blog/chat-app-design-best-practices) | Distinct sender colours; unobtrusive meta |

Limits: Figma was inspected through the public read-only web canvas (no connector, no Dev
Mode values, so radii and spacing are visual estimates). No Figma Community or Dribbble
files were opened; WhatsApp/Viber were studied through help/press pages, not the apps.

## Intentional deviations from Figma

- Header is start-aligned, not centred: at 160 % text the centred title collided with the
  back button.
- No `+`, camera, mic, overflow menu, Spark cards or "Active now": unsupported, and
  `couple-chat.md` forbids inert controls.
- No avatar beside each bubble: in a two-person room side + colour identify the sender, and
  the header carries identity. Figma's thread also omits per-message avatars for text.
- Fonts follow the app default (`useSerifHeadings = false`), so the empty-state title is
  Satoshi rather than Figma's Instrument Serif preview.
- Starter chips fill the draft; they never auto-send.

## Behaviour changes

- `MatrixChatMessage.transactionId` / `stableKey`: own messages keep `txn:<id>` from outbox
  to timeline (via `unsigned.transaction_id`), so the list item is not replaced when the
  server acknowledges it. Outbox entries whose transaction already appears in the timeline
  are dropped, which also removes a brief duplicate during that race.
- `SecureChatViewModel.partner`: display-only identity from `ChatPartnerSource`; returned
  only when the active accepted connection's `chat_room_id` equals the open room; cleared
  on room switch/close; failures leave the header neutral and chat usable.
- Composer stays editable during Loading/Offline/sending; send remains gated on `Ready`.
- Timestamps use a fixed English 12-hour format and the device time zone.

## Verification

See `artifacts/chat-ui-revist/screenshot-index.md` for captures and the final task report
for command outcomes.
