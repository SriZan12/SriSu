# Libsignal binding evaluation

Run with JDK 21 from the frontend root:

```text
gradlew.bat -p tools/e2ee-spike test --console=plain
```

Pinned dependency: `org.signal:libsignal-client:0.105.0`, official Signal Maven
repository. Primary source inspected at
`4beb029d8a941f81e7d9c6d8af1ed25a677569a8`; release v0.105.0 was published
2026-10-07. Upstream is AGPL-3.0-only and explicitly does not support external use.

This harness calls the real Java/Rust native binding using synthetic identities.
It checks bidirectional PQXDH sessions, serialized session reconstruction, replay,
tamper, identity replacement and delayed messages. Private fixture bytes never
leave memory and are never printed. It does not use production accounts or data.

The Windows JVM tests pass. On 2026-10-10 the native Android and iOS evaluation
also passed, including protected persistence and cross-platform exchange. This
remains an isolated evaluation. SriSu now uses the production native adapters;
the later `EngineTests` and `ProductionRelayTest`/`ProductionRelayTests` exercise
those adapters directly. See `docs/flows/couple-chat.md` for current app integration
and the backend `docs/couple-chat.md` for the complete verification record. The
initial counts below describe the prerequisite spike, not all current coverage.

## Native checks

Android: API 35 x86_64 Google Play emulator, WHPX on Windows, JDK 21.0.10.
Both official artifacts are required. The evaluation pins upstream's desugaring
dependency and packages x86_64 only, excluding the unused native testing library.
The resulting debug APK is 123,720,996 bytes; this is an operational cost of the
full upstream native library, not a release-size target.

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\acer\AppData\Local\Android\Sdk'
.\gradlew.bat -p tools/e2ee-spike -PwithAndroid=true :android:assembleDebug :android:assembleDebugAndroidTest --console=plain --max-workers=2
adb -s emulator-5580 install -r tools/e2ee-spike/android/build/outputs/apk/debug/android-debug.apk
adb -s emulator-5580 install -r tools/e2ee-spike/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -r -e class com.srisu.spike.SignalSpikeTest,com.srisu.spike.PersistenceTest com.srisu.spike.test/androidx.test.runner.AndroidJUnitRunner
```

Android: **3 tests passed**. iOS: Apple silicon Mac, Xcode 27.0 (27A266a),
iPhone 17 simulator / iOS 26.5. CocoaPods 1.17.0 uses dynamic frameworks, as
upstream's native linker configuration requires. The iOS Rust artifact is pinned
to the official release checksum `556a82414b545490085a0e21d94990788bdcbee4d769043f7889397872443faf`.
The checksum file itself matched GitHub's published SHA-256 asset digest.

From `ios/`, using Ruby with the installed CocoaPods/xcodeproj gems available:

```sh
ruby generate_project.rb
pod install
xcodebuild test -workspace SignalSpike.xcworkspace -scheme SignalSpike \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" -derivedDataPath build \
  -parallel-testing-enabled NO -only-testing:SignalSpikeTests/BindingTests \
  -only-testing:SignalSpikeTests/PersistenceTests
xcodebuild build-for-testing -workspace SignalSpike.xcworkspace -scheme SignalSpike \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" -derivedDataPath build
```

iOS: **2 tests passed**. Both persistence fixtures use native SQLite transactions
for protocol state, outbox ciphertext and processed incoming messages. Record
values use native AES-256-GCM, authenticated with the store scope and record key.
Android's key is non-exportable through Android Keystore; iOS uses a
WhenUnlockedThisDeviceOnly Keychain item. The tests reconstruct stores, retry the
exact committed ciphertext, inject pre-commit exceptions on send and receive,
check duplicate/conflicting IDs, inspect database/side files for fixture plaintext
and identity bytes, and refuse recovery when the storage key is missing. Android
uses noBackupFilesDir and disables backup in the manifest; iOS excludes its store
directory from backup and sets complete file protection. These are emulator tests,
not evidence of hardware-backed key storage or a device security audit.

## Cross-platform exchange

`run_interop.py` requires installed Android APKs and the iOS build-for-testing.
`--mac-root` is an isolated folder containing `frontend/` and a `simulator-id` file.
The SSH host key must already have been verified. Example from the frontend root:

```powershell
python tools/e2ee-spike/run_interop.py --adb "PATH_TO_ADB" --android-serial emulator-5580 --mac-host "USER@MAC" --identity "PATH_TO_PRIVATE_KEY" --known-hosts "PATH_TO_VERIFIED_HOSTS" --mac-root "/Users/USER/srisu-couple-chat-evaluation"
```

On Windows, `--ssh` and `--scp` can select explicit OpenSSH executables. The local
Windows client's compatibility override was `--ssh-option KexAlgorithms=curve25519-sha256`.
The relay copies only public prekey bundles and ciphertext. It never transfers
private protocol/storage keys or plaintext message bodies. Each Android phase
force-stops the evaluation process first; each iOS phase runs a fresh XCTest host.
Xcode may move its data container during installation, so the runner resolves it
again after each phase. The protected records survive these transitions.

**All 10 phases passed**, run `8be8d419-e1b8-42dc-a6e7-c60f9c041f39`:
Android initiates to iOS, iOS initiates to Android, both reply after restart, retry
committed ciphertext, and process delayed/out-of-order messages while rejecting
tampering and replay. Evidence is under ignored `build/interop/<run-id>/`.

## Limits

These fixtures use one synthetic account pair per room and fixed prekey IDs.
They do not implement production key rotation, device replacement, history UI,
backend transport, media encryption or full local database migration. Injected
rollback is not a power-loss/fsync test. The caller/peer identifiers are synthetic;
production authorization and authenticated operation-envelope validation still
need integration. Neither an E2EE lock indicator nor a release claim follows from
the spike alone. License obligations, unsupported external-use maintenance and
independent security review remain release considerations.
