# Palette grid chat theme picker — 2026-10-28

Frontend: `SriZan12/SriSu`, branch `chat-ui-pure`, base
`6d77bc5f534835b201cff00809d20b1184f2d06a`, local uncommitted changes.
Backend: `SrizanKhadka/SriSu`, existing `codex/couple-chat-rebuild`, base
`6e0935c1c4f24267b9a956d259db5d113e9bb903`. No backend implementation change.
Both checkouts' earlier work was preserved. No branch switch, commit or push.

## Approved source

The user selected **Palette grid** and explicitly authorized implementation.
[reference.html](reference.html) is the unchanged `srisu-theme-picker-options.html`
preview, including its unimplemented Preview tiles alternative. Only Palette grid
is the app reference. SHA-256:
`29b2180d0a08366f099801b7d4bf78b8582391f82bf07f6e3e2443ae7ef695b5`.

Original design context: [Figma message page 1:5](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=1-5),
thread `184:274`, design-system page `0:1`. Live metadata was requested this turn;
the connector remains blocked by its Starter-plan MCP quota. This implementation
follows the approved saved source, not a claimed fresh Figma export.

## Implementation

- One live sample, six compact swatches, short labels, outline/check selection,
  close control, Apply button and one local-only/device-appearance note.
- Neutral Pure chrome in light/dark. Preset colors are resolved against the app
  base scheme **before** installing picker chrome, preserving the existing colors,
  outgoing gradients and stable IDs. No new theme or global palette adjustment.
- Swatch selection is local, saveable UI state. Only Apply calls the existing
  `SecureChatViewModel.chatTheme(id)` and account/room-scoped preference path.
  Dismissal discards the preview; a reopened picker starts with the saved choice.
  An external saved-value change resets the pending selection.
- Accessible radio-group/selected semantics and a polite selected-preview label.
  Sample messages and Read time are synthetic and hidden from conversation semantics;
  no message, receipt, draft, reply or Matrix operation is created by previewing.
- Normal 3 × 2 layout; two columns at 130% text, one at 250%. Content can scroll
  while Close and Apply remain reachable. The native platform supplies safe areas.

`ChatAppearanceTokens` contains the scoped dimensions and typography. Native line
height trimming and disabled-button compositing were corrected after comparing the
first renders with the approved preview. Existing Quick Share and the main chat
screen retain their implementation.

## Checks actually run

`python3 tools/workspace.py verify all` passed theme/contrast auditing, shared core
contracts, Android tests and Android/iOS simulator compilation, plus isolated
backend system checks and the allowlisted synthetic test suite:

- Android: **460 tests, 459 passed, 1 opt-in live transport skip**, no failures/errors.
- Backend: **294 tests, 271 passed, 23 expected skips**, no failures/errors.

Six new Android screen checks open the actual picker through Chat appearance:
Pure, dark with saved Iris, 320 × 568, 320 × 568 with 200% platform font scaling,
cancel/reopen preserving Calm and the message draft, and apply/reopen preserving
the chosen Dawn ID and draft. The Apply button is disabled for the unchanged theme;
preview/cancel/apply generate no sent messages. Selected semantics are asserted.

Final iOS command:
`./gradlew :composeApp:iosSimulatorArm64Test --device 0055683D-7DBA-44C7-855D-4AA64123C522 --tests '*SecureChatIosRenderTest.themePicker*'`.
**4 passed, 0 failures/skips**, on SriSu Couple Chat Evaluation, iOS 26.5.
Includes Pure, dark saved Iris, 320 × 568 and 200% text, visible/selected swatches,
scrolling to Dawn, and reachable Close/Apply controls.

## Screenshots and limits

- [Android Pure, 393 × 760](android-theme-picker-pure.png)
- [Android dark / saved Iris](android-theme-picker-dark.png)
- [Android 320 × 568](android-theme-picker-small.png)
- [Android 200% text, scrolled](android-theme-picker-small-large-text.png)
- [Cancel / saved Calm](android-theme-picker-cancel.png), [Apply / saved Dawn](android-theme-picker-applied.png)
- [iOS Pure](ios-theme-picker-pure.png), [iOS dark](ios-theme-picker-dark.png)
- [iOS small](ios-theme-picker-small.png), [iOS 200% text, scrolled](ios-theme-picker-small-large-text.png)

Reviewed the 393dp renders against the 393px approved source for preview geometry,
spacing, colors, selected indicator, typography and all-six visibility. Platform
font rasterization/check glyphs differ slightly. Android captures the activity plus
its modal window; iOS captures actual sheet content in the simulator Compose test
host. Synthetic phone framing/system bars in the preview are intentionally absent.
Large text intentionally reflows and scrolls instead of preserving fixed geometry.

These are Compose platform renders and automated app-boundary checks, not a fresh
physical-device session. Native keyboard transitions, drag/backdrop dismissal,
screen-reader speech and frame-time performance were not manually re-exercised.
Persistent preference storage uses the existing unchanged path; no process-restart
storage test or live backend/Matrix exchange is claimed for this menu-only change.
No production deployment was performed.
