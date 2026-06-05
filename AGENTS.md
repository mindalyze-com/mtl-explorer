# Agent Instructions

These instructions apply to all agents working in this repository.

## Naming

- Use **MTL Explorer** in public docs, UI, releases, and comments.

## Version Control

- GitHub is a public mirror and may be behind; never merge or rebase GitHub
  branches/PRs into this workspace.
- Treat GitHub PRs only as patch ideas; apply manually only when appropriate.
- Do not publish or infer the private primary version history from GitHub.

## Documentation Style

- Keep README, markup, and agent notes concise; link out.
- Keep root `README.md` GitHub-friendly: tables, badges, collapsibles.
- For major user-facing features, update `documentation/features.md` if warranted.

## Frontend UX

- For UI changes, design the full user flow before editing; avoid narrow visual patches.

## Calculation Ownership

- Keep advanced domain calculations on the server; change APIs rather than duplicating logic in the client.

## Screenshots And Assets

- Prefer WebP screenshots; use PNG only if needed.
- For full end-user regression reports, keep compact screenshots for working
  functions too, not only failures, so the report gives a readable visual
  overview of the validated app.
- Local GPX tracks are private. Never commit, copy, vendor, or derive fixtures
  from them; use public data or fully synthetic anonymized tests instead.

## OpenAPI And Type Safety

- OpenAPI is the API type source of truth.
- For API shape changes, update server first; save live `/mtl/v3/api-docs` to `mtl-api/open-api-schema/schema.json`; never hand-edit it.
- Keep schema `servers` at the normal local URL.
- Add JSON fields in thematic Java order; match `@JsonPropertyOrder`.
- Regenerate the TypeScript client with Maven from `mtl-api/mtl-api-typescript-fetch`.
- Use generated TypeScript types/client APIs; document temporary generator workarounds.
- Run relevant backend/frontend checks; report pre-existing failures separately.

## Constants

- Use named constants for semantic, reused, or tunable values.
- Avoid production magic numbers/strings.
- Use the smallest useful scope; reuse existing constants.

## Local Development Login

- For local browser checks, get GUI credentials only from `mtl-server/src/main/resources/application-dev.yml`.

## Software Versions

- Verify the latest stable non-beta before proposing or pinning software, runtime, package, or base-image versions.
- Prefer latest stable LTS unless compatibility requires older.

## Data Migration Policy

- Do not add legacy-data backfill or old-data migration paths unless explicitly requested.
- Assume users can recreate the database after derived-stat or data-shape changes.
- Keep ingest/recalculation for new imports; avoid migration-only admin endpoints/services.
