# Agent Instructions

These instructions apply to all agents working in this repository.

## Naming

- The user-facing tool name is **MTL Explorer**. Use this name in docs,
  UI copy, release text, and public-facing comments.

## Documentation Style

- Keep README and markup notes short and concise. Prefer links to detailed docs
  over long marketing or explanatory copy.
- Optimize the root `README.md` for GitHub-flavored Markdown rendering,
  including useful GitHub-supported tables, badges, and collapsible sections.
- When adding a major user-facing feature, review `documentation/features.md`
  and update it if the feature is important enough compared with the existing
  entries.

## OpenAPI And Type Safety

- Always keep the OpenAPI contract as the source of truth for API types.
- When changing server API request/response shapes, update the server first so it produces the correct OpenAPI schema.
- Start the updated server locally and download the live OpenAPI schema from `/mtl/v3/api-docs` into `mtl-api/open-api-schema/schema.json`; do not hand-edit or infer schema changes.
- When adding JSON-visible fields, place them where they fit thematically in the Java source and keep `@JsonPropertyOrder` in that same source order.
- If a temporary server port is used while generating the schema, keep the checked-in schema's `servers` entry stable for the normal local URL.
- Regenerate the frontend TypeScript API client with Maven from `mtl-api/mtl-api-typescript-fetch`.
- Use the generated TypeScript types/client APIs in frontend code instead of hand-written duplicate API types.
- Do not bypass generated types for convenience unless there is a documented generator limitation; keep such workarounds small and temporary.
- Type correctness matters. Run the relevant backend compile and frontend build/type checks when API contracts change, and report any pre-existing failures separately.

## Constants

- Prefer named constants over repeated literals for values that have semantic meaning, are reused, or may need tuning.
- Avoid magic numbers and magic strings in production code.
- Put constants near the smallest practical scope: local constants for single-function behavior, module constants for shared local behavior, and exported constants only when multiple modules need the value.
- Reuse existing constants before introducing new ones.

## Local Development Login

- For local browser verification, use the GUI credentials from `mtl-server/src/main/resources/application-dev.yml`.
- Treat `application-dev.yml` as the source of truth for local dev login values; do not take them from `application.yml` or documentation snippets.

## Software Versions

- Before proposing or pinning a software, runtime, package, or base-image version, verify the latest stable non-beta release first.
- Prefer the latest stable LTS where the project has an LTS track, unless there is a documented compatibility reason to use an older version.

## Data Migration Policy

- Do not add legacy-data backfill or old-data migration paths unless explicitly requested.
- Assume users can recreate the database after derived-stat or data-shape changes.
- Keep ingest and recalculation logic focused on newly imported data; avoid admin endpoints or services whose only purpose is migrating existing rows.
