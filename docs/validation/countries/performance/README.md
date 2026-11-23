# Borderless flags, phone dropdown and performance follow-up

2026-11-23, `codex/country-picker-consistency`, frontend base
`b4dbf1c280a8d03f704504738a05dadf1cd430e3`. Publication target: `dev-core-architecture`.

- Shared flag artwork has no surrounding border, background tile or inner padding.
  Original flag aspect ratios remain intact.
- The phone-number calling-code control has an 18 dp down arrow inside its existing
  tap target. The arrow is decorative for accessibility; the selected country and
  prefix remain the control's labels.
- No country API, backend, stored-value, permission or authentication behavior changed.

Native Android fixture captures: [phone control](phone-country-control.png) and
[country picker](country-picker.png). The fixture uses production components;
its otherwise empty phone-control screen is not a redesign of authentication.

## Measured results

Android 15 / API 35, `SriSuNavigationQA`, 1080×2340 / 440 dpi, 60 Hz emulator,
Apple M4 host graphics. The installed release APK was verified non-debuggable.
It uses the repository's existing `minifyEnabled=false`; this is not a claim of
an R8-optimized Play build or a physical-device benchmark.

The harness uses ActivityScenario and UIAutomator with the real frame clock. It
does not advance Compose's virtual test clock. Each phase resets Android's
`gfxinfo` counters. These values use the process-level summary, not the additional
per-window summaries repeated later in each raw file.

| Phase | Frames | Missed deadlines | Reported p95 frame duration |
| --- | ---: | ---: | ---: |
| First opening in process | 75 | 4 (5.33%) | 20 ms |
| Reopen 1 | 75 | 3 (4.00%) | 18 ms |
| Reopen 2 | 25 | 5 (20.00%) | 53 ms |
| Reopen 3 | 65 | 10 (15.38%) | 32 ms |
| Sixteen scrolling gestures | 1,120 | 15 (1.34%) | 17 ms |
| Search edits | 9 | 1 (11.11%) | 27 ms |

The search frame sample is too small for a stable jank percentage. A separate
warm catalogue-filter loop took **15.025 ms for 1,000 queries**, about **0.015 ms
per query**. It excludes composing/drawing results and keyboard latency.

Interpretation: filtering is inexpensive, and sustained scrolling was mostly
smooth in this environment. Opening animations still had occasional slower frames
(first-open p99 85 ms). This does **not** establish zero lag on all phones.
Physical-device and native iOS frame profiling remain unverified. No measured
release UI-thread bottleneck was isolated that justifies changing the production
picker's architecture or animation behavior in this refinement.

Raw reports: [first opening](first-open.txt), [reopen 1](warm-open-1.txt),
[reopen 2](warm-open-2.txt), [reopen 3](warm-open-3.txt), [scroll](scroll.txt),
[search](search.txt), and [machine-readable results](metrics.json).
Interaction-to-test-idle values in the JSON include UIAutomator synchronization
and quiet waits; they are **not tap-to-visible latency measurements**.

## Follow-up regression checks

- Release app/test builds and the real-time performance test passed.
- Normal development build restored after profiling; 36 focused Android/JVM
  tests passed, with no failures or skips.
- Both Android native keyboard/selection tests passed. One earlier run timed out
  at Back dismissal; the test now waits for native UI idle and uses system Back
  through UIAutomator. No production keyboard behavior was changed.
- All six iOS catalogue/component tests passed, including small-screen/dark/large
  text and landscape renders. This is not native iOS frame-time profiling.
- Catalogue generation, all 251 flag hashes, theme validation and whitespace
  checks passed. Unrelated local work and the backend checkout were preserved.

## Measurement corrections

Initial debug/Compose-test-clock measurements were unsuitable for frame-rate
claims. The QA emulator was also running SwiftShader software graphics. It was
restarted with the command-line `-gpu host` override; its saved AVD configuration
was not edited. Apple M4 rendering was confirmed through SurfaceFlinger.

An early stress script swiped down beyond the top of the list, invoking the normal
sheet-dismiss gesture, then tried to search while dismissal was in progress. This
looked like the sheet shrinking. Search was isolated in a fresh sheet, which passed;
the speculative height change was removed. No artificial loading delay, disabled
dismiss gesture or altered modal-height behavior remains in production code.

## Reproduce

`tools/countries/profile.init.gradle` adds local debug signing and the test-host
Activity to this QA release build only. It is not applied by ordinary builds.
The reserved `.invalid` origin prevents the synthetic fixture from reaching a live
backend. No OTP, account mutation or invitation is part of the harness.

```sh
./gradlew --no-configuration-cache -I tools/countries/profile.init.gradle \
  -Psrisu.environment=staging -Psrisu.apiBaseUrl=https://example.invalid/ \
  :composeApp:assembleRelease :composeApp:assembleReleaseAndroidTest
adb -s emulator-5554 install -r composeApp/build/outputs/apk/release/composeApp-release.apk
adb -s emulator-5554 install -r -t composeApp/build/outputs/apk/androidTest/release/composeApp-release-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.srisu.srisu.components.CountryPickerPerformanceTest \
  com.srisu.srisu.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 pull /sdcard/Android/data/com.srisu.srisu/files/country-performance ./country-performance-local
```

Use a dedicated test emulator; restore its ordinary development APK after this
QA build. Raw reports contain synthetic UI only. Source guidance:
[Android performance measurement](https://developer.android.com/topic/performance/measuring-performance),
[`gfxinfo`/frame statistics](https://developer.android.com/tools/dumpsys),
[UIAutomator gestures](https://developer.android.com/reference/androidx/test/uiautomator/UiDevice).
