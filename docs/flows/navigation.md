# Navigation migration and dating retirement

Implementation date: 2026-10-01. Follow-up authorization: publish on
`codex/modular-navigation-retire-singles`, targeting `dev-core-architecture` in both repositories.

| Repository | Working branch | Starting HEAD |
|---|---|---|
| SriZan12/SriSu | dev-core-architecture | 00a198a3e579d32eeb578942bd2c86936257e2c5 |
| SrizanKhadka/SriSu | dev-core-architecture | 6433d60556d0c55e1ae06a1848e788c0fa487126 |

Both checkouts were clean initially. Live integration refs were verified separately:
frontend dev-new-theme `3fa6822ea05578d64cca8891c5e3fdb0a831897a`, backend dev
`ecc8592307114e7d154bda9612e6826e2ca8ba90`. Neither baseline was switched, reset,
merged or rebased. See [ADR 001](../architecture/decisions/001-navigation.md).

## Route migration inventory

| Previous entry | Owner/access | New contract/incoming action | Back/result/lifetime |
|---|---|---|---|
| AppRoot | App / resolved startup | BaseNavigation; actual App entry | State-driven security scope, no competing root |
| Onboarding, Space, Guest | Auth / signed out | Existing startup destinations | Persisted introduction; callbacks through startup |
| PhoneNumberScreen | Auth / signed out | AuthNavigation.Flow → PhoneNumberScreen | Leaving returns to recorded introduction |
| OTP | Auth / active challenge | PhoneNumberVerificationScreen, no OTP/token route args | One challenge-driven Back; flow VM |
| ProfileSetUp | Auth / incomplete profile | Name → gender → optional photo | Local wizard, server-derived resumption; obsolete ArrayDeque removed |
| Home | Home / complete individual profile | HomeNavigation.Home | Root, shared shell |
| Suggestions, filter, suggestion profile | Retired dating | Removed; no redirect to invitation acceptance | Old state namespace ignored |
| Explore tab | Couple discovery / authenticated | HomeNavigation.Explore via coupleProfileGraph | Existing public/permitted couple feed, individual Faves |
| Crushes / SingleConnection / JSON Profile | Retired dating | Removed | No executable dating network calls |
| Matches tab / LoveRequestScreen | Partner invitations / authenticated incl. unlinked | ChatNav.PartnerRequests, Home button | Existing sent/received lists retained under partner flow |
| FindPartner | Partner / authenticated incl. unlinked | ChatNav.PartnerFlow → FindPartnerScreen | Flow-scoped FindPartnerVM; phone lookup preserved |
| InviteSent, RequestReceived, YoureConnected | Partner / same flow | Typed object destinations | Finish child flow on acceptance; return to Home; stale callbacks ignored |
| ConnectionNav.Profile(JSON) from invitations | Partner preview / authorized existing flow | PartnerPreview(userId), ephemeral authorized preview in flow VM | No JSON route; recovery after missing flow data |
| ChatRoomScreen | Chat / authenticated | Typed room list route from Home | Entry VM; choose stable room ID |
| ChatScreen (no argument) | Chat / server-authorized room | ChatScreen(roomId), room list or safe external link | Distinct entry per room, no first-room fallback; scoped drafts |
| CoupleProfile(id,page:String,planId) | Couple Profile / explicit server capabilities | CoupleProfileDestination(id, section:CoupleSection, planId) | Entry VM; local section draft/Back/save; server persistence |
| Story, answers, song, interests, cover/position, date, sharing, plans, plan editor | Couple Profile / member or permitted visitor | Existing separate editors; public typed section intents | Existing confirmation modals stay local; no UI-only authorization |
| EditProfile | Personal account / authenticated | ProfileNav.EditProfile | Entry VM, existing persistence |
| InterestScreen(JSON catalogue/profile) | Personal account picker / same requester | InterestScreen(requester, requestId) | Bounded typed SavedStateHandle result; consume once; cancel without applying |
| Deep links/notification taps | App / gated | Strict custom-scheme parser + one pending target | Account/session-bound, deduplicated, no write side effects |

No implemented Moments/Sparks/Challenges screen graph existed outside Couple Profile
and the couple discovery/content flow. No placeholder destinations were manufactured.
Older personal DOB/zodiac/relationship registration screens had no call sites and
were removed with their unused wizard navigation. Personal fields/models/APIs remain.

## Backend retirement and compatibility

- `/api/social/connect-single/**`, `single-connection/**`, `user-suggestions/**`,
  `get-suggestion-profile/**` return authenticated HTTP **410**, with core-1
  `feature_retired` (not `cursor_expired`); legacy clients retain their normal error envelope.
- Removed dating ViewSets/discovery handlers, serializers, scorer/repository/constants
  and unregistered old chat sender/deleter/backup. Old dating seed command names now
  fail without mutating data.
