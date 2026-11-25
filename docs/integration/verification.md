# Workspace verification — 2026-09-26

Historical local verification, before publishing the workflow to GitHub. Checked
on macOS against these base commits plus the workspace setup changes:

| Repository | Working branch | Base commit | Integration branch |
| --- | --- | --- | --- |
| Frontend | `dev-new-theme-couple-profile` | `8af5deb9c9a246504bd1b3b4776122505687feb9` | `dev-new-theme` |
| Backend | `codex/workspace-integration` | `1a2df95c5aece1d110408bf90aa9ed6f9970da18` | `dev` |

The live remote integration refs matched these commits during verification.
Future tasks must recheck them; this document is a historical result.

## Results

| Check | Result |
| --- | --- |
| Repository identity and live integration refs | Both passed |
| Figma access | Metadata read for file `LztysD1YvINX7RwZpnhhTt`, nodes `0:1` and `14:220` |
| Theme validation | Passed: 96 documented colors retained; 24 role pairs meet 4.5:1 contrast |
| Android unit tests | 20 passed, 0 failed |
| Android Kotlin compilation | Passed |
| iOS simulator ARM64 Kotlin compilation | Passed; compilation does not exercise the simulator UI |
| Backend dependency check | Passed, Python 3.13.5 with exported lock versions |
| Django system checks | Passed |
| Backend selected tests | 77 discovered: 72 passed, 5 PostgreSQL-only tests skipped |
| Migration consistency | `makemigrations --check --dry-run`: no changes detected |
| Workspace helper smoke checks | Repository identity accepted; wrong identity and missing checkout rejected |
| Local documentation/JSON/shell checks | Passed |

The backend initially failed an existing direct-view media test because a missing
path raised Django `Http404` instead of returning an explicit response object.
The test now accepts that exception as a 404 while preserving its HTTP-client
denial assertions. Application endpoint behavior was not changed.

The shared `verify all` command completed the frontend checks and exposed that
backend test issue. After the test-only correction, `verify backend` passed.
Backend checks used an in-memory database/cache/channels, disposable media, and
blocked socket connections. Logged storage/cache failure messages were expected
fault-injection cases in the existing tests.

## Available workflow

```sh
# From the frontend root:
python3 tools/workspace.py status --remote
python3 tools/workspace.py verify all
bash tools/codex-workspace.sh
```

The editor workspace is `SriSu.code-workspace`. The backend has its own
`AGENTS.md`, `docs/workspace.md`, Python environment, and check runner.

## Publication update

GitHub write access was subsequently verified for both repositories. The setup
is distributed on matching `codex/workspace-integration` branches with GitHub
Actions workflow files. Use [the portable guide](other-laptops.md) and the actual
Actions results for server verification. The local results above do not imply
that any GitHub run has passed.

## Remaining boundaries

- The current chat can read/edit both directories. Adding the backend folder to
  the desktop project's saved folder list requires the user to select
  **Edit project → Add folder**. Computer Use rejected control of the Codex app;
  no settings automation or internal-state workaround was used.
- Local command-line Git authentication remains separate from the connected
  GitHub account. Each laptop needs its own authorized login for pushing.
- PostgreSQL concurrency, real device UI, live WebSocket/OTP transport, and
  authenticated mobile-to-server end-to-end behavior were not verified here.
- The API schema, WebSocket contract validation, automatic design drift checks,
  required-check enforcement, deployment setup, and production security audit
  are not implemented by this workspace setup. Refer to the project guide for
  their recommended scope. Connected references are not automatic synchronization.
