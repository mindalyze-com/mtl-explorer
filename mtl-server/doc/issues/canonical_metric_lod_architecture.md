# Canonical Metrics and Downsampled Display Variants

Status: Open
Date: 2026-05-17
Updated: 2026-05-18

## Problem Statement

The GPS processing pipeline has grown into several overlapping concepts:

- canonical full-density track data
- geometry-simplified map shapes
- fixed-size chart/display point sets
- point-level derived metrics
- track-level summary metrics

The current direction is correct in spirit: compute metrics on
`RAW_OUTLIER_CLEANED` and avoid deriving user-facing values from display-sized
data. However, the implementation still mixes two different ideas:

1. A downsampled point series is useful for drawing a chart cheaply.
2. A downsampled point series is not, by itself, a metric-preserving summary.

When reducing a dense track to 1500 or 750 points, simply selecting one point
per bucket can miss important facts inside the bucket:

- a local maximum speed may fall between selected points
- a local maximum 30-second power value may be skipped
- averages change if calculated as "average per retained point"
- min/max altitude can be missed
- short spikes or dips can disappear
- cumulative values are safe only if read from the canonical cumulative series,
  not recomputed from the sampled display geometry

This means the rule must be stricter:

> A display variant may draw a sampled representation, but it must never be the
> source of truth for metrics.

## Current State

### Good

`RAW_OUTLIER_CLEANED` is the intended canonical metric stream.

`SIMPLIFIED_FIXED_POINTS` is built from canonical points and copies many
already-computed values. This is better than recomputing metrics after
downsampling.

Track-level metrics are increasingly being moved onto `GpsTrack`, where they
can be computed once from the canonical dense stream. These are already computed
from `RAW_OUTLIER_CLEANED` exclusively — keep it that way.

### Not Clean Yet

`SIMPLIFIED_FIXED_POINTS` is still a sample, not a rollup. A sampled point can
carry correct facts for that timestamp, but it cannot represent all facts that
happened between this point and the next retained point. Storing it as a
persisted `GpsTrackData` row alongside the canonical variant means there are two
copies of metric fields that can drift apart. This is the root cause of
recurring drift bugs.

`SIMPLIFIED_SHAPE` is geometry-first. It is produced by spatial simplification
and then has point data populated from the simplified geometry. Any metric
computed on that stream is not canonical. Currently `populatePointData()` is
still called on `SIMPLIFIED_SHAPE`, computing window speed/slope/elevation
rates on geometry-simplified vertices — these are non-authoritative and wasteful.

`EnergyService.recalculateEnergyForTrack()` currently iterates all variants and
recalculates energy for each variant. That risks overwriting copied canonical
display values with values derived from sparse/simplified variants.

Some comments and mental models still treat `SIMPLIFIED_FIXED_POINTS` as
"accurate enough" for more than display. It is not. It is accurate only at the
retained timestamps, unless it carries explicit bucket aggregates.

## Why 1500 or 750 Points Cannot Be Fully Correct

Assume a 4-hour track sampled at 1 Hz:

- canonical points: about 14,400
- 1500-point display: about 1 retained point every 9.6 seconds
- 750-point display: about 1 retained point every 19.2 seconds

Even with uniform time selection, many events can sit between retained points.
For line drawing this is acceptable. For metrics it is not.

Examples:

| Metric | Problem with sampled points |
|---|---|
| Max speed | The fastest 30-second window may not align with a retained point. |
| Max power | Peak 30-second power can be skipped between selected points. |
| Average speed | A plain average over retained point speeds is not time-weighted. |
| Elevation gain rate | The max bucket value can be missed if only one point is selected. |
| Min/max altitude | A summit or low point can be dropped. |
| Slope | Local extrema disappear unless min/max are preserved. |
| Energy | Segment energy changes if recalculated between sparse retained points. |

Therefore, a fixed-point display series is only a drawing optimization. It is
not a replacement for canonical metrics or bucket aggregates.

## Clean-Slate Architecture

Use four explicitly separate data products. The key principle: **display LODs
are derived views computed on demand, not stored variants with copied fields**.
Two stored copies of the same metric field always drift eventually.

