# Couple Chat UI revisit — 2026-10-17

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
