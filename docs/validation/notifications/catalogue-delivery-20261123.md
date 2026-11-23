# Complete activity catalogue — 23 November 2026

## Scope and source

Frontend: `SriZan12/SriSu`, existing primary checkout on
`codex/notification-previews`, based on `c12f990619ae9ea30e775c1162a6268e260cccea`.
Backend: `SrizanKhadka/SriSu`, matching branch, published implementation
`8585348ac1573394cd37960ae0fff1a769ce3457` (parent
`2f39a0f587c04295530cce3d63a8c1b936ab7de1`). These continue the rollout based on
`dev-core-architecture`; integration branches were not merged in this task.
The unrelated existing Xcode project edit was preserved byte-for-byte.
Figma `LztysD1YvINX7RwZpnhhTt`: existing settings layout reused with clearer copy;
no new screen, live design match, or Figma modification is claimed. Apple/APNs skipped.

## Root cause and correction

The running Windows database contained real `status.changed` and
`spark.submitted` events without deliveries. All current preference rows had empty
category overrides: six catalogue categories were disabled by product defaults,
not explicit user opt-outs. Challenge invitation already had a producer; no recent
Challenge invitation had been created for the tested couple.

All twelve activity categories now default on in Django and shared Kotlin. Explicit
stored false values, push/activity controls, chat mute, installation permission,
capability gates, session and resource authorization remain authoritative. No
preference rows were rewritten. No general historical backfill was added.

Removed the hourly status-delivery discard: each actual nonempty status change
can notify; repeated request IDs and unchanged text do not create duplicate events.
Status banners also appear on Home. Challenge progress copy distinguishes confirming,
pausing and resuming. Schema-2 activities no longer use a shared FCM collapse key.
High transport priority applies to eligible visible schema-2 alerts independently
of audibility; existing quiet channels and the shared sound budget remain intact.
Legacy schema-1 transport is unchanged. No migration or dependency change required.

## Automated checks executed

- Maintained SQLite allowlist: 441 tests, passed, 34 PostgreSQL-only skips.
- Disposable PostgreSQL 17 allowlist: all 441 passed, no skips; sockets restricted
  to the dedicated local database by the existing runner.
- Seven new cross-domain tests exercise actual status, Spark, challenge, Moment,
  heart, appreciation, reply and idea actions through worker acceptance, schema-2
  payload, authenticated private preview and tap resolution. They also cover all
  policy opt-outs, missing overrides, repeated status/retry behavior, private answer
  exclusion, unrelated-user denial and preferences changed before dispatch.
- Android JVM: 649 tests, zero failures/errors, four opt-in integration skips.
- Android APK assembly and shared iOS simulator compilation passed. This is compile
  compatibility only; Apple push/device behavior was not tested.
- Theme checks, Django system checks, migration drift and Git whitespace checks passed.

## Windows and physical Android

Windows fast-forwarded the matching backend branch to `8585348`, then restarted
web, notification worker and Matrix reconciler using existing bind mounts and
credentials. Docker volumes and unrelated media were preserved. The updated debug
APK was installed over the existing Realme RMX3511 application, preserving login.

Live domain actions used the normal Django services/API views under the current
couple accounts. They verify the real outbox/worker/FCM/Android receiving path;
they do not claim that every sender-side screen was driven in this task.

- Challenge invitation: specific challenge title, audible channel, actual tray tap
  opened that Challenge. Pause progress: specific action/title, quiet channel, tap
  opened the same Challenge in its current state.
- Status: two actual changes within one hour both reached Android with current
  text on the quiet channel; tapping the first opened Home with the matching status.
- Spark first answer: recovered one still-valid missed alert after rechecking current
  authorization. A separate targeted notification of that same still-pending question
  exercised background arrival/tap without changing any answer. The quiet alert
  opened the exact question (matched by fingerprint without logging its text), with
  the “Answer & reveal” action visible. Fresh start/submit/reveal producers were
  independently exercised with synthetic accounts in automated tests.
- Moment publication: a temporary private, labelled test Moment produced its exact
  caption on the quiet channel; tapping opened that Moment. The test Moment was
  deleted through the normal API afterward.
- Status, Spark and challenge-progress alerts were observed concurrently in the
  tray with their respective previews. Android's automatic group summary opens the
  app root; tests expanded the SriSu group and tapped individual child notifications
  to verify destination routing.

No fresh acoustic confirmation is claimed: Android channel selection was inspected;
actual sound was confirmed by the user in the preceding chat-flow task. Status,
Spark and progress notifications intentionally remain quiet. The AOSP emulator lacks
Google Play Services and cannot be used as an FCM receiving device.

Cleanup restored the original status and its original expiry, ended only the test
Challenge, and deleted only the labelled test Moment. Conditional revision checks
protected concurrent user edits. The existing shared idea was left untouched, so
idea arrival/taps and hearts/appreciations/replies have automated integration
coverage here, not a fresh physical-device claim. Existing Spark answers were not
read, changed, submitted, skipped or revealed by the live checks.
