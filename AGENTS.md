# SriSu project guidance

## Identify the project before changing it

- Read `docs/project-context.md` for the frontend, backend, and Figma map. Read the
  relevant feature documentation before implementation.
- The default backend checkout is `../SriSu-backend`. Run
  `python3 tools/workspace.py status --remote` to verify both repositories.
  `docs/integration/project-map.json` is the machine-readable source map.
  `SRISU_BACKEND_DIR` overrides the backend location when necessary.
- Frontend: `SriZan12/SriSu`, integration branch `dev-new-theme`.
- Backend: `SrizanKhadka/SriSu`, integration branch `dev` (confirmed by the user).
  These are different repositories despite sharing the name SriSu.
- Figma: file `LztysD1YvINX7RwZpnhhTt`. Page `0:1` is the design system;
  use the relevant screen's node ID for feature work.
- At task start, inspect each relevant checkout's remote, branch, commit, and
  worktree status. Distinguish an integration branch from the current feature
  branch. Preserve existing work; do not silently switch or reset a checkout.
- Fetch or read the specified remote branch when checking current upstream code.
  A local tracking ref or a historical snapshot is not proof of current state.
- Explicitly read a secondary repository's `AGENTS.md` and applicable nested
  guidance before changing it; primary-folder discovery may not load those files.
- Links identify sources but do not grant access. Report unavailable sources and
  access limitations rather than inferring their contents.

## Keep the three sources consistent

- Figma defines intended appearance and interaction. Backend code and its tested
  contract define available data, authorization, and server behavior. Frontend
  code defines the implemented client behavior. Surface conflicts explicitly.
- Reuse existing feature boundaries, repositories, ViewModels, Ktor services,
  Koin wiring, shared components, and theme tokens. Keep platform-specific APIs
  in platform source sets. Propose significant architecture changes with reasons.
- Never invent an endpoint, response field, permission, or WebSocket event from
  a design. Inspect backend routes, serializers, handlers, and relevant tests.
- Before changing an API, identify affected client calls and compatibility with
  already released clients. Document required deployment order and migrations.
- For UI work, read `docs/design-system/README.md` and inspect current Figma
  context for the requested node. `figma-spec.json` is a dated snapshot, not live
  sync. Record intentional deviations, derived styles, and placeholders.
- Update the affected feature map/flow documentation when behavior changes.
  Record frontend/backend commits and Figma node references in handoffs and PRs.

## Reliability and security

- For behavior changes, cover relevant success, empty, loading, validation,
  failure, retry, expired-session, and permission-denied states.
- Authorization belongs on the server. Test that unrelated users cannot read or
  mutate another couple's profiles, invitations, messages, or private media.
- Keep credentials, signing material, production data, tokens, OTPs, and private
  message contents out of source control, prompts, fixtures, and logs. Use
  synthetic fixtures and secret-free environment examples.
- Test important state transitions, concurrent/duplicate requests, cancellation,
  and compatibility where relevant. A compiling UI is not an integration test.

## Verification and handoff

Use the checks appropriate to the change; these commands are documented by this
repository, not a claim that they passed in the current task:

```sh
python3 tools/theme/check_theme.py
./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

- Discover backend setup and test commands from its selected branch before
  running them. Use an isolated test database and synthetic accounts.
- Cross-repository baseline: `python3 tools/workspace.py verify all`. For backend
  checks alone use `python3 tools/workspace.py verify backend`; read its
  `docs/workspace.md` for isolation and coverage limits. Do not use unrestricted
  backend test discovery, since its legacy chat test connects on import.
- For user-visible changes, verify Android and iOS behavior when environments
  are available; report device/simulator and live-backend coverage separately.
- Finish with changes made, evidence from checks actually run, remaining gaps,
  and affected repositories. Keep verified facts distinct from proposals.
