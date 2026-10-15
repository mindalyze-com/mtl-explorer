# Exploration Score

Exploration Score estimates the fraction of a track's supported path that is
new relative to earlier eligible archive tracks. It is a value from 0 to 1,
displayed as a percentage. It describes the imported archive, not all places
the person has visited.

## Calculation

[ExplorationScoreQueryRepository](../../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/jobs/exploration/ExplorationScoreQueryRepository.java) owns the
PostGIS query; [ExplorationScoreAtomicWorker](../../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/jobs/exploration/ExplorationScoreAtomicWorker.java)
stores the result.

1. Read the configured `SIMPLIFIED_SHAPE` precision's `observed_path`, which
   omits connecting edges across recording gaps.
2. Transform that path to EPSG:3857 and split its line segments to at most
   `corridor-width-m` projected meters.
3. Prepare each target segment's search bounds once. Filter eligible earlier
   track IDs using their maintained source bounds, then retrieve each selected
   shape by ID and project its observed path once. Reuse these candidates for
   the original geometry bounding-box and `ST_DWithin` checks. Combining the
   segment bounds keeps the target prefilter conservative at the antimeridian.
4. Sum unmatched segment lengths and divide by total supported length.
   Java clamps the ratio to `[0, 1]` and rounds it to three decimal places.

Prior tracks must have an earlier `start_date`, successful load, and
`UNIQUE` duplicate status. Both targets and prior tracks exclude
`review_excluded` and `timing_unreliable` recordings. Duplicate classification
and GPS indexing must settle before the job starts.

The query uses EPSG:3857 distances, not ground-distance geodesics. Projection
distortion, simplified geometry, the corridor threshold, and whole-segment
classification limit accuracy. A segment is considered known when any part
comes within the corridor; this is not an exact polygon difference.

Materialized SQL intermediate results avoid repeating archive searches and
coordinate transforms per segment. They can spill to temporary disk under the
existing database memory settings. The calculation keeps no Java archive
cache and does not skip scheduled targets based on duplicate status. Large
candidate sets still need runtime memory and disk capacity.

The source bounds are maintained during ingest. A named 0.001-degree prefilter
margin conservatively covers shape snapping to a 0.00001-degree grid, six-decimal
bound rounding, and spatial bounding-box precision. It does not widen the
scoring corridor: the original geometry predicates still determine coverage.

Two partial indexes support candidate selection: a date/bounds index for
eligible prior tracks, with ID included, and an ID index for the 10 m
`SIMPLIFIED_SHAPE` variant. Bounds are index keys so rejected candidates avoid
heap visibility checks. Matching candidates can still require heap reads after
updates. The default query uses fixed type and precision literals for generic
prepared plans; other precisions use bound parameters and the general ID index.

The lateral lookup's `OFFSET 0` keeps geometry retrieval per selected ID.
Migration 093 replaces the earlier exploration indexes: retaining that partial
spatial index can cause repeated area scans inside the per-ID lookup. The
existing general geometry index remains available. Regression tests cover the
actual migration and JDBC auto/custom/generic plans. Dense late tracks can still
favor the earlier spatial strategy; archive benchmarks should include them.

## Configuration

The shipped [application configuration](../../../mtl-server/src/main/resources/application.yml)
sets:

```yaml
mtl:
  exploration:
    corridor-width-m: 25.0
    use-track-precision: 10
    max-tracks-per-run: 20
    worker-threads: 1
    run-schedule: PT120S
```

The class fallback for `worker-threads` is 3, but the shipped configuration
uses 1 to avoid overlapping large geometry queries. Supported stored shape
precisions are 1, 10, 100, and 1000 meters. Choose a precision that is actually
generated; zero is not a raw-data mode for this query.

A full batch immediately continues through the scheduler's loop. The normal
delay applies after pending work drains or a guard stops processing.
Throughput depends on geometry size, archive density, and database resources;
batch size is not a runtime bound.

## State and invalidation

| Status | Meaning |
| --- | --- |
| `NOT_SCHEDULED` | Not eligible or no usable geometry/date for calculation. |
| `SCHEDULED` | Awaiting calculation. |
| `IN_PROGRESS` | Accepted when reading pending work; current workers keep the pending state until completion. |
| `CALCULATED` | A stored score is available. |
| `NEEDS_RECALCULATION` | Invalidated result or a calculation to retry. |

[ExplorationScoreJob](../../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/jobs/exploration/ExplorationScoreJob.java) checks for newly scheduled
historical imports and invalidates later overlapping calculated tracks through
a repository update. Source-review changes also invalidate affected nearby
scores and release duplicate relationships. Unrelated scores remain available.

The job compares corridor and precision settings with its stored `config`
snapshot. A change resets calculated scores for recalculation. Worker count,
batch size, and schedule do not change the score definition.

Each worker uses its own transaction and writes only exploration status, score,
and calculation date through targeted repository updates. The loaded track remains
unchanged. The write happens after the query, allowing other jobs to commit during
calculation. Missing eligible geometry clears the
score and sets `NOT_SCHEDULED`; calculation failures are logged and marked
`NEEDS_RECALCULATION`.

Only score, status, and calculation time are stored on `gps_track`. Multiplying
the score by a full-track distance is an estimate of new ground, not the exact
projected `novel_m` used internally by this query.
