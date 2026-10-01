# API connectivity audit — 2026-09-29

Baseline: frontend `SriZan12/SriSu` at `e30a3275be06345c1e4d311d3711cdda2fea0eca`
and backend `SrizanKhadka/SriSu` at `b9160da83430b4b4c1fbac71d3cb1fcad2ab3860`,
both on `dev-core-architecture`. Existing local frontend edits were preserved.
This task changes transport/configuration/tests only; no Figma screen changes.

## Actual cause of the reported failure

The supplied request was `POST /api/auth/send-otp/` with a **400 HTML** response,
not 404. On the user's Docker server, the same origin with its LAN Host returned
400 for every probed route; a read-only diagnostic with Host localhost returned
200 JSON for interests, 405 JSON for send-otp and refresh, and 401 JSON for rooms.
That confirms host validation rejects the LAN address before DRF/OTP processing.
Changing endpoint spellings, JSON bodies or CORS will not fix this rejection.

On the other laptop running Docker Compose, add the server's current LAN IP to
the backend `.env`, then recreate the web container:

```dotenv
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,192.168.1.73
```

```sh
docker-compose up -d --force-recreate web
```

The address is the backend laptop's address, not the phone's. Use hostnames/IPs
without scheme or port; keep an explicit allowlist. The backend Compose file now
passes `DJANGO_ALLOWED_HOSTS` and the correct `DJANGO_DEBUG` name. No remote Docker
process, real database, signing key or SMS provider was changed by this task.

From this frontend checkout, safely verify connectivity without sending an OTP:

```sh
python3 tools/check_api.py --base-url http://192.168.1.73:8000/
```

The script uses only unauthenticated GETs, does not follow redirects, and prints
status/content type without response bodies. Build with the same origin:

```sh
./gradlew :composeApp:assembleDebug -Psrisu.environment=development -Psrisu.apiBaseUrl=http://192.168.1.73:8000/
```

The base URL is an origin ending in `/`, not an `/api/` prefix. A physical device
needs a reachable LAN origin; Android emulator `10.0.2.2` reaches its own host,
not a backend on another laptop. HTTPS remains required for release builds.

## Contract audit and fixes

All 26 first-party method/path/query combinations currently called by KMP match
the checked-out backend URLconf. They are now enumerated in backend-owned
`contracts/core-1/routes.json`, pinned in the frontend, and compiled into the
generated test fixture. Django tests resolve each path and verify its method;
KMP tests invoke all API service operations and compare the emitted requests
against that inventory, including trailing slashes, IDs, queries and auth scope.

The earlier contract fixtures covered core/auth payloads and a small paired
transport scenario. They did not verify every legacy service's URL construction
or Docker host settings. API design and those tests therefore did not establish
that an arbitrary running server's configuration matched the mobile build.

The audit found an additional real client defect: Suggestions and Connections
used the global build URL instead of the environment attached to their HTTP
client. A regression test reproduced requests going to the LAN build origin
instead of an injected test origin. They now share the client's environment;
Auth/Profile/Chat default to it as well. The obsolete global URL accessor is gone.

HTML 400 responses now report server configuration instead of asking users to
correct valid form fields. JSON validation still retains its original field
errors. Full body/header logging was removed from the local transport settings;
status, duration and request ID diagnostics remain.

Separately, live backend integration branch `dev` at
`ecc8592307114e7d154bda9612e6826e2ca8ba90` still lacks refresh/logout and room-history
routes from the feature branch. That is not the observed LAN-host failure: the
running Docker server does resolve refresh and rooms. Deploy the paired backend
before the new mobile app. A missing refresh route can otherwise make many
protected calls appear to return 404 because their shared refresh prerequisite
fails first. That case now reports `backend_upgrade_required` without deleting
credentials, weakening authentication or replaying writes.

Resource-level 404s remain correct for nonexistent/unavailable records and
inaccessible chat rooms. This change does not bypass those authorization checks.
External country/city lookup is outside the SriSu backend route inventory.

## Verification

| Check run | Result |
| --- | --- |
| New route test before the environment fix | Failed as expected: injected `https://contract.example:8443/` became the LAN build origin in Suggestions. |
| `python3 tools/workspace.py verify all` | Passed: 65 client tests discovered, 64 passed and the conditional paired test skipped; Android and iOS simulator Kotlin compilation passed; theme/contract checks passed. Backend: 125 discovered, 115 passed, 10 PostgreSQL-only tests skipped on SQLite. |
| `python3 tools/core_integration.py` | Passed separately: real KMP HTTP/WebSocket traffic to disposable Django, including profile, suggestions, empty preferences, all four invitation lists, request status, partner lookup, interests, chat history and logout cleanup. |
| Backend `.venv/bin/python tools/check_core_contracts.py` | Schema and all 12 response/event fixtures passed. The selected Django test suite also checks all 26 route/method combinations. |
| Compose YAML parse and host/debug variable inspection | Passed; Docker is not installed on this frontend laptop, so no local container startup is claimed. |
| `git diff --check` in both repositories | Passed. |
| Final `tools/check_api.py` probe of the other laptop | Still 400 HTML for all four checks. Remote `.env` update/container recreation remains required. No SMS or authenticated request was sent. |

The first full test run exposed an older mock client without an environment;
its setup now supplies an explicit test origin and the full rerun passed. The
separate paired test passed against the corrected production service code.

Route parity is separate from a complete legacy DTO/body audit. Native Android/iOS
device runtime, real SMS delivery, PostgreSQL concurrency and a successful request
after the remote host-setting change were not verified in this task. These results
were recorded before publication; publishing the fixes does not update the other
laptop's environment or restart its Docker containers.