### 1. Raw Import

Purpose: audit/debug only.

Stores source-ish GPS data before cleanup. It should not drive normal metrics.

### 2. Canonical Metric Stream

Purpose: the only source of truth for metric calculation.

This is `RAW_OUTLIER_CLEANED`:

- outlier removed
- stop drift collapsed
- elevation denoised
- stable timestamps retained
- all point-level derived metrics computed once

Canonical point fields should include:

- timestamp
- lat/lng/alt
- cumulative distance
- cumulative duration
- cumulative ascent/descent
- physics smoothing window: speed, slope, elevation rates (90-second symmetric moving window, see below)
- energy and power fields
- stop/event markers where useful

The trailing-window display fields (`speedInKmh30s`, `powerWatts30s`,
`elevationGainPerHour30s`, `elevationLossPerHour30s`) are **not** canonical
stored fields in the target architecture. They are computed dynamically by the
chart-series endpoint with a configurable `windowSec` parameter. See the
*Generic Window Support* section.

Track-level summaries must be computed from this stream only. They already are —
the constraint is to keep it that way.

### 3. Geometry LOD

Purpose: map line rendering and spatial queries.

This is `SIMPLIFIED_SHAPE`.

It should contain geometry and a back-pointer array only. Each vertex should
carry a `canonicalPointIndex` referencing the corresponding `pointIndex` in
`RAW_OUTLIER_CLEANED`. This replaces the current implicit array-index alignment
between the rendered shape and a same-variant points list used by
`fetchTrackPointsForRenderedShape`.

`SIMPLIFIED_SHAPE` must not have authoritative speed, power, ascent, slope,
energy, or elevation-gain-rate metrics. A map click resolves to a
`canonicalPointIndex` and fetches the canonical point for metrics.
`populatePointData()` must not be called on `SIMPLIFIED_SHAPE`.

### 4. Chart LOD

Purpose: render charts while preserving visible truth.

This must not be modeled as "1500 selected points". It is a server-computed,
rich bucket aggregate view over `RAW_OUTLIER_CLEANED`, computed on demand and
never persisted as a `GpsTrackData` variant.

The server deliberately returns more information than a single chart currently
needs. The client may choose which facts to draw, but it must not derive
authoritative metrics from sampled display points. This follows the same broad
model used by serious analysis tools: canonical activity samples remain the
metric source, and aggregation semantics depend on metric type. Peaks preserve
maxima, averages are duration-aware, and cumulative values are represented as
first/last deltas or sums.

Each bucket has shared domain metadata:

- bucket index
- x start and x end
- canonical point count
- duration seconds
- first and last timestamp
- first and last distance
- optional representative canonical point index for cursor/tooltips

Each metric then declares which aggregate facts it returns:

- first value and last value
- min value and where it occurred
- max value and where it occurred
- time-weighted average where meaningful
- delta for cumulative values
- sum/integral where meaningful

The response is bucket-oriented, not independent arrays that can drift out of
alignment. Every bucket carries the metric facts for the same x-domain span.

#### Aggregation Rules per Field Type

Aggregation semantics are encoded as a first-class `FieldAggregator` enum in the
bucketing service. This prevents ad-hoc per-field rules from accumulating in
disconnected places. New field → register it once; it cannot be forgotten.

```java
enum FieldAggregator {
    FIRST_LAST_DELTA,        // cumulative distance, ascent, descent, energy
    TIME_WEIGHTED_STATS,     // direct point/segment values: avg/min/max/first/last
    WINDOWED_METRIC_BUCKET,  // trailing-window display metrics with bucket stats
    EXTREMA_STATS,           // elevation, slope: min/max/avg/first/last
    EVENT_BOUNDARY           // stops and other event boundaries
}
```

`WINDOWED_METRIC_BUCKET` is the key new aggregator for windowed display
metrics. It runs a sliding trailing window of `windowSec` duration over
canonical points as they are assigned to buckets. Per bucket it emits:

