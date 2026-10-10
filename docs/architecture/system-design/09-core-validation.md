# Core implementation validation — 2026-09-26

This records checks actually run for [the core implementation](08-core-implementation.md),
not completion of every future acceptance requirement in the original design.
Both repositories remain on local `dev-core-architecture` branches, with their
starting commits unchanged. No production database, credentials, SMS, deployment,
commit, push, merge or Figma mutation was used.

## Baseline and final results

Environment: macOS, Android Studio bundled JBR, Kotlin 2.2.0 / Ktor 3.2.3;
backend `.venv` Python 3.13.5 / Django 6.0.3 / Channels 4.3.2. Test settings use
synthetic accounts, isolated SQLite and in-memory Channels. Native host SQLite
tests use Robolectric. Full Xcode is not selected; no Android device was attached.

Commands below run from the indicated repository. For direct Gradle/paired runs:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
```

| Check | Actual result | Coverage limit |
| --- | --- | --- |
| Before changes: frontend `python3 tools/workspace.py verify all` | **Passed.** Existing frontend tests/Android/iOS compilation tasks succeeded, with cached Gradle tasks; backend 77 discovered, 72 passed, 5 skipped | Historical baseline, not proof of fresh native execution |
| After implementation: frontend `python3 tools/workspace.py verify all` | **Passed.** Paired contract equality, theme, selected KMP checks and backend checks/tests | Final engine-close adjustment was additionally covered by the later direct build and paired run below |
| Frontend `./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 :composeApp:lintDebug --console=plain` | **Passed.** BUILD SUCCESSFUL, 14s, 50 tasks; full unit suite 41 tests: 40 passed, 1 conditional paired test skipped. Android compilation also ran as a dependency | Shared iOS compilation is not an iOS binary link or runtime test |
| Android lint in the command above | **Passed with warnings.** 0 errors, 97 warnings | Warning count was not classified into existing versus new issues; this is not a warning-free claim |
| Frontend `python3 tools/core_integration.py` | **Passed separately.** One real HTTP/WebSocket/Room integration test; Gradle 4s | Host Robolectric/OkHttp to loopback Daphne, isolated SQLite/in-memory Channels; not a device or distributed deployment |
| Backend `.venv/bin/python tools/workspace.py test` | **Passed.** 95 discovered, 90 passed, 5 PostgreSQL-only cases skipped; 2.314s | Five baseline concurrency cases still require disposable PostgreSQL |
| Backend `.venv/bin/python tools/workspace.py check` | **Passed.** No system-check issues | Not a production deployment audit |
| Backend `.venv/bin/python tools/workspace.py migrations` | **Passed.** No changes detected | No existing database migration was needed or applied |
| Backend `.venv/bin/python -m pip check` | **Passed.** No broken requirements | Dependency consistency, not a vulnerability scan |
| Frontend `python3 tools/core_contracts.py --backend ../SriSu-backend` | **Passed.** Backend authority, pinned client copy and generated fixtures agree | Implemented `core-1` subset; not all legacy APIs |
| Backend `.venv/bin/python tools/check_core_contracts.py` | **Passed.** Schema and all 9 shared fixtures validated | Actual representative responses/events are also checked by backend tests |
| Frontend `python3 tools/theme/check_theme.py` | **Passed.** 96 documented colors, 24 contrast pairs, minimum about 4.75 | No live Figma approval or device accessibility test |
| Frontend `plutil -lint iosApp/iosApp/Info.plist iosApp/iosApp/Info.Debug.plist` | **Passed.** Both plists valid | ATS and Keychain behavior still require native testing |
| Frontend `./gradlew :composeApp:validateReleaseEnvironment` with default development configuration | **Expected rejection.** Gradle exit 1: release requires staging/production HTTPS | Negative configuration test passed; this command intentionally fails |
| Same task with `-Psrisu.environment=staging -Psrisu.apiBaseUrl=https://api.example.invalid/` | **Passed.** Valid HTTPS release configuration accepted | No connection to that illustrative origin; no release packaging/signing |
| `git diff --check` in both repositories | **Passed** | Whitespace check only |

The backend suite deliberately injects storage/cache/publication failures. Its
`OSError: offline` and fixed `socket_publication_unavailable` diagnostics accompany
passing assertions; they are not live incidents. Unrestricted backend discovery
was not run because the legacy chat test opens transport on import.

The paired runner creates a private temporary fixture containing a short-lived
synthetic token, starts its loopback server, and removes the server/credentials in
`finally`. Tokens are not printed or checked in. It verifies this actual sequence:

1. Ktor HTTP fetches the public catalogue from Django.
2. The repository writes/reads the native SQLite Room cache.
3. Ktor OkHttp opens the authenticated Channels socket.
4. A chat command receives its correlated acknowledgment after persistence.
5. HTTP history returns the persisted message; logout clears private projection.

## Concrete guarantee coverage

KMP tests are in `composeApp/src/commonTest/kotlin/com/srisu/srisu/core/` and
`composeApp/src/androidUnitTest/kotlin/com/srisu/srisu/core/`.
Backend foundation tests are in `srisu/test_core.py`; existing selected Social
suites continue to run.

