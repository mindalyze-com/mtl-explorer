
---

# Problem Statement and Implementation Plan: Track Data Variant Architecture

## 1. The Problem

### What the user sees
In the Track Details panel, the **Elevation Gain Rate** chart shows long stretches of zero even though the Elevation chart directly above shows a continuous, unbroken climb. Speed and slope charts have the same issue — long gaps of zero/null interleaved with sporadic spikes.

### Root cause investigation
The charts are rendered from per-point data fetched via:
```
GET /mtl/api/tracks/get/{id}/details?precisionInMeter=10&trackType=SIMPLIFIED
```

This returns `GpsTrackDataPoint` rows from the `SIMPLIFIED` track variant at 10-meter geometric tolerance.

The `SIMPLIFIED` variant is produced by PostGIS `ST_Simplify` (Douglas-Peucker algorithm) in EPSG:3857, which keeps points that define **visual corners** in the (x, y) plane and drops points on straight sections. The problem: **a straight uphill section has no geometric curvature**, so Douglas-Peucker drops almost every point — even though those dropped points carry distinct timestamps, altitudes, and velocities.

The surviving points on the SIMPLIFIED track are then spaced 60–300 seconds apart. The moving-window calculation (`calculateMovingWindowStats` in GPXStoreService.java) uses a 90-second window (half-window = 45s). When both neighbors sit outside the 45s half-window, the algorithm skips the point entirely, leaving `elevationGainPerHourMovingWindow`, `speedInKmhMovingWindow`, and `slopePercentageInMovingWindow` as `null`. The client config maps `null` → `0` via `(p) => p.elevationGainPerHourMovingWindow ?? 0`, producing the flat-zero bands on the chart.

We verified this by querying track #100226's SIMPLIFIED@10m points: out of 60 points, ~46 had `elevationGainPerHourMovingWindow = null`, including the entire steady climb from 414m → 497m.

### The deeper architectural issue
The fundamental mistake is that **time-series metrics (elevation gain rate, speed, slope) are being recomputed on a geometry-simplified point stream**. Douglas-Peucker simplification optimizes for visual shape, not for temporal resolution. These are incompatible sampling axes:

- **Map polyline rendering** needs geometry-optimized points (Douglas-Peucker is correct)
- **Chart/graph display** needs time-optimized points (uniform temporal spacing)

Currently both consumers read from the same `SIMPLIFIED` variant, which serves neither purpose well for charts.

Additionally, the system recomputes all per-point window metrics (`calculateBetweenPointsMetrics` + `calculateMovingWindowStats`) from scratch on every simplified variant. This is wasteful and — as demonstrated — produces incorrect values when the simplified point density is too low for the window size.

### What is already correct
The `RAW_OUTLIER_CLEANED` variant has **all metrics computed correctly** at full GPS density (~1 Hz). All track-level totals (length, ascent/descent, motion duration, stop detection, energy) are already derived from `RAW_OUTLIER_CLEANED` at ingest time. The per-point window metrics on `RAW_OUTLIER_CLEANED` are the authoritative values. The problem is only in how the data is downsampled for client consumption.

---

## 2. The Solution

### Design principle
> Chart-relevant per-point metrics are computed **once**, on `RAW_OUTLIER_CLEANED`, and **never recomputed** on any downsampled variant. Downsampled variants **select** points from the authoritative set and **copy** their metrics, with only between-point deltas recalculated for the surviving point pairs.

### New track variant taxonomy

Rename existing `SIMPLIFIED` → `SIMPLIFIED_SHAPE` and introduce `SIMPLIFIED_FIXED_POINTS`:

| track_type | precision_in_meter | max_points (new col) | Purpose |
|---|---|---|---|
| `RAW` | 0 | null | Original GPS data as-is |
| `RAW_OUTLIER_CLEANED` | 0 | null | Kalman-smoothed + outlier-removed; authoritative source for all metrics |
| `SIMPLIFIED_SHAPE` | 1, 5, 10, 50, 100, 500, 1000 | null | Geometry-optimized (Douglas-Peucker) for map polyline rendering, geo filters, exploration score |
| `SIMPLIFIED_FIXED_POINTS` | null | 750 or 1500 | Time-uniform downsampling for charts, graphs, map hover tooltip, overview stats |

