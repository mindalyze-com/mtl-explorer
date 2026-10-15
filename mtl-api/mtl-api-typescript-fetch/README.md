# mtl-api-typescript-fetch

Generated TypeScript client for the MTL API, using the native **Fetch API** (no runtime dependencies).

## Generated content

Everything under `target/` is generated — do not edit it manually. It is excluded from git.

| Path | Description |
|---|---|
| `target/generated-sources/mtl-typescript/src/` | Generated TypeScript source (models + API classes) |
| `target/generated-sources/mtl-typescript/dist/` | Compiled JS + `.d.ts` declaration files consumed by `mtl-client` |

## Regenerating

From the `mtl-api` directory inside the existing development container:

```bash
mvn -pl mtl-api-typescript-fetch install -q
```

The `install` phase installs the generated npm package's dependencies; its
prepare script compiles `dist/`. A generation-only `package` does not install
those dependencies on a fresh checkout. See [the API workflow](../README.md).

## Generator

Uses `openapi-generator-maven-plugin` with generator `typescript-fetch`.
The [POM](pom.xml) owns the generator and toolchain versions.
Schema source: [schema.json](../open-api-schema/schema.json).
