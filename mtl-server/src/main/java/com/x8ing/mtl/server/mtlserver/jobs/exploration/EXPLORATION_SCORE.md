# Exploration Score

## What is it?

The **Exploration Score** is a metric per GPS track (0.0 – 1.0) expressing what fraction of the
track covered territory the user had **never visited before**. A score of `0.90` means 90% of the
ride was on new / previously unvisited ground.

It is computed entirely within PostGIS as a background job and stored on `gps_track`. The UI
displays it as a percentage badge (e.g. "90% Explored").

---

## Core Concept

Every track a user has ever recorded represents "known territory". For a new track, we:

1. Take all **prior tracks** (chronologically by `start_date`)
2. Break the new track into short segments (~10m each)
3. For each segment, ask: *does any prior track pass within `corridor-width-m` meters?*
4. Sum up the length of segments that had no prior track nearby → that is the **novel portion**

```
Prior track (25m corridor shown):

   ════════════════════════════      ← known territory

New track broken into 10m segments:
   ├──┤├──┤├──┤├──┤├──┤├──┤├──┤├──┤

Each segment checked individually:
   KNOWN KNOWN KNOWN KNOWN ░░░░ ░░░░  (░ = novel)
```

`exploration_score = novel_length_m / total_length_m`

> **Why segments and not points?** A point-sampling approach misses diagonal crossings that
> fall between sample points. Checking line segments with `ST_DWithin` measures the minimum
> distance between two geometries, catching crossings regardless of angle.

---

## Algorithm Parameters (`application.yml`)

```yaml
mtl:
  exploration:
    corridor-width-m: 25.0    # Buffer radius around each prior path (meters)
    use-track-precision: 10   # Which simplified track variant to use (10m precision)
    max-tracks-per-run: 20    # Safety cap per scheduler invocation (handles backlogs)
    run-schedule: PT120S      # How often the job fires (ISO-8601 duration)
```

### `corridor-width-m` (default: 25.0)

How wide the "known territory" corridor around a prior track is. Chosen to cover a
typical trail or road width plus GPS positional inaccuracy (~5–10m).

- Too small (e.g. 5m): biking the same road from the other side lane = "unexplored"
- Too large (e.g. 200m): parallel trails 100m apart = incorrectly "known"
- 25m ≈ covers road/trail width + GPS error margin

### `use-track-precision` (default: 10)

Which of the pre-computed simplified track variants to use for geometry operations.
Each track is stored at multiple precision levels (1m, 5m, 10m, 50m, 100m, 500m, 1000m).

- 10m gives good shape fidelity (tight switchbacks are preserved)
- Raw (0m) would be more accurate but 10–100× slower per query
- 50m+ is too coarse — loses important route shape

### `max-tracks-per-run` (default: 20)

The job runs repeatedly on a schedule. This cap prevents a single run from holding the
scheduler thread for too long during initial backfill of many tracks.

- 20 tracks × ~1–3s per track ≈ up to 60s per run in the worst case
- A backlog of 500 tracks clears in ~25 runs (≈ 50 minutes at 2-minute intervals)

### `run-schedule` (default: PT120S)

ISO-8601 duration between job runs. Used as `fixedDelayString` in Spring Scheduler.
Adjust to `PT5M` for lighter systems.

When a run processes a full batch (i.e. `tracks.size() >= max-tracks-per-run`), the job
immediately re-runs in a while loop without waiting for the next schedule interval. This
drains backlogs (e.g. after initial import of 500+ tracks) as fast as possible.

---

## Status Lifecycle (`EXPLORATION_STATUS` enum)

Each `gps_track` record has an `exploration_status` column:

```
NOT_SCHEDULED          Track has no start_date; cannot determine "prior" tracks
SCHEDULED              Waiting to be processed by the job
IN_PROGRESS            Currently being calculated (prevents double-processing)
CALCULATED             Score successfully computed; result in exploration_score
NEEDS_RECALCULATION    A historical track was imported that invalidates this result
```

### State transitions

