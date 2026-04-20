# GPS Parsing Throughput

This document captures the processing throughput for parsing and importing GPS tracks, based on the `create_date` in the `gps_track` table.

## Results

| total_tracks | total_seconds | avg_tracks_per_second |
|--------------|---------------|-----------------------|
| 4,586        | 12,773.485    | 0.3590249646          |

Hence ony my NAS main processing in average about 3 seconds to ingest a track.  Some 
other time consuming jobs (e.g. ExplorerScorer) may follow.

## SQL Queries Used

**Overall Throughput / Average Tracks per Second:**
```sql
SELECT
  count(*) AS total_tracks,
  EXTRACT(EPOCH FROM (max(create_date) - min(create_date))) AS total_seconds,
  count(*) / NULLIF(EXTRACT(EPOCH FROM (max(create_date) - min(create_date))), 0) AS avg_tracks_per_second
FROM gps_track;
```

**Total Duration and Range:**
```sql
SELECT
  min(create_date) AS first_processed,
  max(create_date) AS last_processed,
  max(create_date) - min(create_date) AS duration,
  count(*) AS total_tracks
FROM gps_track;
```

```sql
SELECT
  date_trunc('hour', create_date) AS processing_hour,
  count(*) AS total_tracks,
  EXTRACT(EPOCH FROM (max(create_date) - min(create_date))) AS duration_seconds,
  ROUND(
    (count(*) / NULLIF(EXTRACT(EPOCH FROM (max(create_date) - min(create_date))), 0))::numeric, 
    1
  ) AS tracks_per_second
FROM gps_track
GROUP BY date_trunc('hour', create_date)
ORDER BY processing_hour;
```sql