Two editions of `SIMPLIFIED_FIXED_POINTS` are pre-computed and stored: one with `max_points=750` (for lightweight use) and one with `max_points=1500` (for detailed chart display).

### How SIMPLIFIED_FIXED_POINTS is built

1. Load all `GpsTrackDataPoint` rows of the `RAW_OUTLIER_CLEANED` variant (already fully computed with all window metrics, energy, etc.)
2. If the RAW point count ≤ `maxPoints`: select all points (short tracks get full fidelity)
3. If count > `maxPoints`: divide the track's time range into `maxPoints` equal buckets and pick the RAW point whose timestamp is closest to each bucket center
4. For each selected point, create a new `GpsTrackDataPoint` row:

**Copied verbatim** (absolute fields — describe this point in time, independent of neighbors):
- `pointAltitude`, `pointTimestamp`, `pointLongLat`, `pointXY`
- `distanceInMeterSinceStart`, `ascentInMeterSinceStart`, `descentInMeterSinceStart`, `durationSinceStart`
- `elevationGainPerHourMovingWindow`, `elevationLossPerHourMovingWindow`, `speedInKmhMovingWindow`, `slopePercentageInMovingWindow` — these were computed from a 90s window over the full-density RAW data; the value is a property of that moment in time
- `energyCumulativeWh`, `powerWatts`, `energyGravitationalWh`, `energyAeroDragWh`, `energyRollingResistanceWh`, `energyKineticWh`

**Recalculated** between surviving points (relative fields — would be wrong if blindly copied since neighboring points changed):
- `distanceInMeterBetweenPoints` = `point[i].distanceInMeterSinceStart - point[i-1].distanceInMeterSinceStart`
- `durationBetweenPointsInSec` = `point[i].durationSinceStart - point[i-1].durationSinceStart`
- `ascentInMeterBetweenPoints` = `point[i].pointAltitude - point[i-1].pointAltitude`
- `energyTotalWh` = `point[i].energyCumulativeWh - point[i-1].energyCumulativeWh` (null if either is null)

**Reassigned**:
- `pointIndex` = sequential 0..N-1
- `pointIndexMax` = N-1
- `gpsTrackDataId` = the new SIMPLIFIED_FIXED_POINTS variant's GpsTrackData id
- `movingWindowInSec` = same as RAW (90)

### What changes for SIMPLIFIED_SHAPE
No per-point window metrics need to be computed on SIMPLIFIED_SHAPE variants. The only consumer of SIMPLIFIED_SHAPE is:
- Map polyline rendering (reads the LineString geometry from `gps_track_data.track`)
- Geo SQL filters (`ST_DWithin`, `ST_Intersects` on the LineString)
- Exploration score (PostGIS spatial queries on the LineString)

None of these read `elevationGainPerHourMovingWindow` or any other windowed metric from `gps_track_data_points`. The map hover tooltip calls `fetchTrackDetails()` which will now serve `SIMPLIFIED_FIXED_POINTS`.

Decision for now: still call `populatePointData()` on SIMPLIFIED_SHAPE (we need `pointLongLat`/`pointXY` populated for spatial queries on the points table), but skip `calculateMovingWindowStats()`. Alternatively, keep as-is for simplicity and clean up later — it's wasteful CPU on ingest but not a correctness issue.

---

## 3. Implementation Steps

### Phase A: DB Schema + Rename (new Liquibase changelog `030.xml`)

1. Add nullable `max_points` INTEGER column to `gps_track_data`
2. `UPDATE gps_track_data SET track_type = 'SIMPLIFIED_SHAPE' WHERE track_type = 'SIMPLIFIED'`
3. Drop and recreate `gps_track_v` view — change all `track_type = 'SIMPLIFIED'` references to `'SIMPLIFIED_SHAPE'` (the latest view definition is in 029.xml, which joins on `gd.track_type = 'SIMPLIFIED'` and `gd.precision_in_meter = 1`)
4. Update `filter_config` SmartBaseFilter expression: replace all `gtd.track_type = 'SIMPLIFIED'` with `gtd.track_type = 'SIMPLIFIED_SHAPE'` (the latest expression is in changeset `20260418_3_smart_base_filter_exclude_planned` in 024.xml)

