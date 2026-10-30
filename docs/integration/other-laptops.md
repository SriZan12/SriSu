# SriSu workflow from any laptop

GitHub stores the workflow, source map, agent instructions, and verification
commands. Figma remains the design source. The GitHub Actions jobs run on
GitHub-hosted machines, so their execution does not depend on a particular laptop.

## Online entry points

- [Frontend workflow branch](https://github.com/SriZan12/SriSu/tree/codex/workspace-integration)
- [Backend workflow branch](https://github.com/SrizanKhadka/SriSu/tree/codex/workspace-integration)
- [Frontend server checks](https://github.com/SriZan12/SriSu/actions)
- [Backend server checks](https://github.com/SrizanKhadka/SriSu/actions)
- [Figma file](https://www.figma.com/design/LztysD1YvINX7RwZpnhhTt/Srisu?node-id=0-1)
- [Shared project context](../project-context.md)

The matching `codex/workspace-integration` branches make this setup available
before the review changes are merged. The frontend integration target remains
`dev-new-theme`; the backend integration target remains `dev`. Use these workflow
branches until both integration branches contain the setup files.

## Use another laptop for coding

Choose any parent directory, then clone into these two sibling folder names:

```sh
git clone --branch codex/workspace-integration https://github.com/SriZan12/SriSu.git SriSu
git clone --branch codex/workspace-integration https://github.com/SrizanKhadka/SriSu.git SriSu-backend
cd SriSu
python3 tools/workspace.py status --remote
```

The source map and editor workspace use relative paths; no home-directory path
from the original laptop is required. Open `SriSu.code-workspace` in an editor
supporting that format, or add both folders to the desktop coding project.
For Codex CLI on macOS, Linux, or WSL, run `bash tools/codex-workspace.sh`.

Each laptop needs its own installed development tools and authenticated GitHub,
Figma, and coding-agent connections. Credentials and local runtime folders are
not copied through the repositories. Configure `local.properties` or the Android
SDK environment locally; keep those machine-specific settings ignored by Git.

For local backend checks, follow `SriSu-backend/docs/workspace.md` to recreate
the Python environment. Android builds require a compatible JDK and Android SDK;
iOS compilation requires macOS and Xcode. You can inspect GitHub's server results
from any laptop without installing those build tools.

## Server verification

Each repository has `.github/workflows/workspace.yml`:

| Repository | GitHub-hosted checks |
| --- | --- |
| Frontend | Theme validation, Android unit tests and compilation on Linux; iOS simulator-target compilation on macOS |
| Backend | Python dependency consistency, Django checks, migration consistency, and selected isolated tests on Linux |

The workflows trigger on pushes to their integration/setup branches and on
pull requests targeting the integration branches. No production credentials,
database, SMS service, or app deployment is used. The backend tests use disposable
SQLite and block socket connections; PostgreSQL-specific tests remain skipped.

Read the result for the exact commit or PR under **Actions** or **Checks**.
A configured workflow is not evidence of a successful run. Neither pipeline
currently validates a combined frontend/backend end-to-end session or Figma
visual fidelity.

The workflow files include `workflow_dispatch` for manual runs once they are
present on the repository's default branch. Until then, use the push/PR runs;
the default branches are currently `master` (frontend) and `main` (backend),
which differ from the integration branches. This setup does not change those
defaults. See [GitHub's manual-run requirements](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

## Keep laptops synchronized

Commit and push work before moving to another laptop. On the second laptop,
inspect `git status`, preserve any local edits, and pull the intended branch with
`git pull --ff-only` in each repository. Run the workspace status command again.
Keep related frontend/backend changes linked through PRs and the shared feature
brief. Uncommitted work, running processes, and chat transcripts are not stored
by these Git repositories.

Keep the setup branches until both sets of changes are merged, then use the
integration branches in new clones. Do not delete the setup branches while this
guide is still used for onboarding.