```
Import new track
      │
      ▼
  SCHEDULED ──────────────────────────────────────────────────┐
      │                                                        │
      ▼ (job picks it up)                                      │
  IN_PROGRESS                                                  │
      │                                                        │
      ├─ success ──► CALCULATED                                │
      └─ error   ──► NEEDS_RECALCULATION                       │
                                                               │
  Historical track imported with earlier start_date            │
      │                                                        │
      └─ later overlapping tracks reset ──► NEEDS_RECALCULATION┘
```

---

## Historical Import Invalidation

When a track is imported with a `start_date` **earlier** than some already-calculated tracks,
those later tracks may now have incorrect scores (they didn't know about this newly added
prior track). The system handles this automatically:

On import of track T with `start_date = D`:

- All tracks where `start_date > D` AND spatial bbox overlaps T → reset to `NEEDS_RECALCULATION`
- The bbox overlap check is done in Java using the `bbox_min_lat / bbox_max_lat / bbox_min_lng /
  bbox_max_lng` columns on `gps_track` (fast, no geometry involved)
- The job then reprocesses them in chronological order

---

## Config-Change Rerun

If `corridor-width-m` or `use-track-precision` are changed in `application.yml`, the
previously computed scores are no longer valid (different algorithm = different results).

On startup, the job compares the current config values against the last-used values stored in
the `config` table (`domain1=exploration, domain2=algo`). If any differ:

1. Bulk UPDATE: `SET exploration_status = 'NEEDS_RECALCULATION' WHERE exploration_status = 'CALCULATED'`
2. Stored config snapshot is updated
3. Job processes the backlog on its normal schedule

`max-tracks-per-run` and `run-schedule` changes do **not** invalidate scores.

---

## PostGIS Query

```sql
WITH
  target_track AS (
    -- 1. Get the target track and transform to EPSG:3857 for metric distances
    SELECT
      gt.id        AS track_id,
      gt.start_date,
      ST_Transform(gtd.track, 3857)            AS geom_3857,
      ST_Length(ST_Transform(gtd.track, 3857)) AS total_m
    FROM gps_track gt
    JOIN gps_track_data gtd ON gt.id = gtd.gps_track_id
    WHERE gt.id                    = :trackId
      AND gtd.track_type           = :trackType
      AND gtd.precision_in_meter   = :precisionInMeter
  ),
  target_segments AS (
    -- 2. Break the target track into segments of max :corridorWidthM meters
    --    ST_Segmentize splits any segment longer than the threshold.
    --    ST_DumpSegments extracts each individual segment as a separate row.
    SELECT
      track_id,
      start_date,
      total_m,
      (ST_DumpSegments(ST_Segmentize(geom_3857, :corridorWidthM))).geom AS segment_3857
    FROM target_track
  ),
  evaluated_segments AS (
    -- 3. For each segment, check whether any prior track passes within :corridorWidthM meters.
    --    Two-step filter:
    --      A) Fast GIST index bbox check in EPSG:4326 (eliminates distant tracks cheaply)
    --      B) Exact metric distance check in EPSG:3857 (only runs on bbox candidates)
    --    EXISTS returns on the first match — no full scan of prior tracks.
    SELECT
      ts.track_id,
      ts.total_m,
      ST_Length(ts.segment_3857) AS segment_m,
      NOT EXISTS (
        SELECT 1
        FROM gps_track_data gtd
        JOIN gps_track gt ON gt.id = gtd.gps_track_id
        WHERE gt.start_date          < ts.start_date
          AND gt.duplicate_status    = 'UNIQUE'
          AND gt.load_status         = 'SUCCESS'
          AND gtd.track_type         = :trackType
          AND gtd.precision_in_meter = :precisionInMeter
          AND gtd.track && ST_Transform(ST_Expand(ts.segment_3857, :corridorWidthM), 4326)
          AND ST_DWithin(ST_Transform(gtd.track, 3857), ts.segment_3857, :corridorWidthM)
      ) AS is_novel
    FROM target_segments ts
  )
-- 4. Aggregate: sum lengths of novel vs. known segments
--    Score computation (novel_m / total_m) is done in Java, not SQL.
SELECT
  MAX(total_m)                                              AS total_m,
  SUM(CASE WHEN is_novel THEN segment_m ELSE 0 END)         AS novel_m
FROM evaluated_segments
GROUP BY track_id;
```

