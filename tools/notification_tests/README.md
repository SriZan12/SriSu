# SriSu notification experiments

These are repeatable, device-only Firebase Cloud Messaging tests using the existing
Google credentials inside the Windows notification worker. No browser login is
needed. They are direct API tests, not saved Firebase Console campaigns or Analytics
A/B experiments. Analytics remains disabled and Apple/APNs setup remains deferred.

Reference implementation: frontend `1e13650` and backend `5fb1237`, both on
`dev-core-architecture`. The kit changes no application API, database schema,
account, preference or domain record. It copies its runner into the worker's `/tmp`
directory on each run; it does not rebuild/restart the deployment.

## Start on the Windows laptop

Double-click `D:\SriSu-tools\notification-tests\StartNotificationTests.cmd`.
Docker Desktop and the existing `srisu-notification_worker-1` container must be
running. Python 3 is already installed on this laptop; its Firebase SDK comes from
the existing container, so the host needs no new Python packages.

Choose **8** first to validate all seven payloads and the current Google credentials.
This uses an existing active Android registration entirely inside the Docker worker.
Firebase requires a registered ID even for validation-only requests. No target is
displayed/exported, and this mode cannot send notifications or prove device delivery.

To send a test, open the current Android debug app, sign in, allow notifications and
leave Home open briefly so its registration reaches the backend. Enable **Push
notifications** and **Invitations & shared activity** in notification settings.
Choose a numbered test and paste **Home → Copy Firebase ID** into the hidden Windows
prompt. Use the app's registered messaging ID; a raw Firebase Installations SDK
record can differ and is not a substitute. The ID is held in memory, passed to Docker through stdin,
and never printed, saved to a file or added to shell history. Copy a fresh ID after
logout/reinstall/registration changes. A legacy FCM token is not the 22-character
FID used by this app.

The sender checks that the chosen ID belongs to a current, permitted Android
installation and live session, and that account push/activity preferences allow
alerts. It refuses stale, signed-out, unregistered or disabled targets. Only one
selected device is addressed; there is no topic or audience/broadcast mode. Keep
test IDs and credentials out of chat, Git and screenshots.

The lookup must use the backend's `SHA-256("fid:" + registered_id)` target digest.
The original kit hashed only the ID, which incorrectly rejected registered phones.
The physical Android run on 29 October caught this mismatch; the corrected kit
retains all session, permission and preference checks. Its regression test fails
against the previous lookup and passes with the correction.

Command-line equivalents, run from the kit folder:

```powershell
py -3 run.py --list
py -3 run.py --check-config
py -3 run.py --case audible
py -3 run.py --case audible --send
```

`--case` alone validates the selected device/payload without sending. `--send`
explicitly sends one chosen case. Each result distinguishes provider acceptance
from phone delivery; sound/visibility/tap results must be observed on the phone.

## Seven Firebase transport experiments

All visible payloads use the same private, generic title/body as SriSu's real
notifications. Run one experiment at a time, clearing previous QA alerts between
cases. Repeat sends of the same case replace its previous QA notification tag.
Test tags/collapse keys are separate from normal app activity groups.

| Menu / case | Prepare and run | Expected result |
| --- | --- | --- |
| 1 — `audible` | Press Android Home, then send. Also repeat with screen locked and after dismissing SriSu from Recents. | Generic OS notification on `srisu_updates`. Sound follows the user's channel volume and DND settings. Tap opens SriSu without a specific private destination. Dismissing Recents differs from force-stop. |
| 2 — `quiet` | App in background; send. | Generic OS notification on `srisu_updates_quiet`, without sound. |
| 3 — `short_ttl` | Copy the ID while online, background SriSu, then disable the phone's network. Send and keep it offline for more than 60 seconds before reconnecting. Repeat a separate send with reconnection well before 60 seconds. | The first message should expire before reconnecting; the second is eligible for delivery. Network/OS delays can still prevent it. The provider TTL is separate from the app's payload expiry. |
| 4 — `data_only` | Send once with Home visible, once with the app backgrounded. | No OS notification or SriSu banner. This probe has no alert or recognized app delivery data. An absent UI alert alone does not prove data arrival. |
| 5 — `expired_payload` | Background app, send, then tap. Repeat foregrounded. | Background Android may display the alert because the FCM TTL is live; the app ignores the expired custom payload and does not route to private content. Foreground: no banner. |
| 6 — `unknown_delivery` | Keep backend reachable. Background app, send, tap while signed in. Also repeat foregrounded. | The payload contains a fresh synthetic UUID, not a created backend delivery. Tap produces the neutral unavailable result. Foreground: the 404 is consumed without an alert. |
| 7 — `invalid_schema` | Background app, send, tap. Repeat foregrounded. | A background OS alert may appear, but the unsupported app schema is ignored. Foreground: no banner or navigation. |

The direct Firebase cases do not run SriSu's outbox, domain authorization or
30-second sound budget. Use real domain actions below for those behaviors. A title
such as "Plan created" or a made-up `type` field would not create a real Plan alert.

## Six actual app-notification experiments

Use two disposable test accounts **A** and **B** on registered Android clients
(a second phone or the dedicated emulator), plus unrelated account **C** for
authorization checks. Register the recipient before triggering the event. Keep
the Windows backend/worker running and both clients connected to it. An iOS client
can initiate domain actions, but receiving iOS push remains outside current coverage.

