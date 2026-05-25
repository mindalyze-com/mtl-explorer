# GPS Data Processing Pipeline

This document describes the current lifecycle of GPS data from an imported
track file to the data served to the UI.

The important split is:

- `RAW` is a stored import trace.
- `RAW_OUTLIER_CLEANED` is the canonical metric stream.
- `SIMPLIFIED_SHAPE` is for drawing and spatial queries.
- `SIMPLIFIED_FIXED_POINTS` is for chart/table display.

## 1. Stored Data Variants

Each successfully imported track segment creates rows in `gps_track_data`.
Each row also has matching `gps_track_data_points` rows.

| `trackType` | Discriminator | Description | Main consumers |
|---|---:|---|---|
| `RAW` | `precisionInMeter = 0` | Parsed source points before outlier cleanup. Exact `0,0` fixes are skipped, and missing elevation may be carried forward from the last known elevation, so this is not a byte-for-byte GPX copy. | Audit/debug only in normal app flow. |
| `RAW_OUTLIER_CLEANED` | `precisionInMeter = 0` | Outlier-removed, stop-collapsed, denoised full-density stream. This is the canonical source for track metrics. | Motion duration, stop events, energy totals, measure/crossing analysis, source for derived variants. |
| `SIMPLIFIED_SHAPE` | `precisionInMeter = 1, 5, 10, 50, 100, 500, 1000` | PostGIS Douglas-Peucker geometry simplification of `RAW_OUTLIER_CLEANED`, snapped to grid, with stop anchors preserved. | Map rendering, map point popups, geo SQL filters, nearby-track lookup, exploration score. |
| `SIMPLIFIED_FIXED_POINTS` | `maxPoints = 750 or 1500` | Time-uniform downsample of `RAW_OUTLIER_CLEANED`. Absolute fields, cumulative totals, moving-window metrics, and energy are copied from the cleaned full-density points; between-point deltas are recomputed between retained points. | Track Details charts, tables, minimap, lightweight per-point display. |

Source of truth: `GpsTrackData.TRACK_TYPE`.

## 2. Import And Processing Pipeline

