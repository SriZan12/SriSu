# Local UI verification — 2026-11-22

Synthetic content only. These are rendered production composables, not design mockups.

- [Android Home skeleton](android-home-loading.png): 390 dp, default text, whole hierarchy loading.
- [Android onboarding](android-photo-large-text.png): 320 dp, 180% text, scrolled to photo actions.
- [Android landscape onboarding](android-photo-landscape.png): 640×320 dp, scrolled to actions; content above remains scrollable.
- [Android interest search](android-interests-large-text.png): 320 dp, 180% text, selected long label wraps.
- [iOS interest search](ios-interests-390-1.0.png): standard text.
- [iOS compact dark search](ios-interests-320-1.8.png): 320 dp, 180% text.
- [iOS landscape search](ios-interests-640-1.0.png).
- [iOS onboarding](ios-photo-small-large-text.png).
- [iOS Home loading](ios-home-skeleton-small.png).

Android screenshots come from native-graphics Robolectric. Separate installed emulator tests passed native picker cancellation and IME inset/back behavior. iOS screenshots come from Compose tests executed in the iOS simulator. They do not show a physical iPhone keyboard or an interactive iOS Photos picker.

See [the handoff](../../flows/profile-onboarding-loading-repair.md) for test counts, branches, implementation decisions and manual coverage limits.
