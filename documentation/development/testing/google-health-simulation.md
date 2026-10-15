# Google Health import simulation

The simulator calls Java objects directly. It uses no HTTP, OAuth, Mockito or Spring application startup. The real importer, point mapper, PostgreSQL repository, transactions and database migrations remain in use.

`GoogleHealthProvider` is the data-query contract. Production `ApiClient` and test `SyntheticGoogleHealthProvider` implement it. Exercise exports and route work have separate interfaces. `SyncService` accepts a Java `Clock`; production uses UTC wall time and the simulation fixes the historical cutoff.

## Dataset

The calibrated benchmark delivers **2025-12-11 through 2026-01-01**, with the end excluded: 21 days and 524,400 samples. The original two-year dataset remains available through `standard()` for correctness and larger-scale experiments. Only raw heart rate contains data:

- One synthetic band, sampled every three seconds.
- A band-off gap every seven days, with fixed random seed **73421**. Gaps start at a random time on the first day of each seven-day period and last 2–36 hours, including gaps across midnight. The benchmark has three gaps.
- String BPM, physical timestamps, UTC offsets, civil date/time and device metadata. Europe/Zurich offsets follow daylight-saving transitions.
- Resource names are absent by default, matching the sampled Google record structure. Smaller correctness cases also exercise named records and multiple sources.

The provider allocates one page at a time. Samples are stable across overlapping queries and worker restarts. No NAS records or credentials are stored in fixtures. `transformSamples`, `beforeRead` and `responseDelay` support corrections, removals, expired tokens, quota pauses and delayed reads. `expectedSamples` provides the expected count after gaps.

## Run

Run Maven in the existing development container. Build a current source snapshot under its external-drive workspace mount, as with other backend checks.

Provider checks need no database:

```sh
mvn -Dtest=SyntheticGoogleHealthProviderTest test
```

Database checks require a disposable PostGIS database whose name starts with `mtl_health_simulation`. The harness resets its Google Health mirror. Provision a separate database named `mtl` in the same isolated cluster: an existing production migration grants a read-only role access to that literal database name. Use the existing development PostGIS image with the [standard database settings](../../../docker-compose.yml), and stop the temporary container after checks.

Pass these environment variables into the development container:

```sh
export MTL_HEALTH_TEST_DB_URL=jdbc:postgresql://localhost:5432/mtl_health_simulation
export MTL_HEALTH_TEST_DB_USER=health_simulation
export MTL_HEALTH_TEST_DB_PASSWORD=health_simulation
mvn -Dtest=HistoricalHeartRateImportSimulationTest test
```

These tests import a short portion of the standard dataset and smaller recovery fixtures. They check finalization, persisted page resumption and expired-token splitting. Correction and removal cases use explicit reloads. `HistoricalBatchImportSimulationTest` also checks unchanged-row retries, retained rows after empty answers, durable duplicate validation and atomic rollback of pages and finalization. Database checks skip when the URL is absent.

Compile once before benchmarking, so compilation is outside the timing:

```sh
mvn -DskipTests test-compile
```

The benchmark is opt-in and bounded to one minute. Run this inside the development container:

```sh
timeout --kill-after=1s 59s mvn -q \
  -Dtest=HistoricalHeartRateImportBenchmarkTest \
  -Dmtl.health.simulation.benchmark=true surefire:test
```

The benchmark truncates the disposable Health schema and resets sequences before each import. The importer gets a 57-second cycle budget and the test has a 60-second timeout. A run must finalize all 21 historical days and store exactly 524,400 samples. An interrupted or incomplete run fails and is not a performance result. The command above also limits the entire prepared test invocation, including JVM startup and cleanup.

Output is a JSON report with dates, seed, workload fingerprint, row/day counts, elapsed time, provider generation time, transaction time, commits and read count. Elapsed time covers import and final count verification. Transaction time includes all work inside transactions; it is not pure SQL execution time. Automatic history normalizes pages before the write transaction, so compare total elapsed time as well as transaction time across implementations. Provider byte counters are zero because no responses are decoded. Add `-Dmtl.health.simulation.report=/workspace/<external-drive-workspace>/baseline.json` to save the report.

The fixed workload fingerprint is `80b9e1ec43e0d8ab977a86d56b0b4d556d394ff07739283ed2b671c1318ab7d7`. Keep the dates, seed, sample interval, page size and provider implementation unchanged when comparing import improvements. Do not automatically resize the workload to fill the minute after an optimization. `-Dmtl.health.simulation.history-days=<days>` is an explicit calibration override and changes the workload fingerprint.

Tests clear their mirror after the class finishes. The benchmark also truncates its data after timing. Add `-Dmtl.health.simulation.keep-data=true` to a standalone benchmark run to retain its database rows for further experiments. Do not combine that option with other database suites.

Other granted streams return empty pages, so the runner includes the production scheduler's empty-stream work. It does not reproduce Google network latency, response parsing, quotas or NAS disk performance. Use identical settings and storage when comparing algorithm changes. Keep database files and build output on the mounted external drive.
