BEGIN;
SET LOCAL session_replication_role = replica;

-- 99,992 synthetic photos plus the activity's eight existing items make an
-- exact 100,000-item activity selection. All synthetic points share a tight
-- Bern location so the main map also exposes a 100,000-photo cluster.
INSERT INTO indexed_file
  (id, "index", name, path, base_path, full_path, size, create_date,
   index_added_date, index_update_date, version, indexer_status, indexer_id)
SELECT 33000000 + g, 'MEDIA', 'med33-' || lpad(g::text, 5, '0') || '.jpg',
       '2026-08-20_0047-beta-full-regression',
       '/app/media',
       '/app/media/2026-08-20_0047-beta-full-regression/mtl-regression-photo-a.jpg', 1024,
       clock_timestamp(), clock_timestamp(), clock_timestamp(), 1,
       'COMPLETED_WITH_SUCCESS', 'MED_33'
FROM generate_series(0, 99991) AS g;

INSERT INTO media_file
  (id, file_id, cre_date, exif_date_image_taken, width_pixels, height_pixels)
SELECT 33000000 + g, 33000000 + g, clock_timestamp(),
       timestamp '2031-01-01 00:00:00' + g * interval '1 second', 100, 100
FROM generate_series(0, 99991) AS g;

INSERT INTO media_track_correlation
  (id, media_id, track_id, track_version, algorithm_version, captured_at,
   adjusted_capture_time, applied_camera_offset_seconds, time_source,
   route_location, distance_in_meter_since_start, duration_since_start_seconds,
   track_point_index, track_point_time_delta_seconds, selected,
   alternative_count, ambiguous, calculated_at)
SELECT 34000000 + g, 33000000 + g, 100028, 1, 1,
       timestamp '2031-01-01 00:00:00' + g * interval '1 second',
       timestamp '2031-01-01 00:00:00' + g * interval '1 second',
       0, 'EXIF_DATE_TAKEN',
       ST_SetSRID(ST_MakePoint(7.44740, 46.94800), 4326),
       (g % 190)::double precision, g, g % 16, 0, true, 1, false,
       clock_timestamp()
FROM generate_series(0, 99991) AS g;

INSERT INTO media_resolved_location
  (media_id, location, position_origin, correlation_id, resolved_at)
SELECT 33000000 + g,
       ST_SetSRID(ST_MakePoint(7.44740, 46.94800), 4326),
       'EXIF_EMBEDDED', NULL, clock_timestamp()
FROM generate_series(0, 99991) AS g;

SET LOCAL session_replication_role = origin;
COMMIT;

ANALYZE indexed_file;
ANALYZE media_file;
ANALYZE media_track_correlation;
ANALYZE media_resolved_location;
