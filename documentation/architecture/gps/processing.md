# GPS data processing pipeline

MTL Explorer uses Kalman smoothing by default. The canonical stream contains
estimates and explicit support decisions; a smooth path does not prove that the
source positions were correct.

## Import and estimation

Background source assessment and import use the configurable GPSBabel allowance
`mtl.indexer.gps.conversion-timeout` (default `PT10M`). Interactive review/export
use their separate one-minute limit. Both conversion modes produce the same GPX
content and preserve the original source; cancellation still terminates the
converter. Native GPX and Java GeoJSON parsing do not use this subprocess limit.

GPSBabel conversions, including review previews and import fallbacks, run outside
database transactions. A source lock spans preparation and saving; diagnosis
commits separately, then imported tracks and replacement changes commit atomically.
Source revisions are checked before saving, and import failures are recorded after
rollback.

1. FIT and other supported formats are converted upstream; `GPXReader` parses
   GPX observations with fractional timestamps, source section identity, and
   available fix, satellite, HDOP, VDOP, speed and source metadata.
2. `RAW` preserves parsed coordinates, including missing altitude/time and
   rejected `0,0` fixes. It is an observation trace, not a byte-level copy of the
   original file. Missing altitude and time serialize as JSON null.
3. Horizontal plausibility and probation reject short excursions. Probation
   uses the longer of its positive entry interval and the interval since the
   previous probation observation, with the usual 120-second time-credit cap.
   It does not accumulate time across a suspect cluster. This preserves strict
   checks for fast loggers while allowing recovery after near-duplicate fixes
   when normal observation spacing resumes. Equal, backward and missing entry
   timestamps retain their existing plausibility rules.
   A stable relocated cluster starts a disconnected recording span. Recording
   segments, clock resets and gaps over 120 seconds also break continuity.
4. `ElevationObservationFilter` rejects short, bounded vertical excursions
   independently of horizontal movement. Rejected altitude stays unknown.
   Persistent altitude steps remain reviewable rather than being removed on
   an unsupported assumption.
5. `BreakStopTreatment` removes isolated position spikes. It no longer detects
   stops or collapses stationary observations into anchor pairs.
6. Startup altitude stabilization and Kalman/RTS smoothing operate within
   recording spans. The transition and process noise
   use each actual time step; missing measurements skip the update. Source
   quality and large innovations increase measurement uncertainty.
7. `RAW_OUTLIER_CLEANED` stores the full-density canonical result.
   `TrackMotionAnalyzer` then classifies moving, stopped, and unknown intervals
   without changing accepted coordinates or elevation. Point metrics, duration,
   chart windows, peaks, energy, and events consume the stored support and
   motion decisions. See [motion analysis](motion.md).

The Kalman settings are under `mtl.denoise`. Defaults are horizontal/vertical
measurement sigmas 6/10 m and horizontal/vertical acceleration sigmas 2/0.5 m/s².
The constructor fallback is also Kalman. Median remains an explicit option.
Processing evidence records the selected algorithm, pipeline identifier and
Kalman parameters. DOP changes relative uncertainty; it is not an error radius.

## Interval contract

A point's `intervalStatus` describes the interval from the preceding point.

| Status | Meaning | Metric support |
|---|---|---|
| `START` | First point | No preceding interval. |
| `OBSERVED` | Positive, continuous timed observations | Distance/time; altitude-derived metrics require valid altitude. |
| `STOPPED` | Stationary anchor support retained by the interval model | Supported elapsed time. Current imports classify stops separately with `motionState`; they do not create collapsed anchor pairs. |
| `GEOMETRY_ONLY` | Nearby observations without usable elapsed time | Geometry distance; no speed, power or energy estimate. |
| `UNKNOWN` | Recording break or unsupported connection | No connecting distance, ascent, speed or energy estimate. Positive elapsed time contributes to unknown duration. |

Geometry-only support is bounded to 200 m without time, or 5 m for equal
timestamps. Backward time never establishes a connecting edge. Motion inference
uses a separate, stricter 60-second observation-gap limit. Moving, confirmed
stopped and unknown seconds partition a complete increasing timeline.
Missing time and backward clock resets cannot establish an absolute duration;
their unknown length must not be interpreted as measured zero time.

Missing altitude resets the vertical baseline. A shared 2 m ascent/descent gate
feeds cumulative climbing, vertical rates and gravitational energy. Unsupported
rates and peaks are null. Power limits also cap the accumulated energy and
record `energyLimited`, so a plausible chart cannot hide an unbounded total.
Energy remains an activity-dependent mechanical estimate calculated after
classification.

## Background batches

Energy, duplicate detection, activity classification, exploration, photo correlation,
exercise-route association and synthetic demo health process bounded batches. A full
batch with progress schedules its next batch immediately through the existing scheduler.
Each callback yields its scheduler thread before the next batch; one running flag per
job prevents overlap and duplicate continuation chains.