- `avg` - time-weighted average of all window values in the bucket
- `min` / `max` - extrema seen across all canonical points in the bucket span
- `first` / `last` - boundary values for line continuity
- `minAt` / `maxAt` - timestamp/distance/index where extrema occurred

This is the parameterized replacement for the hardcoded
`calculateThirtySecondStats()` pass.

| Value type | Aggregator |
|---|---|
| Cumulative distance / energy / ascent / descent | `FIRST_LAST_DELTA` - emit first, last, delta |
| Speed (windowed display) | `WINDOWED_METRIC_BUCKET` - trailing window of `windowSec`; emit avg/min/max/first/last |
| Power (windowed display) | `WINDOWED_METRIC_BUCKET` - trailing window of `windowSec`; emit avg/min/max/first/last |
| Elevation gain/loss rate (windowed) | `WINDOWED_METRIC_BUCKET` - trailing window of `windowSec`; emit avg/min/max/first/last |
| Elevation | `EXTREMA_STATS` - emit min/max/avg/first/last; preserve peaks and valleys |
| Slope | `EXTREMA_STATS` - emit min/max/avg/first/last; preserve extrema |
| Events / stops | `EVENT_BOUNDARY` - preserve event boundaries explicitly |

**Stop policy for windowed metrics**: trailing-window accumulators for speed
and power exclude stopped segments. This uses the existing stop-collapse data
already present on canonical points. A `movingAvg` label should be used in the
response to distinguish moving-only values from naive point-averages. The exact
moving/stopped denominator must be implemented in the server aggregation service,
not inferred by the client.

The client can then draw:

- average line for readability
- min/max envelope where useful
- explicit max markers for peaks
- exact start/end/cumulative values

#### Two X-axis Modes

The endpoint must support both `x=time` and `x=distance` (for elevation
profiles). The bucketer is identical; only the bucket key differs. Do not bake
`time` into response DTOs. Range requests must load enough canonical context
before the visible `from` boundary to compute trailing-window values consistently
at the beginning of the visible range.

#### Implementation Choice: Java First, Not SQL/PostGIS

The first implementation should do chart bucketing in Java, not in PostGIS or
large SQL expressions.

Reasons:

- the existing metric semantics already live in Java
- the code already understands canonical points, stops, cumulative fields,
  energy, speed, elevation rates, and activity-specific behavior
- Java is easier to test with focused unit tests
- Java lets us reuse existing model classes and helper logic instead of
  duplicating metric rules in SQL
- database-side bucketing can be revisited later only if measured performance
  requires it

The database should provide ordered canonical rows efficiently. Java should own
the aggregation semantics.

Target shape:

```text
Controller
  GET /api/tracks/{id}/chart-series?x=time|distance&maxBuckets=1500&windowSec=30&metrics=&from=&to=

Service
  load RAW_OUTLIER_CLEANED points ordered by pointIndex
  optionally restrict by time/distance range, with window look-behind
  iterate once through the canonical rows:
    assign each canonical point to a bucket by x-mode key
    advance WINDOWED_METRIC_BUCKET accumulators for windowed fields (windowSec)
    advance bucket-level aggregators for other fields
  return rich bucket DTOs
```

Performance should be handled incrementally:

- start with Java aggregation over canonical points
- add repository methods that restrict by timestamp or distance range
- stream rows or use paging if very large tracks become a problem
- avoid moving aggregation into SQL unless profiling proves Java is too slow

Important implementation constraint:

> Reuse existing Java calculation and point-model code wherever possible. Do not
> create a second copy of metric formulas in SQL or in frontend code.

## Recommended Target Rule

Every field must have one declared semantic category:

| Category | Examples | Allowed source |
|---|---|---|
| Canonical point fact | smoothedSpeed at timestamp T (90-second window), instantaneous power at T | `RAW_OUTLIER_CLEANED` only |
| Canonical track summary | normalizedPower, max ascent, min/max altitude | aggregate over `RAW_OUTLIER_CLEANED` only |
| Display bucket aggregate (window) | speedWindow avg/min/max/first/last within bucket, powerWindow avg/min/max/first/last within bucket | trailing-window over canonical points, `windowSec` param |
| Display bucket aggregate (spatial) | elevation avg/min/max/first/last inside bucket, slope extrema inside bucket | aggregate over canonical points in bucket |
| Geometry-only | simplified line coordinate + canonicalPointIndex | `SIMPLIFIED_SHAPE` |

