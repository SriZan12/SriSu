This is a Kotlin Multiplatform project targeting Android, iOS.

Start with [the workflow for another laptop and GitHub server checks](docs/integration/other-laptops.md).

For the frontend/backend branches, Figma references, local setup, and shared
development workflow, start with [SriSu project context](docs/project-context.md).
Repository guidance for coding agents is in [AGENTS.md](AGENTS.md).

The paired backend lives at `../SriSu-backend` by default. Check both repositories
with `python3 tools/workspace.py status --remote`, then run their baseline checks
with `python3 tools/workspace.py verify all`. Open [SriSu.code-workspace](SriSu.code-workspace)
for both editor folders, or run `bash tools/codex-workspace.sh` for a Codex CLI
session with both directories available.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
