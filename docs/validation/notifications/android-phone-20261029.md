# Physical Android notification verification — 29 October 2026

The Windows Docker sender was tested against the user's USB-connected Realme
RMX3511, Android 13 / API 33. Apple enrollment and APNs remained deferred.
This record distinguishes provider acceptance, device observations and the user's
audible confirmation. It does not equate an FCM success response with delivery.

## Versions and isolation

- Frontend application code: `56a5896` on `dev-core-architecture`. The Windows
  checkout was on `codex/firebase-notification-test-kit` at `ed9b215`, whose tree
  is identical to that merge. The ignored registered Firebase configuration was
  present in the installed Windows debug build.
  The retained APK's SHA-256 is
  `1bd8c3b3d148c06cdaf4f0e75f55fe4dbc0223f20c099afe8ea14cefaae7e236`.
- Backend: `5fb1237` on `dev-core-architecture`, already deployed on Windows.
  The web, notification worker, Matrix reconciler, Synapse, databases and Redis
  containers were running; the worker and Synapse reported healthy.
- Test-kit fix: `6d04eb0`. No production application code, backend code, API,
  schema or deployment image was changed for these tests.
- Only the connected phone's native registered FID was used, transferred through
  private device storage and process stdin without printing it. The sender checked
  the matching current backend binding, permission, session and push preferences.
- A separate temporary instrumentation APK observed only SriSu QA notifications
  and fixed UI labels and invoked their real `PendingIntent` tap actions. The
  signed-in account, app data and domain records were preserved. No synthetic
  invitation, Plan, Spark or sharing record was inserted into the live database.

## Fix found during execution

The original kit looked up `SHA-256(fid)`, while backend
`notifications.services.register` persists `SHA-256("fid:" + fid)`.
This caused a valid device to be rejected before sending. The lookup now matches
the backend contract and retains its permission/session/preference checks. Seven
kit tests passed. The new regression test also failed as expected when run against
the previous lookup. Python compilation and Git whitespace checks passed.

## Device observations

The phone had notification permission, normal ringer mode, DND off and notification
volume **1/16**. These sound settings were not changed. Each observed background
alert had the generic SriSu title/body, private visibility and a content intent.

| Check | Observed result |
| --- | --- |
| Audible background alert | Arrived on `srisu_updates`, importance 3, sound enabled. The user explicitly confirmed hearing it. A controlled repeat's actual tap opened SriSu Home. |
| Quiet background alert | Arrived on `srisu_updates_quiet`, importance 2, sound disabled. Its actual tap opened SriSu Home. |
| Unknown delivery | Arrived; its actual tap opened SriSu and displayed “This update is no longer available.” |
| Expired payload | Background alert arrived; its actual tap returned to Home without routing to private content. |
| Unsupported schema | Arrived; a controlled repeat's actual tap opened Home without routing to private content. An earlier attempt overlapped with phone use and was excluded. |
| Foreground observations | Audible, quiet, expired, unsupported-schema, unknown-delivery and data-only sends produced no OS alert or unexpected in-app banner during each eight-second observation window. |
| Data-only background observation | No visible OS alert after the accepted send. This probe has no recognized app delivery payload; absence of an alert does **not** independently prove data receipt. |
| Offline TTL | Sent with Wi-Fi and mobile data disabled, then held offline for 75 seconds after provider acceptance. No expired alert appeared in 30 seconds after restoring connectivity. A fresh 60-second-TTL message arrived and its actual tap opened Home. |

Wi-Fi and mobile data were restored to their original enabled states. The probe
cleared only its own QA alerts, then its private target/command files and temporary
instrumentation package were removed. The normal app was relaunched with its
existing account and data intact. Registration targets, credentials and personal
content are omitted from this record and from Git.

## Coverage boundaries

These direct FCM probes bypass the domain outbox and its 30-second sound budget.
They verify generic opening and negative payload/tap guards, not successful
navigation into every real Plan, Spark, invitation or sharing event. Existing
isolated-emulator domain verification is documented separately in
[the notification flow](../../flows/notifications.md); it is not new physical-phone
coverage. Matrix chat push remains unimplemented. Local Plan reminders and Apple
notifications were not exercised in this run.

Foreground and data-only rows are bounded UI observations. In particular, a silent
data-only send has no visible receipt acknowledgement; its end-to-end data arrival
was not independently proven. The speaker confirmation applies to the audible
case. The quiet case was verified through the delivered Android channel's null
sound and low importance, without a separate microphone recording.
