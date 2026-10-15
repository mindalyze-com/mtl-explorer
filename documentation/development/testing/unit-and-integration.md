# Unit and integration checks

Run Java checks in the existing development container. Use the Maven lifecycle so diagnostics and consumers rebuild together. Backend database tests use the [standard database settings](../../../docker-compose.yml) with isolated storage and credentials.

Frontend tests run through `npm test` in `mtl-client/`. Select the affected Vitest suites for a focused check, then run type checking and the relevant build.

Test observable contracts: supported and unavailable measurements, input/output shapes, request cancellation, boundaries, and failure recovery. Do not add tests that only repeat implementation statements.

When API shape changes, export the live OpenAPI schema, regenerate its client, and verify server/client agreement. See [the API workflow](../api-generation.md).