| Guarantee | Executable evidence |
| --- | --- |
| HTTP success, empty body, pagination, malformed response, typed status/field errors | `HttpFoundationTest`, `CoreContractTest`; backend core response/schema tests |
| Coroutine cancellation, logout during request, account change between retries | `HttpFoundationTest`, `CatalogueFoundationTest` |
| GET retry bound, no unsafe-write replay, no credentials to another origin/redirect | `HttpFoundationTest` |
| Socket foreground/logout cleanup, bounded reconnect, terminal denial | `SocketFoundationTest`; backend authentication/watchdog/subscription tests |
| Ack ambiguity, duplicate/unknown/malformed event handling | `SocketFoundationTest`; backend command/limit/publication-outage tests |
| Old room/account cannot update the current projection | `ChatIsolationTest`; backend unrelated-account and membership-revocation tests |
| Public-cache freshness, expiry, offline policy, no authorization-error fallback | `CatalogueFoundationTest`; backend cache scope/expiry/outage tests |
| Post-commit invalidation and stale-loader protection | Backend catalogue invalidation and late-loader tests |
| Durable loading/empty/offline state and cancellation of replaced loads | `CatalogueFoundationTest.presentationKeepsDurableOfflineAndEmptyStatesAndCancelsReplacedLoad` |
| Room transaction/reopen and refusal of destructive migration | `CatalogueDatabaseTest` uses actual SQLite; unknown newer schema fails while retaining rows |
| Backend schema to KMP parsing and real transport | Shared synthetic fixtures plus `CoreLocalTransportIntegrationTest` |
| Secret-free structured diagnostics | Backend canary redaction test; client logger accepts structured metadata and disables old arbitrary-message adapters |

No previous Room schema existed in this baseline. Version 1 is exported; reopening
it and rejecting an unsupported schema are tested. No fictional v0-to-v1 migration
is claimed. A future version must add and test its real migration.

Concurrent token refresh is **not applicable to current execution**: this backend
has no implemented refresh endpoint. Tests cover the session-generation guarantees
needed by a future refresh implementation; they do not claim to validate rotation,
single-flight refresh or server revocation that does not yet exist.

## Measured cost and bounded work

- Public catalogue: one SQL query when cold, zero queries on the next cache hit.
- Twenty-message history page: at most eight SQL queries in the synthetic test.
- History/room pages cap at 50 records. Socket event buffering caps at 64 and
  pending acknowledgments at 32; reconnect stops after five retry attempts.
- Chat reads coalesce over 100ms; active foreground recovery runs every 30s.

The last two entries are configured bounds verified in focused tests/code, not
production latency measurements. Production p95, throughput, battery usage,
multiworker capacity and memory-pressure limits were **not measured**. In
particular, the native OkHttp engine can allocate an incoming frame before the
client's 1 MiB decoding guard runs.

## Failures found and resolved during implementation

- Initial exception handling changed legacy unhandled-exception behavior. The
  adapter now applies the safe 500 mapping only to opt-in `core-1` requests;
  existing Social tests pass again.
- Gradle configuration-cache capture and initial compile issues were corrected;
  subsequent compilation and configuration-cache reuse pass.
- The first real paired run exposed Ktor 3.2.3 OkHttp rejecting the generic
  `maxFrameSize` setter. Removing that unsupported setter and enforcing the
  documented pre-decode guard made the real socket flow pass.
- Test dispatcher and expected missing-migration cleanup issues were corrected;
  focused tests and the full selected suite now pass.

These were intermediate failures, not unresolved failures in the final selected
checks. Broader untested paths remain limitations below.

## Remaining validation and release gates

- **Blocked here:** full Xcode build/link and iOS simulator/device runtime; selected
  developer directory is CommandLineTools. Android runtime validation also needs
  an emulator/device. Verify lifecycle resume, navigation, Keychain/KVault, ATS,
  Room files, backup and secure-store failure UX on each platform.
- **Not run:** disposable production-version PostgreSQL and Redis/multiworker
  tests, concurrency races, pressure/load tests, staging two-account UX, private
  media access/retention, production security audit and complete dependency scan.
- **Deferred architecture:** OTP one-time proofs, revocable device sessions and
  real refresh, strict unlink/write serialization, private-media ownership,
  durable command deduplication/revisions and private offline storage. Existing
  ten-year JWTs and legacy media ownership gaps remain launch risks.
- Reconcile the independently advanced upstream integration branches before a
  release; this task preserved the requested local design baseline. Deploy
  additive backend support before the new mobile client. No remote operations
  were performed, so this implementation is not yet shared across laptops.

Contract bundle SHA-256 at handoff:
`3de691f2aef2db7011d24c93e1ae74d64d4839dde75037dfeba1cd3e02a58c32`.

Local execution logs (ephemeral, not published CI artifacts):
`/private/tmp/srisu-core-baseline-checks.log`,
`/private/tmp/srisu-core-verification-final.log`,
`/private/tmp/srisu-core-kmp-complete.log`,
`/private/tmp/srisu-core-backend-verified.log`,
`/private/tmp/srisu-core-paired-verified.log`.
The paired Gradle run replaces the ordinary unit-test XML/report with its one
targeted test; the full-suite totals above were recorded before that run.

Initial dirty-file fingerprints are in `/private/tmp/srisu-core-baseline.json`.
No initial file was removed. Existing workspace/design documents were updated
additively where needed; the user's pre-existing `social/test_moments.py` changes
remain byte-for-byte intact. No stash/reset/discard or user-work commit occurred.
