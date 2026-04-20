# open-api-schema

Contains the OpenAPI 3.1.0 schema for the MTL server API.

## File

`schema.json` — the API contract. This is the source of truth for all generated client stubs.

## Updating the schema

The schema is exported from the running Spring Boot server (via SpringDoc):

```bash
# Server must be running on localhost:8080
curl http://localhost:8080/mtl/v3/api-docs | python3 -m json.tool > schema.json
```

Or browse the live Swagger UI at http://localhost:8080/mtl/swagger-ui/index.html and download from there.

After updating, regenerate the TypeScript client — see [`../readme.md`](../readme.md).
