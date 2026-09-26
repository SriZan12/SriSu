# SriSu: frontend, backend, and design

This is the shared entry point for SriSu work. It records source identities and
working agreements; it does not synchronize repositories or Figma automatically.
Refresh live sources at task start. Inspection snapshot: 2026-09-26.

See [the setup verification record](integration/verification.md) for commands,
passed checks, test skips, and remaining account/UI steps.
For GitHub-hosted workflow files and setup from another laptop, use
[the portable workflow guide](integration/other-laptops.md).

## Source map

| Part | Canonical source | Working reference |
| --- | --- | --- |
| KMP frontend | [SriZan12/SriSu](https://github.com/SriZan12/SriSu) | [`dev-new-theme`](https://github.com/SriZan12/SriSu/tree/dev-new-theme), selected by the user |
| Django backend | [SrizanKhadka/SriSu](https://github.com/SrizanKhadka/SriSu) | [`dev`](https://github.com/SrizanKhadka/SriSu/tree/dev), confirmed by the user |
| Figma | [Srisu](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu) | Design system page [`0:1`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=0-1); use screen nodes for features |

At inspection:

- Frontend upstream `dev-new-theme`: `8af5deb9c9a246504bd1b3b4776122505687feb9`.
- Local frontend branch: `dev-new-theme-couple-profile`, at the same commit.
  The checkout was clean before this documentation setup. No branch was switched.
- Backend upstream `dev`: `1a2df95c5aece1d110408bf90aa9ed6f9970da18`.
  Its default `main` branch contains a small scaffold and is not the chosen
  application reference. `dev` includes authentication, chat, and social features.
- Both repositories and Figma page metadata were readable through the connected
  tools. GitHub write access was verified for both repositories during publication.
  Each laptop still needs its own authenticated connections for writing.
- The backend is now cloned at `../SriSu-backend`, on working branch
  `codex/workspace-integration` based on the selected `dev` commit. Its Python
  environment and isolated check runner are documented in its `docs/workspace.md`.
- This chat can read and edit both local repositories. The desktop project's
  secondary-folder registration remains a manual UI step: the app blocks
  automation of its own UI. No private app settings were edited to bypass that.
- The workflow publication uses the connected GitHub account. Local command-line
  Git authentication is independent and must be configured on each laptop.

These commit IDs are historical anchors, not permanent pins for future work.

## Existing implementation and design references

Frontend code lives in `composeApp/src/commonMain/kotlin/com/srisu/srisu`:

- `features/`: feature screens, state, ViewModels, repositories, and remote models.
- `di/`: dependency injection and network wiring.
- `core/session/`: shared session handling.
- `theme/` and `components/`: reusable visual foundations.
- `composeApp/src/androidMain`, `composeApp/src/iosMain`, and `iosApp`: platform code.

Reuse [the theme documentation](design-system/README.md),
[the Figma specification snapshot](design-system/figma-spec.json), and
[the partner-linking flow](flows/partner-linking.md).

| Feature/reference | Figma node | Frontend anchor | Backend anchor on `dev` |
| --- | --- | --- | --- |
| Design system | `0:1` | `theme/`, `components/`, design-system docs | Not applicable |
| Find your partner | [`14:220`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=14-220) | `features/chat/presentation/findpartner/`, `ChatApiService.kt` | `social/urls.py`, `social/api/views.py`; `/api/social/find-partner/` |
| Invite sent | [`5:974`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=5-974) | `InviteSentScreen.kt`, partner-linking docs | Inspect the connection routes and handlers in `social/` |
| Connected confirmation | [`5:1118`](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=5-1118) | `YoureConnectedScreen.kt`, partner-linking docs | Inspect accepted-connection state and authorization in `social/` |

Screen-node references above come from existing frontend documentation. The
design-system page and FindYourPartnerScreen metadata were read during setup. The search route
was confirmed in backend URL configuration. This is not a full endpoint audit.

Existing documented differences matter: partner search uses phone numbers;
the design's countdown/share action is not supported by the documented API;
dark mode is derived; some connected-confirmation artwork remains a placeholder.
Recheck these against live sources before extending the feature.

## Use the connected local workspace

Both repositories now have instructions linking their counterpart and Figma.
The shared structured reference is [project-map.json](integration/project-map.json).
Paths in that file are relative to the frontend root; `SRISU_BACKEND_DIR` can
select a different backend checkout for the command-line helpers.

From the frontend root:

```sh
python3 tools/workspace.py status --remote
python3 tools/workspace.py verify all
```

`status` validates repository identities and prints working branches, commits,
dirty-state summaries, and live integration refs. It does not switch, reset,
fetch, or push. Figma live access is checked through the connected Figma tools;
the script only reports the configured reference.

`verify frontend` runs theme validation, Android unit tests/compilation, and iOS
simulator compilation on macOS. `verify backend` runs Django checks and the
selected offline test suites. `verify all` runs both in sequence. On macOS it
uses Android Studio's bundled JDK by default; set `SRISU_JAVA_HOME` to override.
The backend runs with its own ignored `.venv` and synthetic test settings.

For an editor, open [SriSu.code-workspace](../SriSu.code-workspace). For a fresh
Codex CLI session with both directories, run:

```sh
bash tools/codex-workspace.sh
```

This uses the supported `--cd` and `--add-dir` flags and preserves the configured
approval policy. The launcher validates repository identities first. It does
not create another chat automatically as part of setup.

Per-laptop account/UI setup:

1. In the desktop project's menu, use **Edit project → Add folder** and select
   the sibling `SriSu-backend` directory. Keep the frontend as primary.
   The current chat already has filesystem access; this makes the second folder
   part of the saved project for future chats. The editor workspace file is not
   an automatic change to the desktop project's folder list.
2. Configure a GitHub login with write access to `SrizanKhadka/SriSu` before
   pushing backend changes. No credentials are stored in these workspace files.

The workflow is distributed on `codex/workspace-integration` in both repositories.
Use those branches for another laptop until the reviewed changes are merged into
the integration branches. See the portable guide for exact clone commands.

The desktop supports multiple attached local folders, but automatic instruction
discovery and default Git/worktree actions use the primary folder. A worktree for
the primary repository does not independently isolate the backend checkout.
Use a separate backend branch/checkout when concurrent work needs isolation.
See [official project documentation](https://learn.chatgpt.com/docs/projects) and
[AGENTS.md guidance](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

## Keep changes in sync

The subsequent local `dev-core-architecture` implementation adds a backend-owned
`contracts/core-1/` bundle, a pinned KMP copy, and executable paired checks. Read
[the implementation guide](architecture/system-design/08-core-implementation.md)
and [actual validation results](architecture/system-design/09-core-validation.md)
before extending shared infrastructure. Its broader v2 design remains proposed.
The implementation is local and has not been published. The setup guidance below
describes the original workflow; contract checks now exist for the core subset.

Use the backend as the owner of a versioned REST API contract. Add a checked-in
OpenAPI schema, validate it against implementation, and make the frontend test
its DTOs and calls against an identified schema version or backend commit.
A published contract artifact or pinned generated copy is preferable to two
independently edited schemas. OpenAPI/contract CI was not added by this setup.

Document WebSocket messages separately: event names, payload schemas, auth,
reconnection, ordering, acknowledgement, and duplicate-delivery behavior. Chat
has a WebSocket implementation, so an HTTP schema alone is insufficient.

For every feature, record these together in a flow document or task brief:

```text
Feature and user outcome:
Frontend branch and commit:
Backend branch and commit:
Contract/schema version:
Figma node links and inspection date or named design version:
Acceptance criteria, including failure and permission states:
Dependencies, deployment order, and migration/rollback needs:
Known differences between design, contract, and implementation:
Tests run and evidence:
Unverified items:
```

Require related frontend/backend PRs to link to one another and the same brief.
Prefer additive server changes compatible with released clients, deploy the
server support before the client depends on it, and retire old behavior only
after the compatibility policy permits it. Update the contract and map in the
same change that alters behavior. Synchronization is maintained by this workflow
and CI, not by a model remembering earlier chats.

## Quality checks to establish

GitHub Actions configuration now covers the existing frontend build/theme/tests
and isolated backend checks/tests. See the Actions pages for actual server-run
results. Further checks to establish include:

- Frontend unit/behavior tests and Android/iOS compilation; theme checks for
  visual changes; real-device or simulator checks for navigation, keyboard,
  accessibility, text growth, and relevant offline/error behavior.
- Backend tests using an isolated database, migration consistency checks, API
  contract validation, dependency/secret scanning, and deployment-setting checks.
- Cross-user/cross-couple authorization tests for profiles, invites, chat,
  WebSocket subscriptions, and private media; session expiry and revocation;
  OTP abuse controls and sensitive-log checks where those paths change.
- End-to-end tests with two synthetic partner accounts and a third unrelated
  account. Verify denied access as well as the successful relationship flow.
- Required CI and review before merging, plus a staging smoke test covering the
  exact frontend/backend commit pair. AI review supplements executable checks.

## Using ChatGPT Pro and Codex

Use a ChatGPT project for product decisions, architecture discussions, and
research. Give it a current copy of this map, the product brief, accepted
architecture decisions, and the relevant API/design references. Project uploads
are snapshots; refresh them after accepted changes. Use the local Codex project
for implementation and verification against both actual checkouts.

Project instructions can say:

> SriSu uses SriZan12/SriSu at dev-new-theme for KMP, SrizanKhadka/SriSu at dev
> for Django, and Figma file LztysD1YvINX7RwZpnhhTt. Start from the current
> project-context document. Inspect the relevant live sources and state what
> you could verify. Keep backend authorization and API behavior explicit,
> reuse existing architecture and design tokens, and record accepted decisions
> in versioned docs. Identify mismatches before inventing behavior.

For feature work, start a focused chat with this prompt:

> Read AGENTS.md and docs/project-context.md. Implement [feature] using Figma
> node [link]. Verify frontend dev-new-theme and backend dev, then identify
> the actual feature branches and commits being used. Inspect the relevant
> client, backend handlers/tests, and design. Explain mismatches and the
> smallest coherent change, implement within this task's scope, run relevant
> tests, update the feature map, and report verified results and remaining gaps.
> Acceptance criteria: [user-visible behavior and failure/permission cases].

For architecture and security review, use the strongest reasoning model
available in your account with higher reasoning effort when useful. Use a
separate review pass grounded in the diff, requirements, and test evidence.
Model names/settings and tool availability vary by product; see
[official model selection guidance](https://learn.chatgpt.com/docs/model-selection).
Avoid treating any model choice as proof of correctness or security.