### Query Parameters (all come from Java)

| Parameter           | Type    | Source                                                        |
|---------------------|---------|---------------------------------------------------------------|
| `:trackId`          | Long    | method argument                                               |
| `:trackType`        | String  | hardcoded `'SIMPLIFIED'` in `ExplorationScoreQueryRepository` |
| `:precisionInMeter` | Integer | `application.yml` → `use-track-precision`                     |
| `:corridorWidthM`   | Double  | `application.yml` → `corridor-width-m`                        |

`:corridorWidthM` is used for both `ST_Segmentize` (max segment length) and `ST_DWithin`
(match distance). Using the same value ensures no segment is longer than the corridor width,
which prevents a short diagonal crossing from being missed between endpoints.

The score (`novel_m / total_m`) is computed in Java, clamped to `[0.0, 1.0]`, and rounded
to 3 decimal places before storage.

### Query design notes

- **Segment-based, not point-based** — `ST_DWithin` on two line geometries measures the exact
  minimum distance between them, catching diagonal crossings at any angle. A point-sampling
  approach misses crossings that fall between sample points.
- **Length-weighted score** — each segment contributes its actual length (`ST_Length`) to the
  total, so a 2m novel section counts proportionally less than a 50m one.
- **`ST_Segmentize` in 3857** — operates in meters, so `:segmentMaxM` is an exact threshold.
- **Two-step filter per segment** — `&&` probes the GIST index (O(log N)), then `ST_DWithin`
  runs the exact check only on bbox candidates (typically 0–5 tracks). `EXISTS` short-circuits
  on the first hit.
- **No `ST_Union` / `ST_Buffer`** — the original approach of unioning all prior track buffers
  into one polygon OOMs at scale. This approach never builds large intermediate geometries.
- **First track ever** — no prior tracks exist; all segments have `is_novel = true`;
  `exploration_score = 1.0` (100%). Correct by definition.
- **Score computed in Java** — the query returns raw `total_m` and `novel_m`. The score
  (`novel_m / total_m`) is computed, clamped to `[0.0, 1.0]`, and rounded to 3 decimal
  places in `ExplorationScoreAtomicWorker`. This avoids PostgreSQL's
  `ROUND(double precision, int)` limitation.

---

## Database Columns Added to `gps_track`

| Column                  | Type               | Notes                            |
|-------------------------|--------------------|----------------------------------|
| `exploration_status`    | `VARCHAR(255)`     | Enum string, see lifecycle above |
| `exploration_score`     | `DOUBLE PRECISION` | 0.0–1.0, NULL until calculated   |
| `exploration_calc_date` | `TIMESTAMP`        | When the score was last computed |

`novel_length_m` and `known_length_m` are **not stored** — derivable at display time:

- `novel_length_m = exploration_score × track_length_in_meter`
- `known_length_m = (1 − exploration_score) × track_length_in_meter`

---

## Relevant Source Files

| File                                                    | Role                                                  |
|---------------------------------------------------------|-------------------------------------------------------|
| `jobs/exploration/ExplorationScoreJob.java`             | Scheduler — finds pending tracks, delegates to worker |
| `jobs/exploration/ExplorationScoreAtomicWorker.java`    | Per-track calculation in own transaction              |
| `jobs/exploration/ExplorationScoreQueryRepository.java` | Native PostGIS query                                  |
| `db/entity/gps/GpsTrack.java`                           | `EXPLORATION_STATUS` enum + 3 new fields              |
| `db/repository/gps/GpsTrackRepository.java`             | `findByExplorationStatusIn`, invalidation UPDATE      |
| `gpx/GPXStoreService.java`                              | Sets SCHEDULED on import, triggers invalidation       |
| `db/changelog/changes/019.xml`                          | Liquibase migration: columns + index + backfill       |
