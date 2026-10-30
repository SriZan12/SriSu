# App closed and phone locked — 30 October 2026

**Passed on the physical Samsung SM-A075F (Android API 36), with an iPhone 17
simulator running native iOS 26.5 as the sender.** Apple enrollment and APNs remain
skipped. This extends the [earlier iOS → Android test](ios-to-android-20261030.md).

## Observed sequence

Times below are Nepal time, 30 October 2026.

| Time | Verified observation |
| --- | --- |
| 20:18:56 | QA application had no running process and no task in Recents. Android reported `stopped=false`. The keyguard was locked; Samsung reported display wakefulness `Dozing`. |
| 20:19:09 | The native iOS composer ViewModel sent the unique synthetic message through real encrypted Matrix. |
| 20:19:20 | Android listed the received QA notification while the keyguard was still locked. Its record used the audible `srisu_updates` channel. |
| After arrival | The user confirmed **“Heard it and unlocked.”** The agent did not request, enter, or bypass the PIN. |
| 20:23:08 | The host tapped the original notification's title in Android System UI using its accessibility bounds. No substitute app launch or synthetic notification intent was used. |
| 20:23:16 | MainActivity displayed the exact decrypted iOS message in the intended chat, with an enabled composer. The host recorded `PASS`. |

“Closed” here means the activity called `finishAndRemoveTask()`, instrumentation
finished and was uninstalled, and `am kill` ended the remaining background app
process. Both process absence and absence from Recents were asserted before the
send. This check does not cover Settings → Force stop, reboot, or prolonged idle/
battery-restriction scenarios. There was no test instrumentation running inside
the receiving app during notification arrival or the actual notification tap.

## Evidence and test corrections

- `ClosedLockedPushPreparationTest.prepareReceiverThenRemoveItsTask` passed on the
  phone. It registered the native Firebase installation, opted into Matrix push,
  prepared the encrypted Matrix store, persisted only the synthetic session, and
  removed its task. It also verified sound enabled, sufficient channel importance,
  normal ringer mode, nonzero notification volume and no DND restriction.
- The host separately proved process/task absence, locked arrival, the audible
  notification channel, actual System UI interaction and the resulting chat UI.
  Conversation assertions were limited to the QA app's accessibility nodes.
  Raw UI trees were neither printed nor retained. Screenshot protection was not
  disabled or bypassed.
- `IosToAndroidPushLiveTest.nativeIosMessageReachesAndroid`: **1 passed**, 409.599 s
  including coordination and the time spent inspecting the original alert.
- The Windows QA application and instrumentation build passed. No application or
  backend runtime fix was needed.
- Synapse contained 25 encrypted synthetic Matrix events after this exercise,
  compared with 23 before it, and zero occurrences of the synthetic iOS plaintext.
  Django reported 24 accepted deliveries overall. One obsolete QA installation
  from a removed test app was retired as unregistered.

An initial preparation-only host check expected `Asleep`; Samsung correctly
reported a locked `Dozing` state, so that check was corrected before sending.
The first locked message arrived, but inspection stopped because the notification
key's shell separators were not quoted. That command was corrected for the repeat.
The repeated alert then remained available after unlocking. Samsung shortened its
body in accessibility, so the host selected its title instead, after asserting
there were no personal SriSu alerts that could make the selection ambiguous.
The final tap and message assertions used that same repeated alert; no third
message was sent to replace it. Earlier partial attempts are not counted as full
passes.

## Source baseline, isolation and cleanup

Frontend runtime baseline: `3c5c3a62ac416f6beebd74f660a8da252abdd7fc` on
`dev-core-architecture`. Backend and live Windows deployment:
`11891049ef323b6df3c6ee3c8f0688442b18c13f` on the same selected release line.
Only test code and validation documentation changed in this follow-up.

The phone used the separate `com.srisu.srisu.qa` installation and the existing
registered Firebase QA app. Messages used synthetic accounts in the isolated
Windows Compose project `srisu-matrix-push-e2e-20261030`, with independent Django
and Matrix databases. The sender used a dedicated simulator and SSH loopback
tunnel. Android used paired Wireless debugging and ADB reverse ports 8029/18008.
The normal app's account and personal relationships were not used for messages.

The guarded host driver and its nonsecret observations are retained privately on
Windows as `run-locked.py`, `locked-events.jsonl`, `locked-status.json` and
`locked-preparation.log` under `D:\SriSu-secrets\matrix-push-e2e-20261030`.
The driver requires the separate QA APK IDs and the synthetic fixture. Its final
System UI selection refuses ambiguity with personal SriSu notifications. The
preparation test alone is not proof of closed/locked delivery.

After verification, the QA app/instrumentation were uninstalled, the original
screen timeout was restored, and the original awake setting was verified unchanged.
Test reverse ports and private credential fixtures were removed, temporary Django
sessions revoked, and synthetic push targets cleared. The dedicated simulator and
SSH tunnel were removed; only the isolated containers were stopped.

The normal app's installed SHA-256 remained
`72d3f65440c30393dc110ad517f2698f5f3637e8df35baa4ef1197ff8f94aa33` and it was
reopened with its existing account. All seven live Windows backend containers
remained running.
