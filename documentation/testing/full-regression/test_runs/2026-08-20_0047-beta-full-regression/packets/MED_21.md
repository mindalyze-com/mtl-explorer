# Packet: MED_21

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_21
- In scope: Spatial bounds and Track Details timeline query plans and HTTP timings with at least 100,000 media rows and 300 activity rows.
- Out of scope: UI pagination and selection on the large dataset, covered by MED_28 and MED_33.

## Prerequisites

- Required previous coverage IDs or run packets: MED_20 cleanup.
- Required app/data state: Eight-media baseline, empty correlation queues, and disposable database access.
- Required browser context: Authenticated app session; HTTP checks use a separate authenticated disposable session.

## Allowed Mutations

- Allowed: Directly seed 100,000 synthetic media/correlation/resolved rows and 300 synthetic activities, measure read paths, then remove every synthetic row.
- Not allowed: Modify the original eight-media fixture, retain synthetic performance rows, or trigger full-library correlation work.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_21 | Seed the required volume; run `EXPLAIN ANALYZE` for exact map-bounds and persisted timeline SQL; time five authenticated HTTP requests per endpoint; remove the seed. | Map bounds use the spatial resolved-position projection index; timeline uses indexed selected correlations; requests do not interpolate the full media library. | All required rows loaded. Map used `media_resolved_location_ix_location` and returned 5,100 points in 6.226 ms SQL execution. Timeline used `media_track_correlation_ix_track_timeline` for 334 target rows in 8.068 ms. HTTP means were 26.146 ms and 26.876 ms, all 200 with `no-store`. Cleanup restored exact baseline counts and empty queues. | PASS | [assets/MED_21-performance.txt](../assets/MED_21-performance.txt); [assets/MED_21-explain.sql](../assets/MED_21-explain.sql); [assets/MED_21-seed.sql](../assets/MED_21-seed.sql); [assets/MED_21-cleanup.sql](../assets/MED_21-cleanup.sql) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_21-performance.txt](../assets/MED_21-performance.txt) | Dataset counts, indexed plan summary, HTTP timings, and cleanup verification. |
| [assets/MED_21-explain.sql](../assets/MED_21-explain.sql) | Exact production query shapes used for `EXPLAIN (ANALYZE, BUFFERS)`. |
| [assets/MED_21-seed.sql](../assets/MED_21-seed.sql) | Deterministic synthetic large-library seed. |
| [assets/MED_21-cleanup.sql](../assets/MED_21-cleanup.sql) | Bounded removal and statistics refresh for the synthetic seed. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; this database and HTTP performance packet uses linked query evidence.

## Timings

| Step | Timing |
|---|---:|
| Seed 200,300 domain rows | 6.211 s |
| Map SQL execution | 6.226 ms |
| Timeline SQL execution | 8.068 ms |
| Map HTTP, five-run mean | 26.146 ms |
| Timeline HTTP, five-run mean | 26.876 ms |

## Handoff Notes

- Completed: Required-volume seed, spatial and timeline query plans, authenticated HTTP timing, cache-header checks, and bounded cleanup.
- Remaining unfinished coverage: None for MED_21.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04 and is not material to this query-performance packet.
- State left for the next packet: Synthetic MED_21 rows are absent; original media/resolved/selected counts are 8/8/8; manual locations, time corrections, and both work queues are empty.