For each row: start with the recipient backgrounded, trigger from the other account,
verify generic lock-screen copy, then tap and verify the destination. Wait more than
30 seconds between tests when expecting sound. Use fresh resources for foreground
repeats so an old delivery/idempotency key does not suppress a new event.

| Event | Trigger | Recipient and correct tap destination |
| --- | --- | --- |
| `invite.received` | With A/B not linked yet, A sends B a new partner invitation. | B → Partner requests while that invitation is pending. Rejecting/cancelling it makes the old tap unavailable. |
| `invite.accepted` | B accepts that pending invitation once. | A → the current couple space. Repeating the accepted operation must not generate a second event. |
| `plan.created` | A creates a new future Plan. | B → that Plan. Cancelling it before tapping makes the old delivery unavailable. |
| `plan.responded` | B accepts/declines or meaningfully proposes a change to a future Plan. Also cover withdrawal of a proposed change. | The other current member → that Plan. An exact retry/no meaningful state change must not create another alert; an obsolete revision must not authorize an old tap. |
| `spark.revealed` | A submits first and waits; B submits the second encrypted answer. | A → the mutually revealed Spark. Creating a Spark or submitting only the first answer must not trigger this reveal alert. |
| `sharing.proposed` | A proposes a changed, nonempty set of couple-profile sharing sections. | B → Sharing review. Changing/revoking/approving the proposal before tapping must not open an obsolete proposal. Proposing is separate from both partners' publication consent. |

For foreground behavior, repeat from a different screen: a valid real delivery
offers the existing in-app snackbar with **Open**, without a separate OS alert.
Repeat while already viewing the relevant Plan/Spark/couple destination: the
duplicate banner is suppressed. A generic Firebase-console/API message without a
real authorized delivery cannot test this positive path.

## Additional checks across those real events

| Behavior | Experiment / pass condition |
| --- | --- |
| Sound budget and grouping | Trigger two distinct eligible events within 30 seconds for the same recipient installation. The first can be audible; the next uses the quiet channel. Existing OS activity grouping may replace a prior visible alert. |
| Account preferences | Disable Push notifications, then trigger a fresh event; repeat with only Invitations & shared activity disabled. No new domain alert should be sent. Re-enable and trigger a new event; suppressed history is not replayed. Direct Firebase console sends bypass these server preferences; this kit refuses disabled targets. |
| OS permission / DND | Turn notifications off in Android settings and trigger a fresh event; no visible OS alert. Restore permission and reopen SriSu to reconcile. Repeat an audible case under DND; the app must respect Android settings. |
| Logout / account switch | On a disposable client, log out or switch from B to C. Trigger a new event intended for B. No new B alert should be sent to C's binding; an already accepted generic OS alert may race with logout, but cannot authorize C to open B's content. |
| Stale or unrelated taps | Cancel/reject/change a disposable resource after delivery but before tap. Expect the neutral unavailable state. A delivery owned by B must not resolve under C. Use the existing isolated instrumentation test for cross-user request checks; do not export real delivery IDs. |
| Network failure on tap | Receive a valid domain alert, make the backend unreachable from the phone, and tap. Expect the retry message. Restore connectivity, retry, and reauthorize before navigation. |
| Duplicate submission / retry | Repeat an identical idempotent Plan operation or lost-response retry. It must not produce duplicate domain events. No duplicate navigation for the same consumed delivery in the current app process. |
| Offline/terminated changes | Accept that queued OS notifications can outlive app state; tap authorization is authoritative. Android force-stop can block delivery until manually reopening; it is not equivalent to dismissing Recents. |

## Local Plan reminders

Plan reminders are scheduled on the device, not Firebase. On a disposable future
Plan, explicitly select the 15-minute reminder (for example, start time 20 minutes
ahead), grant notification permission, then background the app. Expect a generic
reminder and an authorized Plan on tap. Android alarms are inexact. Cancel the Plan
and sync, or log out, to test cancellation. If the app is terminated/offline, it
cannot learn a partner's change until sync, so an old generic reminder may still
appear; its tap must fetch current state.

## Current exclusions and evidence

On 29 October 2026, all seven payloads were accepted by Firebase's validation-only
API from the Windows Docker worker using an existing registered Android target.
No notifications were sent by that validation. Six isolated helper tests passed;
the installed launcher and case listing were also checked. Phone sound/visibility
and the manual domain experiments remain to be observed when you run them.

Matrix chat-message push, reactions, typing/read receipts, routine Moments and
incomplete Challenge alerts have no current push producer. No experiment here adds
those features. Apple/APNs and measured Analytics A/B experiments are not configured
by this kit.

`test_run.py` covers payload/channel construction, explicit-send gating, denied
bindings, validation-only gating, malformed requests and keeping IDs out of process
arguments/output. Run it using the backend virtualenv (which already
has the pinned Firebase Admin SDK):

```sh
../SriSu-backend-consolidated-20261029/.venv/bin/python -m unittest discover -s tools/notification_tests -p 'test_*.py' -v
```

Record manual results as case, app state, visible alert, sound, tap destination and
pass/fail; omit IDs, credentials and private content. Firebase `accepted` is not
the same as device delivery. See the existing [notification integration evidence](../../docs/flows/notifications.md)
for prior isolated Android/backend coverage; it is not a claim these manual rows
have just been rerun.

References: [Firebase console payload behavior](https://firebase.google.com/docs/cloud-messaging/send/firebase-console),
[message lifetime](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan),
and [Analytics requirements for A/B experiments](https://firebase.google.com/docs/ab-testing/abtest-with-console).
