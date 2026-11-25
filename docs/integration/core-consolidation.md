# Core architecture consolidation — 2026-10-10

The user authorized consolidating the open feature work into `dev-core-architecture`
in both repositories. The outgoing architecture PRs remain open: backend
[37](https://github.com/SrizanKhadka/SriSu/pull/37) and frontend
[22](https://github.com/SriZan12/SriSu/pull/22). This is development integration,
not deployment or a change to the documented `dev` / `dev-new-theme` release flow.

## Preserved source history

| Repository | Source | Starting commit | Remote backup |
| --- | --- | --- | --- |
| Backend | Architecture | `c68e7b068229cb57d37064bea903108f237b1251` | `codex/backup-core-20261010` |
| Backend | Chat, PR 39 | `ea0e639d74b4de982d6257ad6d487ca8ffe5de5e` | `codex/backup-chat-20261010` |
| Frontend | Architecture | `fa7e4fa20a90d2e1289c80fc5552d2bf69e00dd5` | `codex/backup-core-20261010` |
| Frontend | Chat, PR 24 | `d7bb5e9666ec9af8a1944dd6c10053655e54a646` | `codex/backup-chat-20261010` |
| Frontend | Navigation, PR 23 | `ce31cdf1a95f003f2c5f2bd1f040704a8a78a0b5` | `codex/backup-navigation-20261010` |

Frontend theme PR 20's head `3fa6822ea05578d64cca8891c5e3fdb0a831897a`
is already an ancestor of the chat head. Incorporating that history needs no
duplicate cherry-pick. Merge commits retain the original commits and feature
branches; no force push or branch deletion is part of this consolidation.

## Integration decisions

Four textual conflicts were resolved in `EntertainmentScreen.kt`, `ChatNav.kt`,
`HomeGraph.kt`, and `docs/flows/couple-profile.md`. Both profile documentation
sections are retained. The typed modular navigation, scoped ViewModels, session
and couple membership boundary, invitation entry, and Singles retirement remain.

Home, the member profile Message action, partner acceptance, and the existing
strict `srisu://chats/<uuid>` allowlist now open `CoupleConversation`. Chat plan
cards use `CoupleProfileDestination` with `CoupleSection.PLAN`; Moment cards keep
their authorized source viewer. Acceptance removes the completed partner flow.
Legacy chat source remains in history and its explicit internal routes, but
encrypted conversation entry points do not fall back to the legacy transport.
Restoration, callback, entry disposal, and external-link tests exercise the
encrypted route and retain the session/account-bound pending-link policy.

The backend application tree is the previously published chat PR tree. Both
repositories' CI branch filters now include `dev-core-architecture` for push and
pull-request checks. Their existing API contract snapshots agree. Backend migrations and private-media
deployment requirements in [the chat guide](../flows/couple-chat.md) still apply;
deploy the backend's additive migrations/API before the updated client. Challenge
references remain unavailable with the versioned extension point intact.

## Validation

The final command outcomes are recorded in the consolidation PR. Physical-device
gesture/accessibility QA, provider delivery, and independent security review remain
release prerequisites described in the feature guides; automated merge checks do
not establish those outcomes. All integration tests use synthetic accounts and
disposable databases. Original local checkouts and user media are preserved.