### Phase B: Java Enum + Code Rename

5. In GpsTrackData.java:
    - Rename enum value `SIMPLIFIED` → `SIMPLIFIED_SHAPE`
    - Add `SIMPLIFIED_FIXED_POINTS`
    - Add field: `@Column(name = "max_points") private Integer maxPoints;`

6. Update all Java references to the old enum/string:

| File | What to change |
|---|---|
| GPXStoreService.java line ~478 | `TRACK_TYPE.SIMPLIFIED` → `TRACK_TYPE.SIMPLIFIED_SHAPE` |
| PlannedTrackService.java line ~129 | `TRACK_TYPE.SIMPLIFIED` → `TRACK_TYPE.SIMPLIFIED_SHAPE` |
| ExplorationScoreQueryRepository.java line ~122 | `"SIMPLIFIED"` → `"SIMPLIFIED_SHAPE"` |
| TracksController.java line ~143 | `defaultValue = "SIMPLIFIED"` → `defaultValue = "SIMPLIFIED_SHAPE"` |
| GpsTrackRepository.java line ~30 | `track_type = 'SIMPLIFIED'` → `track_type = 'SIMPLIFIED_SHAPE'` in `@Query` |

7. Update comments/Javadoc in GpsTrackVariantSelector.java, EnergyService.java, `GpsTrackDataRepository.java`, `ExplorationScoreAtomicWorker.java`, `TrackMotionAnalyzer.java`, PlannedTrackService.java

### Phase C: Build the SIMPLIFIED_FIXED_POINTS Generator

8. New method in GPXStoreService.java:
```java
private void calculateFixedPoints(Long trackId, int maxPoints)
```
- Load RAW_OUTLIER_CLEANED points via `gpsTrackDataPointRepository.getTrackDetailsByGpsTrackIdAndType(trackId, "RAW_OUTLIER_CLEANED")`
- If empty, log and return
- If count ≤ maxPoints, select all; otherwise select uniformly by time
- Create `GpsTrackData` with `trackType = SIMPLIFIED_FIXED_POINTS`, `maxPoints = maxPoints`, `precisionInMeter = null`
- The `track` (LineString) field: build from the selected points' lat/lng/alt/timestamp coordinates
- Create `GpsTrackDataPoint` rows with field handling as specified above
- Save in batches (reuse existing `SAVE_CHUNK_SIZE` pattern)

9. In the ingest pipeline (`readAndSave()`), after the `calculateSimplified()` calls, add:
```java
calculateFixedPoints(savedTrack.getId(), 750);
calculateFixedPoints(savedTrack.getId(), 1500);
```

### Phase D: Stop Computing Window Metrics on SIMPLIFIED_SHAPE (optional cleanup)

10. In `calculateSimplified()`: change `populatePointData(simplified, movingWindowInSecs)` to a lighter version that skips `calculateMovingWindowStats()`. Or defer this — it's a CPU optimization, not a correctness fix.

### Phase E: Client Switchover

11. In ServiceHelper.ts, function `fetchTrackDetails()`:
    - Change from: `` `api/tracks/get/${gpsTrackId}/details?precisionInMeter=10&trackType=SIMPLIFIED` ``
    - Change to: `` `api/tracks/get/${gpsTrackId}/details?trackType=SIMPLIFIED_FIXED_POINTS&maxPoints=1500` ``
    - (Or the endpoint can resolve by `trackType` + `maxPoints` via the existing query, since `getTrackDetailsByGpsTrackIdAndPrecisionAndType` filters on type and precision — we may need a new repository query that filters on `track_type` + `max_points` instead of `precision_in_meter`)

12. The `TracksController.getTrackDetails()` endpoint currently takes `precisionInMeter` and `trackType`. For `SIMPLIFIED_FIXED_POINTS`, `precisionInMeter` is irrelevant — we need to either:
    - Add a `maxPoints` request param and a new repository query, or
    - Use the existing `getTrackDetailsByGpsTrackIdAndType(trackId, trackType)` query (which ignores precision) — this works if there's only one `SIMPLIFIED_FIXED_POINTS` variant per budget size per track, but we have two (750 and 1500). So we need a query that filters on `track_type` and `max_points`.