The `SIMPLIFIED_FIXED_POINTS` category is eliminated. There is no stored display
variant. Display series are always derived on demand.

No code path should compute user-facing metrics from `SIMPLIFIED_SHAPE` or from
sparse retained point pairs.

## Generic Window Support

The codebase currently uses two distinct window types serving different purposes.

### Physics Smoothing Window (90 s, stored in canonical stream)

`speedInKmhMovingWindow`, `elevationGainPerHourMovingWindow`, and
`slopePercentageInMovingWindow` are computed with a 90-second symmetric window
during track ingest. They are *inputs* to the energy physics calculations:
aero drag uses smoothed speed; kinetic energy uses the velocity change over the
window. These must stay in the canonical stream as stored point fields.

The 90-second duration is an activity-level constant set once at ingest.
It is not configurable per API request.

### Display Window (currently hardcoded 30 s, should be a parameter)

`speedInKmh30s`, `powerWatts30s`, `elevationGainPerHour30s`, and
`elevationLossPerHour30s` are trailing-window display series, currently computed
by `calculateThirtySecondStats()` in `GPXStoreService` and stored as canonical
point fields. This is wrong for two reasons:

1. The window size is hardcoded to 30 s everywhere: the constant, the method
   names, and the column names.
2. These are chart display concerns baked into the canonical data model, which
   forces a reingest any time someone wants a different window.

#### Target: `windowSec` parameter on the chart-series endpoint

The chart-series endpoint accepts a `windowSec` parameter (default: 30,
allowed range: 5–300 s). The bucketer computes trailing-window series in a
single O(N) pass over canonical points using the `WINDOWED_METRIC_BUCKET`
aggregator — the same sliding-window algorithm already in
`calculateThirtySecondStats()`, parameterized.

```text
GET /api/tracks/{id}/chart-series?x=time&maxBuckets=1500&windowSec=30
```

Response series keys are named by metric type, not by window size. The window
size is carried in the response envelope so the client can label axes correctly.
The response is bucket-oriented: every metric object belongs to the same bucket
span and can be used by the client without re-deriving metric truth.

```json
{
  "trackId": 123,
  "source": "RAW_OUTLIER_CLEANED",
  "xMode": "time",
  "bucketCount": 1500,
  "windowSec": 30,
  "buckets": [
    {
      "index": 0,
      "xStart": 0.0,
      "xEnd": 9.6,
      "count": 14,
      "durationSec": 9.6,
      "firstTime": "...",
      "lastTime": "...",
      "firstDistanceM": 0.0,
      "lastDistanceM": 42.7,
      "representativeCanonicalPointIndex": 12,
      "metrics": {
        "speedWindowKmh": {
          "avg": 12.3,
          "min": 4.1,
          "max": 31.4,
          "first": 10.9,
          "last": 13.2,
          "minAt": "...",
          "maxAt": "..."
        },
        "powerWindowW": {
          "avg": 185.0,
          "min": 80.0,
          "max": 310.0,
          "first": 170.0,
          "last": 195.0,
          "maxAt": "..."
        },
        "elevationM": {
          "avg": 514.1,
          "first": 512.0,
          "last": 516.2,
          "min": 511.8,
          "max": 518.4,
          "minAt": "...",
          "maxAt": "..."
        },
        "distanceM": {
          "first": 0.0,
          "last": 42.7,
          "delta": 42.7
        }
      }
    }
  ]
}
```

A client that wants 60-second averages for hiking requests `windowSec=60`. The
canonical stream does not change; only the in-request computation changes.

#### How the trailing-window bucketer works

The bucketer maintains a sliding window over canonical points as it assigns them
to buckets in a single forward pass:

1. Walk canonical points in `pointIndex` order.
2. Maintain a trailing-window accumulator (duration, distance, ascent, power)
   spanning the preceding `windowSec` of data. Advance the tail pointer when
   the window exceeds `windowSec`.
