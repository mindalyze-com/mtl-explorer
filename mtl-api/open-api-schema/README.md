# open-api-schema

Contains the live-exported OpenAPI schema for the MTL Explorer server API.

## File

`schema.json` — the API contract. This is the source of truth for all generated client stubs.

## Updating the schema

The schema is exported from the running Spring Boot server (via SpringDoc):

```bash
# Server must be running on localhost:8080
curl --fail --silent --show-error http://localhost:8080/mtl/v3/api-docs -o schema.new.json &&
  python3 -m json.tool --indent 2 schema.new.json schema.validated.json &&
  mv schema.validated.json schema.json &&
  rm schema.new.json
```

Or browse the live Swagger UI at http://localhost:8080/mtl/swagger-ui/index.html and download from there.

Keep `servers` at `http://localhost:8080/mtl`; configure the export server to
advertise that URL. Never hand-edit generated API shapes.
Always save the schema with two-space indentation and a trailing newline.

After updating, regenerate the TypeScript client — see [the API workflow](../README.md).
