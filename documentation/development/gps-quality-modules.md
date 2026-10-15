# GPS diagnostics module

Diagnostics is a named Java module in `mtl-server`. Its sources keep the `com.x8ing.mtl.server.gpsquality.diagnostics` package; its descriptor is in `src/main/modules/diagnostics`. The application depends on diagnostics; diagnostics depends only on the JDK. The repair module has been removed.

`GpxTimestampParser` gives review and import the same strict clock interpretation. Missing offsets follow the GPX UTC convention and are logged as assumed UTC; original source text is retained. Import normalizes accepted values before JPX and leaves invalid timestamps absent without dropping geometry. Calendar overflow, leap seconds, excessive precision, and non-GPX offsets remain invalid; exact `24:00:00` means the next midnight.

Maven compiles diagnostics during `generate-sources` and the application during `compile`. Separate output directories enforce the boundary even when tests are skipped. Lifecycle initialization clears module outputs and consumer `.class` files while preserving application resources, so incremental builds cannot retain removed modules or callers of removed APIs. Use lifecycle goals rather than direct compiler goals.

`mvn test` uses the module path. `GpsQualityModuleIsolationTest` verifies runtime descriptors and rejects application dependencies. Enable `GpsQualityModuleBuildIntegrationTest` with `-Dmtl.test.jpms.build=true` to verify consumer recompilation without `clean`.

`mvn package` produces the Boot JAR and the diagnostics JAR under `target/gpsquality`; Docker runs both. Core classes are excluded from the Boot JAR. Run Java commands in the existing development container. For a packaged run from `mtl-server`:

```sh
java --module-path target/gpsquality \
  --add-modules com.x8ing.mtl.gpsquality.diagnostics \
  -jar target/mtl-server-1.0.0-SNAPSHOT.jar
```

Maven `spring-boot:run` is configured with the compiled module path. IDE launches require Maven compilation and equivalent VM arguments; startup rejects classpath-only diagnostics. Keep Spring, filesystem access and persistence in application adapters. Do not add module readability bypasses.