```
Track file on disk
  |
  |  GPX files are read directly.
  |  FIT files are converted upstream before this pipeline.
  v
GPXReader.importGpxXml()
  |
  |  Parse via io.jenetics.jpx.
  |  Coordinates are CoordinateXYZM:
  |    X = longitude
  |    Y = latitude
  |    Z = elevation in meters
  |    M = epoch seconds
  |
  v
[PASS 1] filterOutliers()
  |
  |  For every waypoint:
  |    - exact 0.0,0.0 coordinates are ignored
  |    - missing elevation is replaced by the last known elevation
  |    - the resulting coordinate is appended to RAW
  |
  |  A point is plausible when:
  |    - missing timestamp: accepted, because speed cannot be checked
  |    - negative dt: rejected
  |    - same timestamp: accepted only when drift <= 5 m
  |    - normal timestamp: distance / min(dt, 120 s) <= 416 m/s
  |    - if dt > 120 s, distance must also be <= 5000 m
  |
  |  Implausible points enter a probation buffer:
  |    - if the suspect cluster stabilizes for >= 15 s, it may be promoted
  |    - without timestamps, >= 5 plausible probation points may be promoted
  |    - promotion is rejected if the cluster starts > 5000 m from the main
  |      track, except during startup or a > 12 h temporal gap
  |    - if the stream snaps back to the main track, probation points are
  |      discarded as outliers
  |
  |  A > 12 h temporal gap splits the cleaned output into separate imported
  |  segments, except for synthetic same-location stop anchor pairs that
  |  preserve one long stop inside the activity.
  |
  v
[PASS 2] BreakStopTreatment.apply()
  |
  |  Runs only on the cleaned stream. RAW is not modified.
  |
  |  2a. Isolated A-B-C spike removal:
  |    - A->B and B->C jumps must each be >= 90 m
  |    - A->C return distance must be <= 50 m
  |    - timestamps must be valid and each side must be <= 40 s
  |    - a following support point must return within 40 m, or the whole
  |      unsupported excursion must be <= 20 s
  |
  |  2b. Stop/break detection:
  |    - TrackStopDetector detects compact time-sufficient clusters
  |    - speed is not the primary signal, because stationary GPS drift can
  |      produce large low-speed scribbles
  |    - nearby fragments merge when the time gap is <= 120 s and centers
  |      are <= 100 m apart
  |
  |  2c. Stop collapse:
  |    - all points inside an accepted stop range are removed from the
  |      cleaned line
  |    - two synthetic anchor coordinates are inserted at the stop center:
  |      one at stop start time and one at stop end time
  |
  v
[PASS 3] configured denoiser
  |
  |  Default: MedianElevationSmoother
  |    - replaces Z with the median of neighbors within 50 m and 30 s
  |    - X/Y position and M timestamp are preserved
  |
  |  Optional: KalmanElevationSmoother when configured via
  |    mtl.denoise.algorithm=kalman
  |    - smooths horizontal and vertical state with a Kalman/RTS pass
  |
  |  The final result is stored as RAW_OUTLIER_CLEANED.
  |
  v
[PASS 4] populatePointData() on RAW and RAW_OUTLIER_CLEANED
  |
  |  Creates gps_track_data_points:
  |    - WGS84 pointLongLat
  |    - Web Mercator pointXY
  |    - timestamp and altitude
  |    - geodetic distance between points
  |    - duration between points and since start
  |    - cumulative distance, ascent, descent
  |
  |  Ascent/descent totals use a 2 m accumulation gate so small altitude jitter
  |  does not inflate cumulative climbing.
  |
  |  Moving-window metrics use a 90 s symmetric window:
  |    - speedInKmhMovingWindow
  |    - elevationGainPerHourMovingWindow
  |    - elevationLossPerHourMovingWindow
  |    - slopePercentageInMovingWindow
  |
  |  Track motion duration is derived from RAW_OUTLIER_CLEANED points where
  |  moving-window speed is >= 0.5 km/h.
  |
  |  Stop totals and stop events are mapped from the stop ranges detected
  |  during import. Stop classification is not re-run from speed later.
  |
  |  Energy is intentionally not computed at this moment. Activity type is not
  |  known yet, so energy is deferred until classification.
  |
  v
[PASS 5] calculateSimplified() for SIMPLIFIED_SHAPE
  |
  |  Source: RAW_OUTLIER_CLEANED LineString.
  |  Output precisions: 1, 5, 10, 50, 100, 500, 1000 m.
  |
  |  Algorithm:
  |    - transform to EPSG:3857
  |    - ST_Simplify with the requested tolerance
  |    - transform back to EPSG:4326
  |    - ST_SnapToGrid
  |    - preserve synthetic stop anchor pairs so timing survives simplification
  |
  |  Each SIMPLIFIED_SHAPE variant gets its own point rows. Their metrics are
  |  computed on that simplified geometry and should not be treated as the
  |  authoritative full-density metric stream.
  |
  v
[PASS 6] calculateFixedPoints() for SIMPLIFIED_FIXED_POINTS
  |
  |  Source: RAW_OUTLIER_CLEANED point rows.
  |  Output budgets: 750 and 1500 points.
  |
  |  If source point count <= budget, every point is kept.
  |  Otherwise, the time range is divided into uniform buckets and the point
  |  closest to each bucket center is selected.
  |
  |  Stop anchor pairs are protected even if that slightly exceeds the budget.
  |
  |  Copied from RAW_OUTLIER_CLEANED:
  |    - position, altitude, timestamp
  |    - cumulative distance/ascent/descent/duration
  |    - moving-window speed, slope, elevation gain/loss
  |    - cumulative energy and instantaneous power fields
  |
  |  Recomputed between retained points:
  |    - distanceInMeterBetweenPoints
  |    - durationBetweenPointsInSec
  |    - ascentInMeterBetweenPoints
  |    - energyTotalWh
  |
  v
Post-ingest jobs
  |
  |  ActivityTypeClassifierJob assigns activity type.
  |  EnergyService.recalculateEnergyForTrack() then recalculates energy across
  |  all variants and writes track-level totals from RAW_OUTLIER_CLEANED.
  |
  |  ExplorationScoreJob uses SIMPLIFIED_SHAPE at configured precision
  |  mtl.exploration.use-track-precision, currently 10 m in application.yml.
```