3. At each canonical point inside a bucket, record the current window value.
4. Track first/last/min/max window values and extrema locations within the
   bucket span.
5. Accumulate a duration-weighted numerator and denominator for the bucket
   average.
6. When the bucket boundary is crossed, emit avg/min/max/first/last and extrema
   metadata for every requested windowed metric.

This is O(N) per request and requires no pre-stored display columns.

#### Industry reference: Golden Cheetah

Golden Cheetah (the open-source cycling analysis tool) uses the same principle:
one canonical `RideFile` with `dataPoints()`, no stored display variants, and a
configurable `smooth` parameter applied at chart-render time in a single forward
pass over canonical points. Smoothed arrays (`smoothWatts[]`, `smoothSpeed[]`,
etc.) exist only in memory for the duration of the render; they are never
persisted. NP and other derived metrics are computed lazily from canonical on
demand. This is the desktop equivalent of the architecture described here.

GC validates the core thesis: display smoothing is a view concern and has never
belonged in stored data.

#### Wall-clock time tracking vs 1-Hz normalization

GC expands raw points into a dense 1-second integer-indexed array before
applying any smoothing. This makes the sliding window trivially correct in
wall-clock seconds (array index arithmetic) and handles recording gaps
automatically via carry-forward. It is a clean approach for a desktop app that
loads one ride into RAM.

For this server-side architecture, **full 1-Hz normalization is not planned**.
The disadvantages outweigh the simplicity gain here:

- Sub-second data (2 Hz / 4 Hz devices) would lose intra-second peaks, which
  matters for min/max extrema.
- A 6-hour hiking track at 1-point-per-minute expands to a 21,600-slot array
  (60× the canonical point count), wasting memory for slow-activity tracks.
- Different fields need different gap-fill policies (carry-forward for altitude,
  zero for power/speed during stops), which re-introduces per-field logic that
  `FieldAggregator` is supposed to centralize.

**Middle path — wall-clock time tracking over raw canonical points:**

Instead of normalization, the bucketer tracks elapsed wall-clock time explicitly:

- The trailing-window tail pointer advances when
  `currentPoint.timestamp − tailPoint.timestamp ≥ windowSec`.
  Duration is always compared in real seconds, not in point count.
- If the timestamp gap between two consecutive canonical points exceeds a
  configurable threshold (e.g. `> maxGapSec`, initially 10 s), the gap is
  treated as a stop. The trailing-window accumulator is reset at the gap
  boundary rather than stretching across it. This prevents a 2-minute GPS
  dropout from inflating the window value at the point immediately after
  reconnection.
- Stop segments (already flagged on canonical points via stop-collapse) are
  excluded from windowed-metric accumulators for speed and power, consistent
  with the moving-only policy documented above.

This gives exact wall-clock window durations over raw canonical points without
any array expansion, and makes the gap and stop policies explicit and testable.

#### Effect on canonical stored fields

Once the chart-series endpoint is the sole consumer of these display series,
`speedInKmh30s`, `elevationGainPerHour30s`, `elevationLossPerHour30s`, and
`powerWatts30s` can be removed from `GpsTrackDataPoint` and the ingest
pipeline. `calculateThirtySecondStats()` is deleted.

`powerWatts30s` currently also drives Normalized Power. NP must be computed
once at energy-calculation time from `RAW_OUTLIER_CLEANED` and stored as
`normalizedPowerWatts` on `GpsTrack`. After that, the per-point `powerWatts30s`
column has no remaining role and can be dropped.

#### Normalized Power stays 30 s by definition

Coggan's NP formula is defined over a 30-second rolling average. It is not
configurable. NP is computed at energy-calculation time, stored on `GpsTrack`,
and never re-derived from a chart request. A client requesting `windowSec=60`
gets a 60-second rolling power chart; the track's `normalizedPowerWatts` field
is always the standard 30-second NP.

#### Activity-specific default windows

| Activity | Default `windowSec` | Rationale |
|---|---|---|
| Cycling | 30 | matches power meter standard; FTP test protocol |
| Running | 30 | consistent with cycling; pace changes fast |
| Hiking / walking | 60 | slower motion; 30 s is noisy |
| Skiing / other | 30 | conservative default until tuned |

