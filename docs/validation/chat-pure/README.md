> Follow-up: [shared palette structure, presence and typing](activity/README.md)
> supersedes the header subtitle and preset-specific layout described in this
> initial Pure comparison. Original reference and initial evidence are preserved below.

## Publication — 2026-10-28

The full chat UI includes the shared palette structure and activity indicators,
[illustrated Quick Share](quick-share/README.md), and
[Palette grid theme picker](theme-picker/README.md). Their approved HTML sources
and Android/iOS screenshot evidence are included here. The implementation commit
is `84e1837dc61e3bbfd7daa6e6f3288e2b9018540e` on `chat-ui-pure`, targeting
`dev-core-architecture` at `6d77bc5f534835b201cff00809d20b1184f2d06a`.
Its published tree is identical to local implementation commit `045a1b1`.

Companion backend [PR #40](https://github.com/SrizanKhadka/SriSu/pull/40), commit
`e640439a1e039f70703d4dd12ceaf2db375a1902`, publishes only the presence renderer,
regression assertions, disposable activity check and rollout notes. It targets
`codex/couple-chat-rebuild` at `6e0935c` because that Matrix implementation is not
yet on the backend integration branch. Unrelated backend changes remain in the
original checkout. Synapse configuration regeneration/restart is required for
presence; there is no API/schema migration or production deployment in this task.

The final UI baseline passed 460 Android tests (459 passed, one expected live
transport skip), theme/contract checks and Android/iOS compilation. The final
iOS theme-picker suite passed four tests. Earlier activity and Quick Share suites
and native-render limitations are recorded in their individual reports. A fresh
isolated backend publication checkout passed system checks and 286 tests
(264 passed, 22 expected skips), plus the disposable live Synapse presence/typing
check. This is separate from the earlier 294-test run that included unrelated
local backend work. Native keyboard transitions, physical-device media exchange
and frame-time performance remain unverified. The dated implementation snapshots
below and in the follow-up reports describe their state before publication.

# Pure conversation UI — 2026-10-28

Implemented on `chat-ui-pure`, created from freshly fetched
`origin/dev-core-architecture` at `6d77bc5f534835b201cff00809d20b1184f2d06a`.
Frontend: `SriZan12/SriSu`. Backend: `SrizanKhadka/SriSu`, inspected at
`6e0935c1c4f24267b9a956d259db5d113e9bb903` on `codex/couple-chat-rebuild` with
pre-existing changes. No backend source, API, database, or deployment changed.
Integration branches remain frontend `dev-new-theme` and backend `dev`.

## Reference

[reference.html](reference.html) is an unchanged copy of the actual approved preview,
originally saved at:
`/Users/srijan/.codex/visualizations/2026/10/28/01a1829e-2310-7de0-8145-4be17a976786/srisu-chat-options.html`.
SHA-256: `c46f143fc4330106c48d7da3269d5d7dce9e54a5d3d80ecc63cf1ace0807aea9`.
Only its initial **Pure** variant is the implementation reference. Names, messages
and waveform in the screenshot fixtures are synthetic, never production defaults.

Original Figma context: file `LztysD1YvINX7RwZpnhhTt`, message page `1:5`, thread
`184:274`, design-system page `0:1`. Live design-context and screenshot requests
were attempted; both were blocked by the connector's Starter-plan quota. The local
approved preview was available in full and takes precedence per the user's request.

## Implementation

- Satoshi body 16/23.68, medium 17 header; Instrument Serif 23 for the Spark question.
- White background, flat gray incoming and solid charcoal outgoing bubbles.
- 18 dp timeline gutters (13 dp below 370 dp), 14/9 bubble padding, 20 dp corners,
  6 dp sender corners, 3 dp joined gap and 16 dp group gap.
- Centered avatar/name/security group with symmetric 44 dp controls; no header divider
  or repeated timeline avatars. Large names elide without displacing controls.
- Spark uses 85% of available width, 18 dp radius, 15/14 padding, real encrypted question.
- Bare voice notes use a 218 dp card, outlined play control, real levels, duration,
  playback/seek/retry and accessible speed action on the duration. Older events with
  no waveform retain a neutral low-level placeholder, not a made-up waveform.
- Composer uses one gray 28 dp pill, plus, partner placeholder and 44 dp mic/up-arrow.
  Existing draft, reply, recording and attachment states remain wired. At larger text
  the input and controls use separate rows; tall drafts/previews are bounded.
- Existing list keys, arrival effects, pagination, new-message following and receipt
  ownership remain. Pure text groups carry metadata below their final bubble; failed
  and pending messages retain immediate status and retry.
- Pure becomes the default. An explicitly stored previous palette is respected and
  Pure can be selected in the existing appearance sheet. Only its palette preview was
  adjusted for legible white outgoing text; the sheet layout was not redesigned.

## Comparison and intentional differences

[Comparison](comparison.png): source / Android / iOS normalized to 393 × 800 display
units. The source's rounded phone frame and fake system bars belong to the mockup;
Compose renders use the full test viewport, and the native app uses real safe areas.
This is visual review evidence, not a zero-difference golden test.

The first pass was corrected for native line-height trimming, header alignment,
composer controls, Spark footer spacing and voice waveform density. Remaining:

- Real date and group timestamps are retained, including more group times than the
  demo shows. `Read 6:45 PM` means a read message sent at 6:45, not a fabricated
  read-receipt timestamp. Failed/loading/offline content can change the composition.
- The Spark footer reads **Open Spark**, not **Answer to reveal**; the event reference
  does not contain current answer/reveal state. It opens the existing Spark flow.
- Gray/lilac surface secondary text is `#6D6F75` instead of `#707278`, a small contrast
  correction. Metadata on white keeps the exact reference color. Error/selection
  states retain their existing semantic colors.
- Native emoji and text rasterization differ slightly between Android, iOS and the
  browser. Header lock/Spark/overflow glyphs reuse existing native icon resources;
  back/plus/mic/play/up-arrow use reference-derived outlined geometry.
- Large text and narrow screens intentionally wrap/scroll and keep controls reachable.
  They cannot retain the fixed demo's exact text layout.

## Verification

`python3 tools/theme/check_theme.py`: passed. Global theme provenance preserved;
no raw visual color/type/shape tokens outside the theme.

`./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`:
425 tests discovered, 424 passed, 0 failures/errors, 1 skipped. The skipped
`CoreLocalTransportIntegrationTest.pairedHttpDatabaseAndLogout` requires the isolated
backend integration fixture, which was not started for this UI-only change.

Android evidence is Robolectric native graphics, not a physical-device session:
393 × 800 reference, 320 × 568, 160%/200% text, long name, dark mode, media/replies,
pending/failed/unavailable states, scrolling and jump-to-latest, empty compose/send,
Spark callback routing, and 393 × 440 constrained composer. The constrained render
checks available height; it does **not** prove native IME transitions.

Selected iOS suite on `SriSu Couple Chat Evaluation`
(`0055683D-7DBA-44C7-855D-4AA64123C522`, iOS 26.5): 43 tests passed, 0 failed/skipped.
Filters: `SecureChatIosRenderTest`, `ChatRecoveryTest`, `ChatPlaybackOwnerTest`,
`ChatTimelineTest`, `ChatThemeTest`, `ChatMessageMotionIosTest`. Includes reference,
small and 160% text renders, loading/history motion, stable outbox acknowledgment,
playback ownership and recovery tests. Render media drivers do not play real audio.

Xcode Debug arm64 simulator build passed. The actual `SecureChatScreen` was also
launched using a temporary synthetic repository in an isolated app identity
`com.srisu.pureevaluation`. [Native screenshot](ios-native-window.png).
Native reply/cancel accessibility actions worked. All temporary fixture sources and
entry-point substitutions were removed; they are not part of the app implementation.

**Native keyboard remains unverified.** Device Hub's computer-input adapter returned
`noWindowsAvailable` for coordinate taps and did not deliver text entry. The software
keyboard could not be exercised reliably. No Android native keyboard session was run.
No real-account or two-client Matrix exchange, native recording/playback output,
upload transfer, or real-device performance/frame-time benchmark is claimed by this
change. Those paths keep their existing implementation and automated regression coverage.

## Evidence files

- [Browser reference](reference.png), [Android](android-pure-reference.png), [iOS](ios-pure-reference.png)
- [Android small](android-pure-small.png), [iOS small](ios-pure-small.png)
- [160% text](android-pure-large-text.png), [200% long name](android-pure-long-name.png)
- [Dark](android-pure-dark.png), [constrained composer](android-pure-constrained-composer.png)
- [Scrolled history](android-11-scrolled-history-jump.png), [media and reply](android-14-restored-photo-voice-reply.png)
- [Pending and failed](android-10-pending-failed-unavailable.png)