Temporary DB connection acquisition failures pause duplicate/classification
batches before marking a track failed. The current and later tracks remain pending
and retry on the existing periodic schedule. Authentication, configuration and
data errors are distinguished from temporary connection failures.

Idle or failed batches stop their continuation and retry on the existing periodic
schedule. Energy and demo-health idle checks default to five seconds. Energy generations,
delayed energy/media retries, indexing prerequisites and the shared heavy-job guard
remain in their respective workers. Exploration failures wait for its normal retry
rather than immediately recalculating the same failed batch. Exercise associations
commit metadata, activity changes and energy requests together before another batch runs.

Shutdown checks stop new batches and leave pending records for restart. File indexers
already use worker queues; remote sync, downloads and service probes keep their own
schedules and limits.

## Storage and consumers

| Variant | Consumers |
|---|---|
| `RAW` | Quality review and audit. |
| `RAW_OUTLIER_CLEANED` | Authoritative point metrics, server chart buckets, energy and crossing analysis. |
| `SIMPLIFIED_SHAPE` | Map rendering at 1, 10, 100 and 1000 m; points refer back to canonical indices. |

`recordingBreakBeforePointIndexes` preserves both ends of discontinuities
through simplification and map rendering. `observed_path` is a separate XY
MultiLineString used by proximity, photo-route matching and exploration queries;
it omits connecting gap edges. Coordinate arrays retain their stable point
indices. Photo position interpolation requires an observed adjacent interval or
an exact observation. Disconnected tracks are excluded from automatic Frechet
matching, which assumes one continuous curve. Custom SQL written against the
flat `track` column must use `observed_path` when continuity matters.

Track Details charts use server buckets over canonical points, not map
simplification. Main maps, point layers, the Details minimap and the GPS review
map preserve recording breaks. Single-track 3D replay is unavailable across
recording gaps. Crossing analysis also splits recording spans before timing.

## Review and validation

Track Details → Quality → Review GPS observations loads
`GET /api/tracks/{trackId}/quality` on demand. Original and processed paths can
be toggled; selecting a review item shows its location, source evidence and
possible metric impact. Reasons include rejected observations, large
corrections, stationary support, unknown intervals and limited energy. The list shows
up to 250 items, ranked by a heuristic impact score, not calibrated confidence.

See the [pipeline reliability tests](../../../mtl-server/src/test/java/com/x8ing/mtl/server/mtlserver/gpx/GpsPipelineReliabilityTest.java) and [source assessment tests](../../../mtl-server/src/test/java/com/x8ing/mtl/server/mtlserver/gpx/GpsProcessingAssessmentTest.java).
Synthetic cases cover known failures and numerical consistency. Correlated GPS
drift, slow movement and altitude steps can still be ambiguous. Ground-truth
field evaluation is needed before claiming universal accuracy improvements.

New derived fields are populated by new imports. Existing databases must be
recreated/reimported to obtain complete evidence and observed paths; no legacy
backfill is included in those field changes. Changeset 082 separately schedules
one ordinary GPS reimport; see [release upgrade instructions](../../release-notes/v.0.9.0.md).

## Source diagnosis and timing provenance

Source review assesses recorded evidence and processing results independently
of repair. Severe timing findings are contained before temporal splitting;
original timestamps remain in RAW observations. Estimated repairs retain their
provenance on reimport. Untimed GPX routes can supply geometry without measured
duration, speed, energy, or time-based health/photo matching.

Findings mark a source as needing review; they do not automatically exclude
it from the map or statistics. Saved use decisions control those exclusions.
Estimated, conflicting, or otherwise unsupported timing independently restricts
time-based calculations, duplicate preference, and exploration eligibility.
Including a source does not convert estimated timing into measurements. See
[source review](../../user/tracks/source-review.md)
and the [GPS quality module boundary](../../development/gps-quality-modules.md).

## Indexer queue and recovery

New and changed files become pending as soon as discovery is committed, even
while imports are waiting. Processing shows **Discovering** while the file total
is still growing, then reports progress against the committed queue. GPS and
media counts describe source files, not track points or tracks inside a file.

Manual rescans retry failed files even when their size and modification time are
unchanged. Startup and scheduled scans recover pending work and changed files
without repeatedly importing unchanged failed sources. Stable zero-byte GPS and
media files are informational **Skipped** outcomes and are processed when their
contents change. Earlier imported data stays available and is identified as
retained data.

Incomplete storage scans preserve imported database records. Removal stays
pending until database cleanup succeeds; failures retain their operation and
error. The indexer never deletes original GPS or media files. Completion writes
retry temporary database failures, and a rescan can recover pending work whose
worker is no longer active.

When duplicate detection or activity classification cannot temporarily obtain a
database connection, the batch pauses and leaves its tracks pending for the next
scheduled turn. Invalid data and permanent errors keep their failure diagnostics.
This retry does not increase worker threads or the database pool size.

New derived fields are calculated by normal imports; legacy backfill is not the
default policy. Acknowledgement semantics are described in
[source issues](../source-issues.md).
