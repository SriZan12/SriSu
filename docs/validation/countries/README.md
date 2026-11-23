# Country picker verification — 2026-11-23

Follow-up: [borderless flags, dropdown arrow and measured performance](performance/README.md).
That report adds a non-debuggable Android run with real-time gestures; the debug
timings below are retained as historical validation, not release performance claims.

Branch: `codex/country-picker-consistency`, in the primary SriSu checkout.
Frontend base: `b4dbf1c280a8d03f704504738a05dadf1cd430e3`.
Backend contract reviewed at fetched `797b0a08406a7703ed07b93f41841fe67ae44705`;
the backend checkout and its existing local changes were not modified.
This records local verification before publication into `dev-core-architecture`.
No migration, deployment or live account mutation was part of these checks.

## Results

| Check | Actual result |
| --- | --- |
| Complete Android/JVM unit suite | 630 tests, no failures, 4 conditional integration skips |
| Final focused Android/JVM rerun after label-loading optimization | 36 tests passed: catalogue 3, picker UI 5, Find Partner ViewModel 28 |
| Android production and instrumentation APK builds | Passed |
| Android 15 / API 35, `SriSuNavigationQA`, `emulator-5554` | 2 instrumentation tests passed; real modal, keyboard opening/dismissal, prefix search, selection/reopen, repeated swipes, last-country scrolling, Back/close, and preserving city when reselecting its country |
| iOS simulator compilation and test binary linking | Passed |
| iOS simulator focused tests | 6 passed: 3 catalogue and 3 Compose UI tests, including search, selection, scrolling, empty results, close and IME Done/focus behavior |
| Full iOS app build | Xcode Debug simulator build passed using `iosApp.xcodeproj`, scheme `iosApp` |
| Native iOS app inspection | iPhone 17 Pro / iOS 26.5: picker opening, flag/row rendering, selection updating the phone prefix, selected state after reopening, and close verified |
| Catalogue and assets | Generator check passed; 251 records/flag hashes checked, all 251 PNGs decoded independently |
| Theme checks | Passed; 96 colors and 24 contrast role pairs checked |
| Whitespace checks | `git diff --check` passed |

The Android full suite preceded the final change from individual label resources
to one cached string array. The 36 focused Android tests, both native Android
tests, six iOS tests and full iOS build were rerun after that change. The final
cleanup only indented the shared UI and used a nullable-safe test output directory.

## Layout evidence

These are actual component renders, not image-generated mockups. They render the
same shared picker content used by the modal. System bars, modal scrim and native
keyboard are not included in these component captures. The native modal/keyboard
was separately exercised on the Android emulator.

| Platform | Standard | Small screen / 1.8× text / dark | Landscape |
| --- | --- | --- | --- |
| Android | [390×844](android-standard.png), [selected](android-selected.png) | [320×640](android-small-dark-large-text.png) | [640×320 location mode](android-landscape-location.png) |
| iOS | [390×844 selected](ios-390-844-1.0-true.png) | [320×640 location mode](ios-320-640-1.8-false.png) | [640×320](ios-640-320-1.0-true.png) |

The small-screen cases deliberately retain enlarged text: long names wrap rather
than shrinking accessibility text. The Android dark render exposed inherited
black text on a dark surface; an explicit themed Surface fixed it before the final
captures. Screen-reader labels also avoid announcing the prefix twice.

## Performance evidence and limits

- Country rows and flags have no runtime HTTP dependency; the catalogue is lazy
  Kotlin data and labels use one cached Compose resource array.
- The final debug-emulator sample filtered 1,000 alternating name/prefix queries
  in 141.9 ms (about 0.14 ms per query). This measures the catalogue filter only,
  not text layout or frame time.
- First-open interaction-to-idle was 1,582 ms in that run (an earlier run was
  1,169 ms). This includes cold composition, modal animation and test synchronization
  on a busy host. These samples are not a controlled before/after benchmark and
  do not establish release-device startup time or a frame-rate guarantee.
- Repeated Android swipes and selection completed without functional failure.
  Release-device frame profiling and low-end physical-device checks remain open.

## Remaining coverage limits

- Native iOS software-keyboard behavior is **not verified**. The computer-use
  adapter could activate rows but could not enter text or reliably direct pointer
  events into Device Hub; typing/paste did not change the search field. Capture
  Keyboard was restored to its original setting. The separate Compose iOS tests
  verified text editing and IME Done through the test API, which does not prove
  native keyboard appearance/occlusion.
- No physical-device, TalkBack/VoiceOver speech, RTL translation or additional
  language pack was tested. Semantics expose text labels, selection and named
  controls; flags are decorative and never the only label.
- No live OTP, authenticated profile save, partner invitation or city-service
  request was sent. The relevant Find Partner ViewModel tests use synthetic data.
  Backend phone/country fields and the separate online city lookup are unchanged;
  no backend test suite was run for this frontend-only change.
- Figma metadata was accessible, but screenshot access hit the Starter quota.
  This is a derived shared component using existing theme tokens, not a claim of
  pixel comparison against newly fetched Figma images.

## Reproduction

Use the configured JDK (this run used Android Studio's bundled JBR):

```sh
python3 tools/countries/generate.py --check
python3 tools/theme/check_theme.py
./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:testDebugUnitTest --tests '*Country*' --tests '*FindPartnerViewModelTest*' :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest :composeApp:linkDebugTestIosSimulatorArm64
adb -s emulator-5554 install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb -s emulator-5554 install -r composeApp/build/outputs/apk/androidTest/debug/composeApp-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class com.srisu.srisu.components.CountryPickerDeviceTest com.srisu.srisu.test/androidx.test.runner.AndroidJUnitRunner
xcrun simctl spawn BC14261D-8642-47FC-94B1-A3E8FA4C77ED composeApp/build/bin/iosSimulatorArm64/debugTest/test.kexe '--ktest_filter=com.srisu.srisu.components.CountryPickerIosTest.*:com.srisu.srisu.components.CountryCatalogueTest.*'
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'id=BC14261D-8642-47FC-94B1-A3E8FA4C77ED' -derivedDataPath /tmp/srisu-countries-ios-build CODE_SIGNING_ALLOWED=NO build
git diff --check
```

Substitute an available simulator ID and an absolute path to `test.kexe` for the
`simctl spawn` invocation. Do not send real OTPs or mutate real profiles just to
exercise this offline selector.

See [the audit and flow](../../flows/countries.md) and
[data provenance and maintenance](../../../tools/countries/README.md).
