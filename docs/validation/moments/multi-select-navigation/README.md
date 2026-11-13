# Multi-selection and navigation verification — 2026-10-23

Production Compose components rendered with synthetic test fixtures. No real
account content or fake production records are used. These show the new bottom
bar and responsive composer; they do not prove native gallery interaction.

| Capture | Environment |
|---|---|
| [Android Home/navigation](android-navigation-home.png) | 390×844 dp, standard text |
| [Android dark navigation](android-navigation-dark.png) | 390×844 dp, derived dark theme |
| [Android larger text](android-navigation-small-large-text.png) | 320×640 dp, 1.6× text |
| [Android composer, larger text](android-create-preview-small-large-text.png) | 320×640 dp, 1.6× text; wrapped controls |
| [iOS Home/navigation](ios-navigation-home.png) | iOS 26.5 simulator Compose runtime, 390×844 dp |
| [iOS larger text](ios-navigation-small-large-text.png) | iOS 26.5 simulator Compose runtime, 320×640 dp, 1.6× text |

Figma source: Home page `1:3` in `LztysD1YvINX7RwZpnhhTt`, visually inspected in
the browser. Connector quota prevented layer/asset extraction; exact fidelity is
not claimed. Native OS safe areas, gallery interaction and hardware camera remain
separate from these fixture captures.

## Native picker follow-up on iPhone 17

After the Mac was unlocked, the production `IosChatMedia` Swift adapter and KMP
framework were compiled into a temporary UIKit test app on iPhone 17 / iOS 26.5.
The following checks passed through the real PHPicker UI:

- Five synthetic PNGs (562–675 bytes each) returned five decodable photos, zero
  failures, and the exact selection order: 250×180, 350×180, 450×180, 300×180,
  400×180. See [native callback evidence](ios-native-five-photo-result.png).
- With a limit of three, tapping a fourth image did not add it; the callback
  returned exactly the three selected images in order.
- With one remaining slot, selection returned one decodable image with zero
  failures.
- Cancelling returned an empty batch with zero failures.

This verifies the production native adapter, not authenticated Moment publication.
At that point the main iPhone 17 app showed sign-in. The later
[authenticated full-flow check](../full-flow/README.md) verified the actual
picker-to-composer, publication and appreciation paths and supersedes that gap.
Its draft and batch transitions also have automated coverage.
The user explicitly confirmed Home / Chat / Profile for bottom navigation.
