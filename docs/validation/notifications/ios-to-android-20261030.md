# Native iOS → physical Android push — 30 October 2026

The complete repeated run passed. The sender was a dedicated **iPhone 17 simulator,
iOS 26.5**, running the production `SecureChatViewModel`, Darwin HTTP transport,
`TrixnityMatrixChatRepository` and `IosMatrixPlatformStore`. The receiver was the
physical **Samsung SM-A075F, Android API 36**, controlled from Windows through
paired Android Wireless debugging. This was not a physical-iPhone test or a
manual tap on the iOS Send button: the native test drove the real composer ViewModel.
Apple enrollment, signing changes and APNs setup remained skipped.

## Executed flow

1. Two synthetic partners used independent native cryptographic stores on iOS
   and Android. The Android receiver registered its real Firebase installation
   and `matrix-chat-push-1` capability, then entered the background.
2. The iOS composer sent a unique synthetic text through encrypted Matrix.
   Synapse delivered the encrypted event to Django's application service, and
   the existing notification worker sent the actual FCM alert. No direct Firebase
   test message or fabricated Django event substituted for this path.
3. Android received the generic private alert on `srisu_updates`. The device
   reported a sound URI, default-or-higher importance, normal ringer mode, nonzero
   notification volume and no DND restriction.
4. The user confirmed **“Yes, I heard it”** for the first iOS-generated alert at
   **19:42:17 Nepal time**. This is human confirmation of the speaker, separate
   from the automated sound-policy assertions.
5. The repeated run's alert arrived at **19:50:32 Nepal time**. Its real
   notification `PendingIntent` opened MainActivity, and the delivery resolved
   to the intended Django room UUID. Native accessibility assertions found the
   exact decrypted iOS message and an editable composer in that conversation.
   Android `FLAG_SECURE` remained enabled.

Final results:

- `IosToAndroidPushLiveTest.nativeIosMessageReachesAndroid`: **1 passed**, 232.613 s
  including time waiting for the Android companion and completion signal.
- `IosToAndroidPushDeviceTest.iosMessageRingsAndOpensItsConversation`:
  **OK (1 test)**, 101.671 s.
- Native iOS test linking and final Android instrumentation Kotlin compilation passed.
- Synapse audit: 23 encrypted synthetic Matrix events in the retained test database
  (21 from the preceding Android exercise, plus these two iOS messages), with
  **zero occurrences of the synthetic iOS plaintext** in Synapse event storage.
  Django reported 22 accepted deliveries overall, including these two new alerts.

An earlier complete attempt delivered the audible alert and resolved the correct
room, but its final visible-message assertion timed out. It is not counted as a
full pass; the repeated run above passed without a production code change. The
first iOS harness startup also timed out because its application lifetime captured
Main before the test dispatcher was installed; the test setup order was corrected.
These results establish a successful cross-platform run, not a claim of repeated
reliability across every lifecycle or notification-shade state.

## Isolation, release baseline and cleanup

Frontend runtime baseline: `0fb6bb1bd713b2b4221df039f3e3c486b9c2a2b2` on
`dev-core-architecture` (runtime identical to `a080d49`). Backend baseline and live
Windows deployment: `11891049ef323b6df3c6ee3c8f0688442b18c13f`, also from
`dev-core-architecture`. This follow-up adds test sources and validation records;
it changes no application or backend behavior. No Figma changes were needed.

The exercise ran on the isolated Windows Django/PostgreSQL/Synapse stack,
Compose project `srisu-matrix-push-e2e-20261030`, with its own database and Matrix
identity. The Mac used a pinned SSH loopback tunnel; Android used ADB reverse ports
8029/18008 over its paired Wi-Fi connection. The live `srisu` databases and personal
relationships were not used for test messages.

The Samsung already had a personal login, so a separate Android package,
`com.srisu.srisu.qa`, was registered in Firebase project `srisu-ef431` and built as
**SriSu QA**. Its Firebase config stayed private. Temporary local package/config/
label build edits were restored afterward; this was the same runtime source with
a separate application sandbox. The registered QA Firebase app remains available
for future isolated tests.

A preflight assertion initially included session data in its failure output.
That assertion was removed, and the affected normal session was renewed locally
through the normal refresh path; the account was retained. No credentials were
committed. Later test guards use boolean checks and never interpolate sessions.
Temporary UI diagnostics were removed from both source and retained artifacts.

After testing, the QA app and both instrumentation packages were removed. The
normal app's installed SHA-256 remained exactly
`72d3f65440c30393dc110ad517f2698f5f3637e8df35baa4ef1197ff8f94aa33`, and it was
reopened with its preserved account. Screen timeout and plugged-in awake settings
were restored and verified, and test reverse ports were removed. Temporary Django
sessions were revoked, synthetic push targets cleared, credential fixtures deleted,
the dedicated simulator deleted, and only the isolated containers stopped.
All seven live Windows deployment containers remained running.

## Repeating the check

The native sender is confined to `iosTest`; it requires a private fixture via
`SIMCTL_CHILD_SRISU_IOS_ANDROID_PUSH_FIXTURE`. Use a newly created dedicated simulator.
The Android companion is confined to `androidInstrumentedTest` and refuses any
package other than `com.srisu.srisu.qa` or a pre-existing login. Ordinary runs without
a fixture do not provide live coverage.

Both require `scope=isolated-matrix-push-e2e`, loopback API port 8029, synthetic
partner IDs 820000000000000001/2, a current room UUID, fresh private session values,
and a unique `run_id`. Never commit or print that fixture. Android receives it in
its QA app-private `files/ios-android-push-fixture.json`. Wait for iOS status `ready`
and Android stage `background-ready`, then write the matching run ID into the
sender's adjacent `ios-send.txt`. When Android finishes, create `ios-stop.txt` and
check both test results. Restore device settings, revoke temporary sessions,
remove the QA packages and private fixtures, and stop only the isolated services.
