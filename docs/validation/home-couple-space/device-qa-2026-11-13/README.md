# Home: installed-app keyboard and scrolling QA

13 November 2026. Frontend baseline `7cb2e32fbcb6578bf2fabd4d39b6438e6faaff6f`,
branch `codex/home-couple-space`, [frontend PR #28](https://github.com/SriZan12/SriSu/pull/28).
Backend remains `3e276ad6a38b1afdd955d760be4519628bd1b3f7`,
[backend PR #41](https://github.com/SrizanKhadka/SriSu/pull/41). No backend source,
API contract, migration, production deployment, or merge changed in this pass.
Existing signing-project edits and untracked design briefs were excluded.

Design authority is still the four approved attachments and Stage 2 composition,
including the curved partner connector. Figma Home `238:193` was last read live
on 11 November and is older context; this pass changes input synchronization,
not the approved layout.

## Defect found and corrected

Rapid input in the installed iOS app lost characters in both Chat and the Plan
editor. The intended synthetic sentence was `QA draft: coffee together after our walk.`
The old Chat produced `QAfee together after our walk.` and the old Plan editor
produced `QA draft: cofee togeherater urwal.`. The native iOS Spotlight field
accepted the same injected text correctly on the same simulator.

Screen-state echoes could replace newer local keyboard edits. Both editors now
keep immediate `TextFieldValue` state and acknowledge older model values without
overwriting newer text, selection, or composing ranges. Genuine replacements and
clears still apply. Chat's existing UTF-8 limit and validation feedback, Plan's
character limit, draft persistence, and the ViewModel's send path are retained.

The delayed-echo regression failed before the correction (`co` replaced `coffee`)
and passed afterward. Additional tests cover composition/caret preservation,
conflated acknowledgements, selection-only changes, model replacement, successful
send-clear, and rejected oversized UTF-8 input. Native iOS rapid typing was then
repeated successfully in Chat and Plan, including multiline Chat text and a Plan
preset replacement.

## Native coverage

- **Android:** Pixel 4a AVD `SriSuHomeQA`, Android 15/API 35 ARM64,
  1080 × 2340 at 440 dpi. Restored official Google emulator/system-image packages.
  Installed Compose Home tests cover independent actions, status-sheet Android
  Back, scrolling at 200% text, unpaired state and failure/retry. The opt-in
  `HomeAppDeviceTest` additionally exercises the real `MainActivity` and real
  repositories against the isolated Home QA backend; its fixture is not shipped.
  That live journey passed: repeated Home swipes, Plan typing and lower actions,
  Back dismissing the IME before the editor, saved Plan draft, Chat multiline
  typing, Send bounds above native IME insets, Home scroll return and restored
  Chat draft. The first-run notification primer was dismissed with **Not now**;
  the test waits for native window focus and asynchronous draft restoration.
- **iOS:** dedicated `SriSu Couple Chat Evaluation`, iOS 26.5. Fresh full signed
  Xcode Debug build, install and launch; actual phone/OTP login to the isolated
  synthetic account. Home scrolls to statistics above navigation. Chat Back
  returns to the prior Home section. Chat draft text survives leaving/reopening
  and an app relaunch. The composer stays above the software keyboard at normal and
  accessibility-medium text; the Plan form scrolls its actions above the keyboard.
- **Accessibility settings:** iOS accessibility-medium text and Reduce Motion
  enabled, then restored. Home statistics stack and navigation reflows into two
  rows; all lower content remains reachable. A relaunch was used to apply the
  simulator's text-size change. This is not a full VoiceOver/TalkBack audit.
- **Backend isolation:** loopback Django `8940`, Synapse `8948`, separate Home QA
  PostgreSQL/Redis containers and synthetic accounts. No production account or
  data was used. The backend source and existing companion PR are unchanged.

Full Android unit suite: **515 reported, 514 passed, one opt-in HTTP test skipped**,
zero failures/errors. Selected iOS suite: **77 reported, 75 regular tests executed**,
zero failures/errors; two opt-in live tests return early without their fixtures.
Android app/test APK builds, iOS simulator compilation, full signed Xcode Debug
build, theme audit and whitespace checks passed. See
[machine-readable results](verification.json). No backend suite was rerun in this
frontend-only pass; the unchanged backend head's CI was checked separately.

## Captures

These are native images, not generated previews. Android fixture captures exclude
OS chrome; iOS captures are full installed-app screenshots. The baseline Home
images precede the input fix, which does not alter Home's visual tree.

| State | Evidence |
| --- | --- |
| Android paired / no partner | [Paired](android-instrumented-paired.png), [unpaired](android-instrumented-unpaired.png) |
| Android 200% text, bottom | [Capture](android-instrumented-large-text-bottom.png) |
| iOS Home and lower sections | [Home](ios-home.png), [bottom](ios-home-bottom.png) |
| iOS Chat rapid-input regression | [Before](ios-chat-keyboard-before.png), [after](ios-chat-keyboard.png) |
| iOS Plan actions above keyboard | [Capture](ios-plan-keyboard-bottom.png) |
| iOS accessibility text / reduced motion | [Home bottom](ios-large-text-reduced-motion-bottom.png), [Chat keyboard](ios-large-text-chat-keyboard.png) |

## Smoothness and limits

Repeated iOS Home swipes, modal scrolling, keyboard transitions and Home/Chat
navigation remained responsive during observation. Android touch-scroll and
navigation assertions check functional progress, not a frame-rate budget.
Apple's Animation Hitches instrument explicitly reports that hitches are
unsupported on this simulator. No 60/120 fps or release-hardware performance
claim is made. Physical Android/iPhone scrolling, thermals and low-end-device
performance remain release checks.

The reused isolated Matrix history has unavailable encrypted messages on the
newly logged-in iOS device because its historical keys are absent. This pass
verifies the composer and navigation; it does not establish a fresh encrypted
two-device send/decrypt result. Earlier isolated Matrix verification remains
dated evidence. No change to encryption, uploads, playback, replies or reactions
was made. Android Studio's cached SDK inventory could not show the restored AVD;
native Android instrumentation was used without restarting the user's IDE.

## Reproduction

Run the documented theme audit, full Android unit suite, Android app/test builds,
iOS simulator compilation and the selected Home/Plan/ChatRecovery/component iOS
tests. A full Xcode app build is separate from simulator-target compilation.

```sh
python3 tools/theme/check_theme.py
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebugAndroidTest \
  :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:iosSimulatorArm64Test \
  --device 0055683D-7DBA-44C7-855D-4AA64123C522 \
  --tests 'com.srisu.srisu.features.home.*' \
  --tests 'com.srisu.srisu.features.chat.ChatRecoveryTest' \
  --tests 'com.srisu.srisu.features.plans.*' \
  --tests 'com.srisu.srisu.components.*'
```

`HomeAppDeviceTest` requires a dedicated installation built with
`SRISU_API_BASE_URL=http://127.0.0.1:8940/`, loopback ADB reverses for 8940/8948,
and an app-private `files/home-app-device-fixture.json` containing scope
`isolated-home-ui-qa`, that exact `base_url`, and a valid synthetic `session` for
`+9779800000241`. Obtain test credentials from the isolated seed; never commit or
print them. The test rejects a different existing login, removes the fixture and
clears its synthetic session afterward. Without an explicit fixture it skips.

```sh
adb -s emulator-5560 shell am instrument -w -r \
  -e class com.srisu.srisu.features.home.HomeDeviceTest,com.srisu.srisu.features.home.HomeAppDeviceTest \
  com.srisu.srisu.test/androidx.test.runner.AndroidJUnitRunner
```

Both previously published heads had successful CI when checked on 13 November:
[frontend run 35](https://github.com/SriZan12/SriSu/actions/runs/42962705082) and
[backend run 16](https://github.com/SrizanKhadka/SriSu/actions/runs/42571530468).
The PR description records CI for the subsequent input-fix commit separately.