- Active HTTP chat/history, WebSocket subscription/mutations/delivery share current
  couple authorization. A dating-only room no longer grants access. Its records and
  messages remain in the database, **not presented as an active or read-only archive**.
  A separately authorized archive/export product would need its own access policy.
- Historical SingleConnectionModel, foreign keys, migrations and BLOCKED records stay.
  Existing couple feed/Moments blocking rules continue to use those records. No user,
  relationship, message or preference table was deleted or backfilled.
- Personal preferences are preserved; the previously broad list is now owner-scoped.
  Individual profiles, unlinked accounts, invitations, Faves, Moments and couple
  publication consent retain their contracts.
- `contracts/core-1/routes.json` distinguishes current client calls, retired paths,
  and preserved legacy preferences. Backend is authoritative; frontend snapshot and
  generated fixtures were refreshed together. This remains additive for retained calls.

Roll out the backend retirement and new client together with an update notice for
old dating clients. Backend-first is compatible with retained auth/couple/chat calls;
old dating actions intentionally stop with 410. There are no new migrations and no
production/server changes were performed. Pushing would not constitute deployment.

## Design and platform scope

Existing shell components/theme/icons are reused. Figma page `1:4` was re-requested;
the connector returned its Starter-plan limit. The supplied Couple UI final board and
existing implementation remain references; no new frame fidelity or modified Figma
is claimed. Tab removal is product retirement, not a new visual design.

Back gestures, cancelled predictive gestures, keyboard/safe areas, accessibility and
large-text behavior on physical devices require manual validation. Existing rendered
Couple Profile tests continue; native unit execution does not establish gesture UX.

## Validation record

Baseline: Android unit tests and iOS shared compilation passed; backend 158 tests passed
with 11 SQLite skips. Final outcomes are below. Tests use synthetic accounts and
isolated databases only.

Initial migration compilation exposed stale imports/callback signatures; fixed.
A new membership test initially used an incomplete-profile fixture, correctly remaining
outside MAIN; changed to a complete synthetic profile. The initial Native link command
could not find the simulator SDK under default xcrun; command-local DEVELOPER_DIR fixed
it. No tests/permissions were disabled to obtain a pass.

### Final checks (2026-10-01)

Gradle commands used `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
Native/Xcode commands additionally used `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

| Command / check | Actual outcome |
|---|---|
| `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug` | **Passed**: 108 tests, 0 failures/errors, 1 skipped; Android APK built. The opt-in transport integration is skipped without its temporary server and was run separately below. |
| `./gradlew :composeApp:iosSimulatorArm64Test` | **Passed**: 84 tests, no failures/errors/skips; concrete typed routes execute on Kotlin/Native. |
| `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/srisu-navigation-xcode CODE_SIGNING_ALLOWED=NO build` | **Passed**: actual iOS app including Swift URL adapter. Built Debug plist contains the `srisu` scheme. |
| Backend `.venv/bin/python tools/auth_postgres_tests.py --postgres-bin /opt/homebrew/opt/postgresql@17/bin` | **Passed**: 163 tests, no skips, disposable PostgreSQL database. |
| Backend `.venv/bin/python tools/workspace.py check` | **Passed**: Django system checks. |
| Backend `.venv/bin/python tools/workspace.py migrations` | **Passed**: no model changes requiring migrations. No production migration applied. |
| Backend `.venv/bin/python tools/check_core_contracts.py` | **Passed**: schema and 15 shared fixtures. |
| `python3 tools/core_contracts.py --backend ../SriSu-backend` | **Passed**: paired snapshots/fixtures match. |
| `python3 tools/core_integration.py --backend ../SriSu-backend` | **Passed**: real KMP–Django loopback HTTP/WebSocket integration; temporary server and credentials removed. |
| `python3 tools/theme/check_theme.py` | **Passed**: 96 Figma colors, 24 M3 role pairs, required contrast checks. |
| `git diff --check` in both repositories | **Passed**. |
| Manual Android/iOS gestures, predictive-back cancellation, accessibility, keyboard and safe-area UX | **Not run** on physical devices. Build/unit success does not establish these behaviors. |
| Figma live frame inspection | **Blocked** by connector Starter-plan call limit. |
| Push-provider delivery / verified HTTPS links | **Not run**: provider/domain setup is absent; only custom-scheme adapters and shared entry policy are implemented. |

Core contract digest: `4832be95e15b6c44d642fe1a4a5481b500f76e9a92ce7e69d4b34a2900e892d0`.
Navigation tests cover duplicate/late callbacks, distinct resource entries, tab state,
entry VM disposal, library restoration, scoped picker results, external delivery,
same-account session replacement, membership recovery and chat isolation. Backend
coverage includes retirement errors, preserved historical records, rejected dating-room
access and personal-preference ownership. This is not a claim of every journey being
manually exercised against a deployed backend.

The new feature branches are based on the starting HEADs above. PRs target
`dev-core-architecture`; the integration branches are not modified. Publication
does not deploy the changes. No deployment or production migration was performed.
