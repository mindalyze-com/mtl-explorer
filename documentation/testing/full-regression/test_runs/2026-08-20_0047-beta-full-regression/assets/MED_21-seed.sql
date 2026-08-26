BEGIN;
SET LOCAL session_replication_role = replica;

INSERT INTO indexed_file
  (id, "index", name, path, base_path, full_path, size, create_date,
   index_added_date, index_update_date, version, indexer_status, indexer_id)
SELECT 10000000 + g, 'MEDIA', 'med21-' || g || '.jpg', 'med21',
       '/synthetic/med21', '/synthetic/med21/med21-' || g || '.jpg', 1024,
       clock_timestamp(), clock_timestamp(), clock_timestamp(), 1, 'SUCCESS', 'MED_21'
FROM generate_series(0, 99999) AS g;

INSERT INTO media_file
  (id, file_id, cre_date, exif_date_image_taken, width_pixels, height_pixels)
SELECT 10000000 + g, 10000000 + g, clock_timestamp(),
       timestamp '2030-01-01 00:00:00' + g * interval '1 second', 100, 100
FROM generate_series(0, 99999) AS g;

INSERT INTO indexed_file
  (id, "index", name, path, base_path, full_path, size, create_date,
   index_added_date, index_update_date, version, indexer_status, indexer_id)
SELECT 20000000 + g, 'GPS', 'med21-track-' || g || '.gpx', 'med21',
       '/synthetic/med21', '/synthetic/med21/med21-track-' || g || '.gpx', 1024,
       clock_timestamp(), clock_timestamp(), clock_timestamp(), 1, 'SUCCESS', 'MED_21'
FROM generate_series(0, 299) AS g;

INSERT INTO gps_track
  (id, file_id, track_name, start_date, end_date, number_of_track_points,
   track_length_in_meter, create_date, update_date, load_status, duplicate_status,
   activity_type, activity_type_source, version, track_source)
SELECT 2000000 + g, 20000000 + g, 'MED_21 Track ' || g,
       timestamp '2030-01-01 00:00:00', timestamp '2030-12-31 23:59:59',
       2, 1000, clock_timestamp(), clock_timestamp(), 'SUCCESS', 'UNIQUE',
       'WALKING', 'FILE', 1, 'IMPORTED'
FROM generate_series(0, 299) AS g;

INSERT INTO media_track_correlation
  (id, media_id, track_id, track_version, algorithm_version, captured_at,
   adjusted_capture_time, applied_camera_offset_seconds, time_source,
   route_location, distance_in_meter_since_start, duration_since_start_seconds,
   track_point_index, track_point_time_delta_seconds, selected,
   alternative_count, ambiguous, calculated_at)
SELECT 30000000 + g, 10000000 + g, 2000000 + (g % 300), 1, 1,
       timestamp '2030-01-01 00:00:00' + g * interval '1 second',
       timestamp '2030-01-01 00:00:00' + g * interval '1 second',
       0, 'EXIF_DATE_TAKEN',
       ST_SetSRID(ST_MakePoint(20 + (g % 1000) * 0.001,
                              10 + (g / 1000) * 0.001), 4326),
       g, g, g % 1000, 0, true, 1, false, clock_timestamp()
FROM generate_series(0, 99999) AS g;

INSERT INTO media_resolved_location
  (media_id, location, position_origin, correlation_id, resolved_at)
SELECT 10000000 + g,
       ST_SetSRID(ST_MakePoint(20 + (g % 1000) * 0.001,
                              10 + (g / 1000) * 0.001), 4326),
       'EXIF_EMBEDDED', NULL, clock_timestamp()
FROM generate_series(0, 99999) AS g;

SET LOCAL session_replication_role = origin;
COMMIT;

ANALYZE indexed_file;
ANALYZE media_file;
ANALYZE gps_track;
ANALYZE media_track_correlation;
ANALYZE media_resolved_location;
