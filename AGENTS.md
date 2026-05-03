# Agent Instructions

These instructions apply to all agents working in this repository.

## OpenAPI And Type Safety

- Always keep the OpenAPI contract as the source of truth for API types.
- When changing server API request/response shapes, update the server first so it produces the correct OpenAPI schema.
- Start the updated server locally and download the live OpenAPI schema from `/mtl/v3/api-docs` into `mtl-api/open-api-schema/schema.json`; do not hand-edit or infer schema changes.
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