13. Regenerate OpenAPI schema and TypeScript client.

### Phase F: Energy on New Variant

14. `EnergyService.recalculateEnergyForTrack()` already iterates all `GpsTrackData` variants for a track and processes their points. The `SIMPLIFIED_FIXED_POINTS` variants will be picked up automatically. However, since our points are copies from RAW_OUTLIER_CLEANED, the energy values are already set. The recalculation will overwrite them with the same values (harmless) or with between-point recomputed values. Verify this works correctly — the energy calculation uses between-point deltas, so the recalculated values on SIMPLIFIED_FIXED_POINTS will reflect the gaps between surviving points, which is what you'd want for chart segment-level energy display.

---

## 4. Files to Modify

| File | Changes |
|---|---|
| `mtl-server/src/main/resources/db/changelog/changes/030.xml` | **NEW** — add `max_points` column, UPDATE track_type, recreate view, update filter_config |
| `mtl-server/src/main/java/.../db/entity/gps/GpsTrackData.java` | Rename enum, add `SIMPLIFIED_FIXED_POINTS`, add `maxPoints` field |
| `mtl-server/src/main/java/.../gpx/GPXStoreService.java` | Rename refs, add `calculateFixedPoints()`, call it in ingest pipeline |
| `mtl-server/src/main/java/.../web/services/track/TracksController.java` | Rename default param, potentially add maxPoints param |
| `mtl-server/src/main/java/.../db/repository/gps/GpsTrackRepository.java` | Rename `'SIMPLIFIED'` in `@Query` |
| `mtl-server/src/main/java/.../db/repository/gps/GpsTrackDataPointRepository.java` | Add query for `track_type` + `max_points` lookup |
| `mtl-server/src/main/java/.../db/repository/gps/GpsTrackDataRepository.java` | May need `findByGpsTrackIdAndTrackTypeAndMaxPoints()` |
| `mtl-server/src/main/java/.../jobs/exploration/ExplorationScoreQueryRepository.java` | `"SIMPLIFIED"` → `"SIMPLIFIED_SHAPE"` |
| `mtl-server/src/main/java/.../planner/service/PlannedTrackService.java` | `.SIMPLIFIED` → `.SIMPLIFIED_SHAPE` |
| ServiceHelper.ts | Switch `fetchTrackDetails()` to `SIMPLIFIED_FIXED_POINTS` |
| schema.json | Regenerate |

---

## 5. Verification

1. All existing `GPXStoreServiceMovingWindowTest` tests pass
2. New unit test for `calculateFixedPoints()`: verify point count ≤ budget, verify absolute fields match source RAW points exactly, verify relative fields are correctly recomputed
3. Full re-ingest of all tracks (no data migration needed)
4. `curl .../details?trackType=SIMPLIFIED_FIXED_POINTS&maxPoints=1500` for a climbing track — verify `elevationGainPerHourMovingWindow` is non-null and positive at every interior point
5. UI charts show continuous, smooth elevation gain rate (no zero-band artefacts)
6. Map polyline rendering still works (SIMPLIFIED_SHAPE code path unchanged)
7. Geo filters (circle, rectangle, polygon) still work — they reference `SIMPLIFIED_SHAPE@1000m`
8. Exploration score job still works — references `SIMPLIFIED_SHAPE`

---

## 6. Key Decisions

- **Two editions**: 750 and 1500 points, both pre-computed and stored
- **No data migration** — all tracks will be re-ingested from GPX files
- **Uniform time-bucket selection** (not LTTB or other visual-optimized algorithms) — since we copy the RAW window metrics verbatim, the selection method doesn't affect metric accuracy; uniform spacing gives the most even chart resolution
- **The moving-window fix from the initial investigation stays** — it makes the two-pointer window robust for any sparse-but-legitimate input (GPS signal dropouts in RAW data). It's a defensive guard, independent of this architectural change
- **Energy on SIMPLIFIED_FIXED_POINTS**: let the existing `EnergyService.recalculateEnergyForTrack` handle it automatically — it already iterates all variants