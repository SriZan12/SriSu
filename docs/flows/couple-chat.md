# Couple chat implementation

For the subsequent conversation redesign, its design-access limitations and
validation, see [the Chat UI refinement record](couple-chat-ui.md).

Updated 2026-10-10. Working branch `dev-core-architecture-chat-system`, baseline
`fa7e4fa20a90d2e1289c80fc5552d2bf69e00dd5`. Backend same branch, baseline
`c68e7b068229cb57d37064bea903108f237b1251`. Publication is through paired draft
PRs against frontend `dev-new-theme` and backend `dev`; no deployment is included.

The backend `docs/couple-chat.md` owns the full implementation, threat model,
limits, rollout order and verified/remaining checks. Encrypted chat is wired into
the app and available for local testing and QA. Device acceptance and independent
security/license review remain prerequisites for release.

Feature boundary: `features/couplechat/{crypto,data,presentation}`; Android native
adapter `secureChatNative`; Swift adapter `iosApp/SecureChat`; platform media drivers
in androidMain and iosApp. Existing Ktor auth/session, Koin, theme, lifecycle and
typed Navigation are reused. `ChatNav.CoupleConversation` and `SharedMoment` carry
identifiers only. Home and owner Couple Profile enter the private conversation.

Text/media/voice, replies, desired reactions, deletion, protected offline queue,
nickname/privacy/safety controls, Ask/polls, server-gated Sparks, existing Plans and
Moment references are implemented in source. Foreground synchronization also updates
the unread Home entry. No notifications, calls, reminders or multi-device transport.

Challenges have no implemented domain. The user chose to keep references
unavailable for now. `ChatCard.Challenge(sourceId)` is a versioned E2EE reference,
not a URL or copied source snapshot. Future Challenge work should add authorized
source lookup, typed loading/unavailable states and exact-source navigation to its
card renderer. Deleting the card must never delete the Challenge. No transport or
local-message migration is required for the existing reference shape.

Plans are domain data, not E2EE: creating from chat requires explicit user action,
uses the existing Plans API and disables its legacy plaintext chat side effect.
The exact consented request ID/body and form drafts survive uncertain responses
and restarts. Plan cards reload on foreground. Reading anchors, reply selection,
Ask/Spark answer drafts and private clipboard selection are also implemented.
Moments are read from their original API, never copied into chat previews; leaving
or expiry clears the viewer's memory. Existing permissions and 24-hour expiry apply.

Native libsignal is pinned to 0.105.0. It is AGPLv3 and unsupported for external
integration; independent license/security review remains required before release.
Use the deployment/key lifecycle guidance in the backend document. Never claim
server ciphertext can restore history after the device keys are lost.

Figma Message page `1:5`; thread `184:2`, voice `184:1121`, Spark `184:1634`, Ask
`185:2`, Planner `185:781`, options `185:1624`. Screenshot/context access was quota-
blocked. Current UI extends existing theme components; exact visual matching has
not been verified.

Verification: full APK build and native media tests passed on Android; full iOS
app/media tests passed (including MP4 decoding both ways). The complete unit/render
run passed 141 with one existing skip (142 total). Actual KMP two-user repositories
passed on Android and iOS using disposable PostgreSQL, real HTTP/WebSockets and
native protected storage. The iOS run caught a readiness race during synchronization;
normal catch-up now preserves the verified session and the full test passes.
Backend regression: 220 selected tests passed on disposable PostgreSQL. The native
HTTP runner passed encryption/media transport, bidirectional QR comparison and
Spark reveal after 2,100 ordinary messages. Two additional Spark concurrency cases
passed in the 58-test focused backend suite. None of these alone proves the full
device/UI matrix; see the backend report for exact runs and remaining checks.
The final Android unit/APK/instrumentation/lint build passed; lint reports zero
errors and 121 warnings. Final native media/QR checks passed on Android (three)
and iOS (five); normal iOS builds exclude the synthetic evaluation harness.
The visible Android conversation also passed against the disposable backend:
send/reply/reaction, private nickname, Ask vote, unavailable Challenge and deletion.
Full iOS UI, cross-platform full-app UI, physical-device and release-performance
acceptance remain outstanding; native Android–iOS transport checks are distinct.

The Android native 10,000-message Debug benchmark passed correctness/bounded-page
checks but does not establish Release performance goals (cold page 1,027 ms,
protected enqueue 160 ms on the two-vCPU API35 emulator during builds). Physical
Release profiling, camera/QR capture and audio-route tests remain outstanding.

The iOS KMP integration entry point lives in `tools/chat-app-evaluation` and is
compiled only with `-PchatAppEvaluation=true`. Release tasks reject this property.
The normal framework excludes the harness. Synthetic sessions are supplied only
to XCTest/instrumentation via disposable private fixtures and removed afterwards.

Local Windows checkout is `D:/Djangos/SriSu/.cache/frontend-couple-chat`; set
`SRISU_BACKEND_DIR=D:/Djangos/SriSu` for workspace helpers. The original frontend
checkout remains untouched. Use JDK21 to build the native library, Java17 application
bytecode and compileSdk36. D:-based ignored Gradle/temp/test caches avoid this
machine's full C: drive; do not delete user files to make room.
Core desugaring is explicitly updated from 1.1.6 to 2.1.5 for compileSdk36 after
the lint compatibility error; unrelated dependency upgrades are not included.
