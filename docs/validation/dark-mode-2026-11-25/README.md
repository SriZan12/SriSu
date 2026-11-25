# Dark-mode refinement — 25 November 2026

Frontend: `codex/dark-mode-polish`, based on `3872d11ca4d17a40691040830a28efed918bae74`.
Backend: unchanged at `8e5c91645caae34541d5d523afab0dc0b5b77c2f`; no API or migration changes.
Work stays in the original checkout. These changes are included in the subsequent
`codex/home-refresh-partner-nicknames` frontend publication; no deployment is
performed by that workflow.

## Cause and correction

Home and Questions installed a light-only nested Material scheme and used static
light colours, including the bottom navigation. These now resolve a shared palette
from the effective app theme, respecting both system mode and Settings overrides.
The header, canvas, question card, sheets, skeleton, empty states, topic tiles and
navigation use dark surfaces and readable paired text colours.
Android Home sheets also explicitly apply the effective app appearance to their
own window. Material 3 otherwise derives their system-bar icons from the OS mode,
which made them unreadable when a user selected dark SriSu on a light-mode phone.

Notifications' purple unread wash is replaced with a subtle neutral surface;
the unread dot and semantic state remain. Settings Together and selected countries,
Moment audiences/crop, interests, Plans and Challenges use the shared neutral
selection roles. Artwork and notification-type badges retain their meaning/colour.
The light palette and Material's light filter defaults remain unchanged. Saved
Chat palettes are untouched.

The user requested a derived dark treatment. Figma design-system node `0:1`, file
`LztysD1YvINX7RwZpnhhTt`, was requested again but the tool quota blocked inspection.
No claim of an approved Figma dark-mode export or new pixel-exact dark reference.
See [palette rationale and source](../../design-system/README.md#dark-surfaces-and-selection-2026-11-25).

## Verification

- Full Android unit suite: 703 tests, zero failures, seven opt-in integration skips.
- After preserving Material's exact light chip defaults, 34 scoped theme/Profile
  checks passed; Android APK/test APK and iOS simulator compilation also passed.
- iOS simulator: 13 checks passed (11 native Compose screen cases and two palette
  contrast tests), including dark Home, quiet/unpaired/loading, 320 dp/200% text,
  Questions home/answer, Notifications, Settings and large text.
- Android native UI uses the dedicated SriSuHomeQA Android 15 emulator, 1080×2340,
  density 440, installed production composables with synthetic data. Covers active
  appearance changes, dark/light rendering, scrolling, navigation callbacks, honest
  empty states, bottom sheets, native answer keyboard and 200% text.
  The 22-case batch had one screenshot-helper failure; the corrected sheet case
  and dark answer keyboard case subsequently passed in a targeted two-case rerun.
  After the native sheet fix, all 14 Home/Questions cases passed again with the
  production activity's edge-to-edge setup; the other eight Settings/Inbox cases
  passed in the earlier batch. All 22 cases are therefore covered after reruns.
- Android APK/test APK and iOS simulator compilation passed. Theme token audit and
  Git whitespace checks passed. New dark text/surface pairs meet at least 4.5:1.
- The final Android sheet-window correction passed three native cases (sheet
  system-bar flags, live appearance switching and answer keyboard), three scoped
  theme tests, and Android/iOS compilation. No iOS behaviour changed in that fix.
- A repeated full suite exposed two existing Profile render harness timeouts. Both
  passed in isolation; draining the paused Robolectric main looper during their
  mocked-load wait made the full suite pass, without changing production behaviour.
- The initial native Home sheet screenshot failed because both underlying and sheet
  windows matched `isRoot`. Capture now uses the whole native display; interaction
  assertions remain intact. Home captures wait for WindowManager's separate dialog
  fade; Question captures also include the actual OS keyboard.

These are native Compose test surfaces, not a signed end-to-end UIKit app session
or live-account backend run. iOS keyboard behaviour and physical-device behaviour
were not reverified in this palette task; Android's native keyboard was exercised.
No measured frame-rate claim is made. Personal photos, signing configuration,
`.env`, `local.properties`, Finder metadata and backup stashes remain untouched.

## Screenshots

- [Home](ios-spark-390-1.0-dark.png), [quiet day](ios-quiet-390-1.0-dark.png),
  [unpaired](ios-unpaired-390-1.0-dark.png), [loading](ios-loading-390-1.0-dark.png).
- [Questions](ios-decks-dark-dark.png), [answer](ios-answer-dark.png),
  [320 dp / 200% text](ios-catalogue-active-small-200-dark.png).
- [Notifications](ios-notifications-dark.png), [Settings Together](ios-dark-connected.png).
- [Android Questions](android-questions-dark.png),
  [Android Home status sheet](android-home-status-dark.png),
  [Android answer keyboard](android-question-keyboard-dark.png),
  [Android Settings](android-settings-dark.png).

All screenshots use synthetic screen state and existing bundled test artwork.
The Android keyboard is supplied by the emulator's IME and retains its own theme.
