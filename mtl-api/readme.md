# mtl-api — OpenAPI Client Generation

This module generates typed TypeScript client stubs from the OpenAPI schema.

## Source of truth

`open-api-schema/schema.json` — the full API contract. Update this whenever the server API changes.

To refresh it from a running server:
- Swagger UI: http://localhost:8080/mtl/swagger-ui/index.html
- Raw schema: http://localhost:8080/mtl/v3/api-docs

## Active generator

| Module | Generator | Used by |
|---|---|---|
| `mtl-api-typescript-fetch` | `typescript-fetch` (native Fetch API, no deps) | `mtl-client` |

## How to regenerate

Run from the `mtl-api` directory:

```bash
# 1. Generate TypeScript source from schema
mvn -pl mtl-api-typescript-fetch package -q

# 2. Compile to dist/ so the client picks up the latest build
cd mtl-api-typescript-fetch/target/generated-sources/mtl-typescript && npm run build
```

The generated output lands in `mtl-api-typescript-fetch/target/` which is excluded from git (it is a build artifact).

## How the client consumes it

`mtl-client/package.json` references the generated package via a `file:` dependency:

```json
"x8ing-mtl-api-typescript-fetch": "file:../mtl-api/mtl-api-typescript-fetch/target/generated-sources/mtl-typescript"
```

Vite's `server.fs.allow` is configured to permit reading files outside the client directory.
After regenerating, no `npm install` is needed in the client — the symlink updates automatically.