## 3. Filters And Noise-Reduction Steps

| Step | Code | Current values | Purpose |
|---|---|---|---|
| Exact missing fix skip | `GPXReader.processSegment()` | lon = 0.0 and lat = 0.0 | Ignore missing GPS fixes. |
| Elevation carry-forward | `GPXReader.processSegment()` | previous known elevation | Avoid NaN elevation gaps where source lacks elevation. |
| Timestamp plausibility | `GPXReader.isPlausible()` | negative dt rejected; same timestamp allows <= 5 m | Reject impossible time ordering and duplicate-time drift. |
| Speed plausibility | `GPXReader.isPlausible()` | distance / min(dt, 120 s) <= 416 m/s | Reject impossible movement. |
| Long-gap distance guard | `GPXReader.isPlausible()` | if dt > 120 s, distance <= 5000 m | Prevent long pauses from legitimizing huge jumps. |
| Probation promotion | `GPXReader.handleImplausiblePoint()` | >= 15 s or >= 5 points without timestamps; spatial guard 5000 m | Recover from real relocation after initial suspicious points. |
| Startup reset | `GPXReader.handleImplausiblePoint()` | first 10 accepted cleaned points | Allows cold-start fixes to settle. |
| Segment split | `GPXReader` | non-stop temporal gap > 12 h | Split separate activities found in one file while preserving synthetic stop anchor pairs. |
| A-B-C spike removal | `BreakStopTreatment` | jumps >= 90 m, return <= 50 m, each side <= 40 s | Remove short GPS excursions. |
| Stop detection | `TrackStopDetector` | cluster/time profiles below | Detect stationary drift clusters. |
| Stop merge | `TrackStopDetector` | gap <= 120 s and center distance <= 100 m | Merge nearby stop fragments. |
| Stop collapse | `BreakStopTreatment` | replace stop range with two anchors | Remove drift scribble while preserving stop duration. |
| Elevation median denoise | `MedianElevationSmoother` | neighbors within 50 m and 30 s | Smooth altitude jitter without moving X/Y. |
| Optional Kalman smoothing | `KalmanElevationSmoother` | configured via `mtl.denoise.algorithm=kalman` | Optional 3D smoothing. |
| Ascent/descent gate | `GPXStoreService.calculateBetweenPointsMetrics()` | 2 m accumulated altitude delta | Avoid altitude jitter inflating totals. |
| Moving-window metrics | `GPXStoreService.calculateMovingWindowStats()` | 90 s symmetric window | Smooth speed, slope, elevation rates. |
| Moving duration threshold | `GPXStoreService.saveOneLoadResult()` | speed >= 0.5 km/h | Track-level moving time. |
| Shape simplification | `SimplifiedTrackRepository` | 1/5/10/50/100/500/1000 m | Reduce map geometry size. |
| Fixed-point selection | `GPXStoreService.calculateFixedPoints()` | 750 and 1500 points | Stable chart size and temporal coverage. |

Stop detector profiles:

| Category | Min duration | Window | Min points | Max median sample gap | Max sample gap | Cluster radius | R80 threshold | Min inlier ratio | Exit radius |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `MICRO_STOP` | 30 s | 60 s | 6 | 12 s | 15 s | 45 m | 45 m | 0.80 | 70 m |
| `SHORT_STOP` | 60 s | 150 s | 6 | 18 s | 30 s | 65 m | 65 m | 0.75 | 70 m |
| `BREAK` | 180 s | 360 s | 12 | 30 s | 60 s | 75 m | 75 m | 0.70 | 130 m |
| `LONG_BREAK` | 600 s | 7200 s | 8 | 900 s | 1800 s | 90 m | 85 m | 0.70 | 180 m |

