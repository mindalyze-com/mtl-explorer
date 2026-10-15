-- MTL Explorer scoring reference before candidate preparation; used for equivalence tests.
WITH target_track AS (
    SELECT
        gt.id AS track_id,
        gt.start_date,
        ST_Transform(gtd.observed_path, 3857) AS geom_3857,
        ST_Length(ST_Transform(gtd.observed_path, 3857)) AS total_m
    FROM gps_track gt
    JOIN gps_track_data gtd ON gt.id = gtd.gps_track_id
    WHERE gt.id = :trackId
      AND NOT gt.review_excluded AND NOT gt.timing_unreliable
      AND gtd.track_type = :trackType
      AND gtd.precision_in_meter = :precisionInMeter
),
target_segments AS (
    SELECT
        track_id,
        start_date,
        total_m,
        (ST_DumpSegments(ST_Segmentize(geom_3857, :corridorWidthM))).geom AS segment_3857
    FROM target_track
),
evaluated_segments AS (
    SELECT
        ts.track_id,
        ts.total_m,
        ST_Length(ts.segment_3857) AS segment_m,
        NOT EXISTS (
            SELECT 1
            FROM gps_track_data gtd
            JOIN gps_track gt ON gt.id = gtd.gps_track_id
            WHERE gt.start_date < ts.start_date
              AND gt.duplicate_status = 'UNIQUE'
              AND gt.load_status = 'SUCCESS'
              AND NOT gt.review_excluded AND NOT gt.timing_unreliable
              AND gtd.track_type = :trackType
              AND gtd.precision_in_meter = :precisionInMeter
              AND gtd.track && ST_Transform(ST_Expand(ts.segment_3857, :corridorWidthM), 4326)
              AND ST_DWithin(ST_Transform(gtd.observed_path, 3857), ts.segment_3857, :corridorWidthM)
        ) AS is_novel
    FROM target_segments ts
)
SELECT
    MAX(total_m) AS total_m,
    SUM(CASE WHEN is_novel THEN segment_m ELSE 0 END) AS novel_m
FROM evaluated_segments
GROUP BY track_id
