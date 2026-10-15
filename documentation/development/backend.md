# Backend development

The Spring Boot application lives in `mtl-server/`. Maven owns compilation, packaging, and tests. Run these commands inside the existing development container, from `mtl-server/`:

```sh
mvn test
mvn package
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The diagnostics module compiles during the Maven lifecycle and is required at runtime. Use the configured module path; see [GPS diagnostics](gps-quality-modules.md).

Use an isolated database and public/synthetic data for tests. Follow [the testing guide](testing/README.md) and preserve pre-existing failures separately from results for the change.

After API changes, export the live schema and regenerate the client through [the API workflow](api-generation.md). Source and runtime settings are owned by the POM and application configuration.

## Implementation

Keep advanced domain calculations on the server. Change APIs rather than
duplicating calculation logic in the client. Prefer existing repositories and
standard framework features over separate persistence helper classes.

Format code and organize Java imports using the project style. Limit cleanup
to code changed for the task. Follow [shared code rules](README.md#shared-code-rules)
and [software version policy](README.md#software-versions).

## Logging

Log major server operations and lifecycle phases at `INFO`: start, meaningful
milestones or phase summaries, completion, duration, and useful counts. Keep
per-record and other high-volume detail at `DEBUG`.

Log every caught exception with operation context, its type, and a safe message
before recovering, falling back, or returning. Do not silently ignore exceptions
or use empty catch blocks. Use `WARN` for recoverable failures and `ERROR` with
the throwable when the operation fails. Keep repeated recoverable stack traces
at `DEBUG` to avoid flooding normal logs.

## Data migration

Do not add legacy-data backfills or old-data migration paths unless explicitly
requested. Assume users can recreate the database after derived-statistic or
data-shape changes. Keep ingest and recalculation for new imports; avoid
migration-only administration endpoints and services.
