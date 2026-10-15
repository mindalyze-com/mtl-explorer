# API schema and client generation

OpenAPI is the API type source of truth. Change the server first, then export
its live schema and regenerate the TypeScript client. Never hand-edit API shapes.

Add JSON fields in thematic Java order and match `@JsonPropertyOrder`.

## Export the schema

With the development backend running, work from `mtl-api/open-api-schema/`:

```sh
curl --fail --silent --show-error http://localhost:8080/mtl/v3/api-docs -o schema.new.json
python3 -m json.tool --indent 2 schema.new.json schema.validated.json
```

Check both commands succeeded before replacing `schema.json`. Keep two-space
indentation, original field order, a trailing newline, and the normal
`http://localhost:8080/mtl` server URL. Remove the temporary files afterward.
Configure the export server correctly rather than editing the exported values.

## Generate the client

Inside the existing Java development container, run from `mtl-api/`:

```sh
mvn -pl mtl-api-typescript-fetch install -q
```

The generated package is under
`mtl-api-typescript-fetch/target/generated-sources/mtl-typescript/`.
Its source and compiled output are build artifacts. The
[frontend manifest](../../mtl-client/package.json) consumes that package through
its local `file:` dependency. Run `npm install` in `mtl-client/` after the first
generation. Finish regeneration before starting Vite after Maven `clean`.

Use generated models and API methods. Document any temporary generator
workaround, and verify relevant backend/frontend contracts after a shape change.
Run relevant backend/frontend checks and report pre-existing failures separately.
The [saved schema](../../mtl-api/open-api-schema/schema.json) defines actual
field names and units; [architecture](../architecture/units.md) explains semantics.
