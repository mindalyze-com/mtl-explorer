# mtl-api-typescript-fetch

Generated TypeScript client for the MTL API, using the native **Fetch API** (no runtime dependencies).

## Generated content

Everything under `target/` is generated — do not edit it manually. It is excluded from git.

| Path | Description |
|---|---|
| `target/generated-sources/mtl-typescript/src/` | Generated TypeScript source (models + API classes) |
| `target/generated-sources/mtl-typescript/dist/` | Compiled JS + `.d.ts` declaration files consumed by `mtl-client` |

## Regenerating

From the `mtl-api` directory:

```bash
mvn -pl mtl-api-typescript-fetch package -q
cd mtl-api-typescript-fetch/target/generated-sources/mtl-typescript && npm run build
```

See the parent [readme.md](../readme.md) for the full workflow.

## Generator

Uses [`openapi-generator-maven-plugin`](https://github.com/OpenAPITools/openapi-generator) v7.4.0 with generator `typescript-fetch`.
Schema source: `../open-api-schema/schema.json`