The gap columns correspond to `maxMedianGapS` and `maxGapS` in
`TrackStopDetector.StopProfile`, so they are timestamp gap limits in seconds.
The radius columns are distances in meters.

## 4. Which UI Uses Which Data

### Map

The map renders `SIMPLIFIED_SHAPE` geometries.

| Situation | Data |
|---|---|
| Low zoom collection | `SIMPLIFIED_SHAPE`, `precisionInMeter = 1000` |
| Normal/detail collection | `SIMPLIFIED_SHAPE`, `precisionInMeter = 10` |
| High zoom viewport refinement | `SIMPLIFIED_SHAPE`, `precisionInMeter = 1`, fetched per visible track |
| IndexedDB cache | 1000 m and 10 m geometries plus track metadata |
| Track-point layer | Points generated from the currently rendered shape coordinates |
| Track-point popup | `/details?trackType=SIMPLIFIED_SHAPE&precisionInMeter={renderedPrecision}` |
| Nearby-track selection | SQL lookup on `SIMPLIFIED_SHAPE`, `precisionInMeter = 10` |

Main client files:

- `mtl-client/src/components/map/Map.vue`
- `mtl-client/src/utils/tracks/trackApi.ts`
- `mtl-client/src/utils/tracks/trackCollectionLoaderCore.ts`
- `mtl-client/src/utils/tracks/trackCacheDb.ts`

Main server endpoints:

- `POST /api/tracks/get-simplified`
- `GET /api/tracks/get/{gpsTrackId}?precisionInMeter=1`
- `GET /api/tracks/get/{gpsTrackId}/details?trackType=SIMPLIFIED_SHAPE&precisionInMeter=...`
- `POST /api/tracks/get-track-ids-within-distance-of-point`

### Track Details

Track Details intentionally mixes metadata from the track entity with
time-uniform display points.

| Track Details area | Data |
|---|---|
| Metadata, totals, events | `GpsTrack` entity from `GET /api/tracks/get/{id}?precisionInMeter=1`; geometry there is `SIMPLIFIED_SHAPE @ 1 m` |
| Charts and table rows | `SIMPLIFIED_FIXED_POINTS`, `maxPoints = 1500` |
| Track Details minimap | Same `SIMPLIFIED_FIXED_POINTS @ 1500` point list |
| Overview totals such as moving/stopped time | Track-level fields on `GpsTrack`, computed from `RAW_OUTLIER_CLEANED` |
| Per-screen summaries derived from visible rows | The fixed-point detail rows |

The frontend call for chart data is:

```
GET /api/tracks/get/{id}/details?trackType=SIMPLIFIED_FIXED_POINTS&maxPoints=1500
```

Do not use `SIMPLIFIED_SHAPE` as chart data. Douglas-Peucker keeps geometry
shape, not temporal sampling, so it can drop many points on straight climbs or
descents.

### Measure, Race, And Crossing Analysis

Measure/crossing analysis uses the canonical metric variant:

```
RAW_OUTLIER_CLEANED
```

The backend loads it through `GpsTrackVariantSelector.forMetricsId()` so
straight sections still have full GPS sampling. This matters for crossing time,
speed, and segment calculations.

Stop annotations in crossing results come from stop events detected at import
time. They are not re-created from a simple speed threshold inside the crossing
code.

### Filters

The standard filter flow resolves track IDs first, then map/statistics calls use
those IDs.

`SmartBaseFilter` excludes:

- failed loads
- duplicates
- planned tracks
- tracks before `1971-01-01`
- tracks outside optional date filters

Geo filter parameters use `gps_track_data` rows where:

```
track_type = 'SIMPLIFIED_SHAPE'
precision_in_meter = 1000
```

