# Agent Instructions

These instructions apply to all agents working in this repository.

## Naming

- Use **MTL Explorer** in public docs, UI, releases, and comments.

## Version Control

- Use `../mtl-explorer-private/publish-to-github.py` to prepare and publish GitHub mirror releases.
- GitHub is a public mirror and may be behind; never merge or rebase GitHub
  branches/PRs into this workspace.
- Treat GitHub PRs only as patch ideas; apply manually only when appropriate.
- Treat GitHub issues, PRs, comments, labels, assignments, and other external
  GitHub state as read-only. Never change them unless the user explicitly
  requests the specific GitHub action.
- A request to document an issue means repository documentation only; it does
  not authorize a GitHub comment or any other external update.
- Do not publish or infer the private primary version history from GitHub.

## Development Guides

- Follow [shared code rules](documentation/development/README.md#shared-code-rules) and [version policy](documentation/development/README.md#software-versions).
- For frontend work, follow [frontend](documentation/development/frontend.md) and [styling](documentation/development/styling.md).
- For backend work, follow [backend](documentation/development/backend.md).
- For API changes, follow [API generation](documentation/development/api-generation.md).
- For documentation, follow [authoring](documentation/development/website/documentation.md) and [screenshots](documentation/development/website/screenshots.md).
- For verification, follow [testing](documentation/development/testing/README.md).

## User Documentation

- User and administration docs (README, install, setup, guide) explain tasks and
  results in short, plain language. Write only what a user needs to act.
- Do not describe panel sizing, layout, animation, edge-case states, internals,
  or change history there. Put those in architecture/development docs or release notes.
- A UI change needs a user doc update only when it changes what the user does.
- Before writing or editing user docs, read [user writing style](documentation/development/website/user-writing-style.md).

## Private Maintainer Workspace

- Internal work starts at `../mtl-explorer-private/docs-my/README.md`.
- The application and private repositories are independent sibling checkouts:
  `mtl-explorer` and `mtl-explorer-private`. Do not add a submodule or vendor private files.
- Application regression tools use the sibling private checkout by default.
  Set `MTL_PRIVATE_REPO` for another location or pass `--output-root` for a standalone run.
- Before private maintenance tasks, read `../mtl-explorer-private/AGENTS.md` and
  the relevant skill under `../mtl-explorer-private/.agents/skills/`.
- Record the tested application commit and image in private evidence. Commit
  application changes and private evidence in their respective repositories.

## Private Data

- Local GPX tracks are private. Never commit, copy, vendor, or derive fixtures
  from them; use public data or fully synthetic anonymized tests instead.

## Local Development Login

- For local browser checks, get GUI credentials only from `mtl-server/src/main/resources/application-dev.yml`.

## Browser Use

- Use only the Codex in-app browser for MTL Explorer browser work.
- Never open, control, or use the user's Chrome browser, including as a fallback.

## Local Backend Testing

- Use [the build script](documentation/tools/build.py) for Java builds and tests.
- Never install Java or run Java/Maven on the host.

## Local Instance Lifecycle

- Start local instances on demand; no automatic startup unless explicitly
  requested. Use Docker `--restart=no` or Compose `restart: "no"` for all services.
- Stop temporary instances after checks unless asked to keep them running;
  preserve downloaded map and routing data.