The server applies these defaults when `windowSec` is absent from the request.
The client may override via an explicit `windowSec` query parameter.

## Recommended Project Plan

The phase order matters. Introduce the reusable bucket/window aggregation
service first, expose it through the chart-series endpoint, switch consumers,
then remove the stored display variant and clean up energy service and geometry
LOD. Do not remove `SIMPLIFIED_FIXED_POINTS` or stop recomputing energy on it
before the frontend no longer reads it.

No data migration or backfill is needed. Users can reingest or recreate the
database after the data-shape change. Do not write any admin endpoint or service
whose only purpose is migrating existing rows.

### Phase 1: Audit and Guard Rails

Find every consumer of `gps_track_data_points` and classify it:

- canonical metrics
- chart display
- table display
- map display
- map point popup
- measure/crossing analysis
- energy recalculation
- exploration/spatial query

Add an ArchUnit rule (or equivalent enforced test) that prohibits any service
outside a designated `audit` package from reading a variant other than
`RAW_OUTLIER_CLEANED` for metric computation. Comments alone do not enforce the
rule; a failing test does.

### Phase 2: Introduce Bucket Aggregation Service

Add a Java service backed by `RAW_OUTLIER_CLEANED` that owns all rich bucket
aggregation semantics:

- one bucket timeline per request
- shared bucket metadata: x range, time range, distance range, count, duration
- field-specific aggregators via the `FieldAggregator` registry
- `WINDOWED_METRIC_BUCKET` support for avg/min/max/first/last and extrema
  metadata
- optional `metrics=` filtering so clients can request only the facts they need
- range look-behind so trailing-window values are stable at visible boundaries

This service is the metric source for chart LODs. It must be unit-testable
without a controller.

### Phase 3: Introduce Chart-Series Endpoint

Introduce the chart LOD API backed by `RAW_OUTLIER_CLEANED`:

```text
GET /api/tracks/{id}/chart-series?x=time|distance&maxBuckets=1500&windowSec=30&metrics=&from=&to=
```

Return typed rich buckets using the aggregation service. Do not expose raw
`GpsTrackDataPoint` rows as a metric-preserving reduction. See the *Generic
Window Support* section for the full response shape and window semantics. The
`windowSec` parameter controls `WINDOWED_METRIC_BUCKET` series; other aggregators
(cumulative, elevation) are unaffected by it.

`count` and `durationSec` per bucket are required so downstream clients can make
correct display choices. `maxAt` / `minAt` preserve extrema locations for
tooltip pinning.

Because this changes the public API, update the server DTOs first, start the
updated server, download the live OpenAPI schema from `/mtl/v3/api-docs`, and
regenerate the frontend TypeScript client from `mtl-api/mtl-api-typescript-fetch`
before using the endpoint in frontend code.

### Phase 4: Switch Frontend to Chart-Series

Update `TrackGraph.vue` and the track details table to consume the chart-series
endpoint instead of the `SIMPLIFIED_FIXED_POINTS` point list. The overview
panel already uses `GpsTrack` canonical summaries and does not need to change.
Cursor sync, minimap sync, and tooltip lookup must use bucket metadata or a
canonical point lookup, not sampled display point indexes.

### Phase 5: Remove SIMPLIFIED_FIXED_POINTS

Once no frontend consumer reads `SIMPLIFIED_FIXED_POINTS`, stop generating and
persisting it. Remove the generation code in `GPXStoreService` and the
corresponding `GpsDataVariant` enum member. Drop the stored rows on next
reingest; no migration service is needed.

This eliminates the entire class of drift bugs caused by maintaining two copies
of metric fields.

### Phase 6: Energy Service — Canonical Only

With `SIMPLIFIED_FIXED_POINTS` gone, `EnergyService.recalculateEnergyForTrack()`
can be simplified:

- calculate energy only on `RAW_OUTLIER_CLEANED`
- write track-level summary from that result
- remove the per-variant loop entirely — there is nothing else to update
- do not recalculate energy from `SIMPLIFIED_SHAPE`