### Statistics And Trends

Statistics and trends do not read per-point rows at request time.

Endpoint:

```
POST /api/tracks/get-track-statistics?groupByDateFormat=...
```

Source:

- `GpsTrackRepository.getTrackStatistics()`
- `gps_track_v`
- track-level fields on `gps_track`

Returned values include distance, moving duration, energy, power, training load,
and exploration score. These are aggregates from track-level summaries, not RAW
or chart point data.

`StatisticsOverview.vue` uses the already loaded map track metadata for its
cards/list rather than fetching a separate per-point dataset.

### Exploration Score

Exploration scoring uses `SIMPLIFIED_SHAPE` at the configured precision:

```
mtl.exploration.use-track-precision: 10
```

The SQL compares each target track against prior successful unique tracks using
PostGIS segment and distance checks.

### Energy

Energy is calculated after activity classification.

`EnergyService.recalculateEnergyForTrack()` iterates all `GpsTrackData`
variants for the track and writes per-point energy fields. Track-level energy
and power totals are taken from the `RAW_OUTLIER_CLEANED` variant.

The ad-hoc energy endpoint defaults to `RAW_OUTLIER_CLEANED` and does not define
normal UI map/chart data flow.

## 5. Role Of RAW Data

`RAW` is stored so imports can be inspected and compared with cleaned results.
It is not used by normal map, Track Details, statistics, trends, exploration, or
crossing flows.

Important caveat: `RAW` is not an exact serialized copy of the source file. The
importer still skips exact `0,0` fixes and may fill missing elevation before
storing coordinates. The original GPX/FIT file is the only true byte-level
source artifact.

Generic API endpoints can technically be called with `trackType=RAW`, but the
frontend does not use RAW for normal features.

## 6. Key Source Files

| File | Role |
|---|---|
| `mtl-server/src/main/java/.../gpx/GPXReader.java` | GPX parsing, RAW/cleaned split, outlier filtering, temporal segment split. |
| `mtl-server/src/main/java/.../gpx/BreakStopTreatment.java` | A-B-C spike removal and stop collapse into synthetic anchors. |
| `mtl-server/src/main/java/.../logic/motion/TrackStopDetector.java` | Cluster-based stop detection and stop-event mapping. |
| `mtl-server/src/main/java/.../gpx/MedianElevationSmoother.java` | Default elevation-only median denoiser. |
| `mtl-server/src/main/java/.../gpx/KalmanElevationSmoother.java` | Optional Kalman/RTS smoother. |
| `mtl-server/src/main/java/.../gpx/GPXStoreService.java` | Stores variants, computes point metrics, creates simplified/fixed-point variants. |
| `mtl-server/src/main/java/.../db/entity/gps/GpsTrackData.java` | Variant enum and discriminators. |
| `mtl-server/src/main/java/.../db/repository/gps/GpsTrackVariantSelector.java` | Central selector for metric vs rendering variants. |
| `mtl-server/src/main/java/.../db/entity/gps/projection/simplified/SimplifiedTrackRepository.java` | PostGIS simplification query. |
| `mtl-server/src/main/java/.../energy/EnergyService.java` | Per-point and track-level energy recalculation. |
| `mtl-server/src/main/java/.../jobs/exploration/ExplorationScoreQueryRepository.java` | Exploration score SQL using `SIMPLIFIED_SHAPE`. |
| `mtl-server/src/main/java/.../web/services/track/TracksController.java` | Track, details, simplified, filter/stat endpoints. |
| `mtl-client/src/components/map/Map.vue` | Zoom-based map precision and on-demand high-detail geometry. |
| `mtl-client/src/components/trackdetails/TrackDetails.vue` | Track Details orchestration. |
| `mtl-client/src/utils/ServiceHelper.ts` | Frontend calls for details, crossing, statistics, rendered-shape point popups. |
| `mtl-client/src/utils/tracks/trackCollectionLoaderCore.ts` | Track collection paging and IndexedDB coordination. |