This phase becomes largely mechanical once Phase 5 is complete.

### Phase 7: Simplified Shape Cleanup

Add `canonicalPointIndex` to each `SIMPLIFIED_SHAPE` vertex. Switch the map
popup to resolve via canonical lookup using that index. Then:

- remove or do not populate derived metric fields on `SIMPLIFIED_SHAPE`
- remove the `populatePointData()` call for the `SIMPLIFIED_SHAPE` variant
- keep geometry and `canonicalPointIndex` only

Do not overload the current LineString M coordinate with `canonicalPointIndex`;
it already carries timestamp. Use an explicit typed geometry DTO or a dedicated
point field.

### Phase 8: Remove Stored 30-second Display Columns

Once the chart-series endpoint is the sole consumer of windowed display series,
remove the stored per-point columns from `GpsTrackDataPoint`:

- `speedInKmh30s`
- `elevationGainPerHour30s`
- `elevationLossPerHour30s`
- `powerWatts30s`

Delete `calculateThirtySecondStats()` from `GPXStoreService`. Verify that
`normalizedPowerWatts` is already stored on `GpsTrack` before removing
`powerWatts30s`; NP must be computed and persisted at energy-calculation time
(`computeThirtySecondPowerStats()` stays in `EnergyService` but only for NP,
not for per-point storage).

Track-level 30-second maxima on `GpsTrack` may remain as canonical summaries
(`speedInKmh30sMax`, `elevationGainPerHour30sMax`,
`elevationLossPerHour30sMax`, `powerWatts30sMax`) as long as they are computed
from `RAW_OUTLIER_CLEANED` through the reusable window service. Removing
per-point display columns does not mean removing canonical track summary fields.

Drop the columns on next reingest; no migration service is needed.

## Acceptance Criteria

- No user-facing summary metric is computed from `SIMPLIFIED_FIXED_POINTS`.
- No user-facing metric is computed from `SIMPLIFIED_SHAPE`.
- `SIMPLIFIED_FIXED_POINTS` is not generated or persisted as a stored variant.
- Energy is computed once from canonical points only; no per-variant energy
  recalculation loop exists.
- A 750/1500 chart response returns rich bucket facts (avg/min/max/first/last
  where meaningful) computed from canonical points.
- A 750/1500 chart response preserves local min/max values inside every bucket.
- Re-rendering the same track at 750, 1500, or full density keeps all summary
  metrics identical.
- Map popups resolve metrics through `canonicalPointIndex`, not through a
  same-variant array-index lookup.
- Requesting `windowSec=30` and `windowSec=60` on the same track produces
  identical `normalizedPowerWatts` (stored on `GpsTrack`) but different
  `powerWindow` chart series.
- Stored columns `speedInKmh30s`, `powerWatts30s`, `elevationGainPerHour30s`,
  `elevationLossPerHour30s` do not exist in the final schema.
- `calculateThirtySecondStats()` does not exist in the final codebase.

### Regression Test Fixture

Add a parameterized test class using a synthetic fixture (4-hour track, 1 Hz,
with at least one 1-second spike and one 20-second plateau):

- for every field with a `WINDOWED_METRIC_BUCKET` aggregator
- for every bucket count in `{full, 1500, 750, 500}`
- for every window size in `{30, 60, 120}`
- assert the reported `max` equals the canonical max (the spike must be found
  regardless of window size or bucket count)
- assert that `avg` with `windowSec=60` is never equal to `avg` with
  `windowSec=30` on a non-uniform fixture (proves the window is actually applied)
- assert that `avg` is duration-weighted, not a plain mean of retained samples
- assert that range requests with `from`/`to` match full-track results at the
  same visible bucket after applying the required window look-behind

This single test class is the mechanical regression guard that prevents this
issue from recurring.

## Priority

High for correctness and long-term maintainability.

The recent fixes move overview metrics and 30-second display series in the right
direction, but they do not fully solve metric-preserving downsampling. The
remaining work should be treated as an architecture cleanup project, not as a
small chart tweak.
