BEGIN;
SET LOCAL session_replication_role = replica;

INSERT INTO indexed_file
  (id, "index", name, path, base_path, full_path, size, create_date,
   index_added_date, index_update_date, version, indexer_status, indexer_id)
VALUES
  (26010000, 'GPS', 'med26-bad-srid.gpx', 'med26', '/synthetic/med26',
   '/synthetic/med26/med26-bad-srid.gpx', 1024, clock_timestamp(),
   clock_timestamp(), clock_timestamp(), 1, 'SUCCESS', 'MED_26'),
  (26000000, 'MEDIA', 'med26-failing.jpg', 'med26', '/synthetic/med26',
   '/synthetic/med26/med26-failing.jpg', 1024, clock_timestamp(),
   clock_timestamp(), clock_timestamp(), 1, 'SUCCESS', 'MED_26'),
  (26000001, 'MEDIA', 'med26-healthy.jpg', 'med26', '/synthetic/med26',
   '/synthetic/med26/med26-healthy.jpg', 1024, clock_timestamp(),
   clock_timestamp(), clock_timestamp(), 1, 'SUCCESS', 'MED_26');

INSERT INTO gps_track
  (id, file_id, track_name, start_date, end_date, number_of_track_points,
   track_length_in_meter, create_date, update_date, load_status, duplicate_status,
   activity_type, activity_type_source, version, track_source)
VALUES
  (2601000, 26010000, 'MED_26 bad SRID track', timestamp '2032-01-01 10:00:00',
   timestamp '2032-01-01 10:10:00', 2, 268, clock_timestamp(), clock_timestamp(),
   'SUCCESS', 'UNIQUE', 'WALKING', 'FILE', 1, 'IMPORTED');

INSERT INTO gps_track_data
  (id, gps_track_id, create_date, precision_in_meter, track_type, track,
   number_of_physical_points)
VALUES
  (2602000, 2601000, clock_timestamp(), 0, 'RAW_OUTLIER_CLEANED',
   ST_Transform(
     ST_SetSRID(ST_MakeLine(ARRAY[
       ST_MakePoint(8.0000, 47.0000),
       ST_MakePoint(8.0020, 47.0020)
     ]), 4326),
     3857
   ),
   2);

INSERT INTO gps_track_data_points
  (id, gps_track_data_id, moving_window_in_sec, point_index, point_index_max,
   point_timestamp, point_long_lat, distance_in_meter_since_start,
   duration_since_start)
VALUES
  (2603000, 2602000, 0, 0, 1, timestamp '2032-01-01 10:00:00',
   ST_SetSRID(ST_MakePoint(8.0000, 47.0000), 4326), 0, 0),
  (2603001, 2602000, 0, 1, 1, timestamp '2032-01-01 10:10:00',
   ST_SetSRID(ST_MakePoint(8.0020, 47.0020), 4326), 268, 600);

INSERT INTO media_file
  (id, file_id, cre_date, exif_gps_date, exif_gps_location,
   exif_date_image_taken, width_pixels, height_pixels)
VALUES
  (2600000, 26000000, clock_timestamp(), timestamp '2032-01-01 10:05:00',
   ST_SetSRID(ST_MakePoint(8.0010, 47.0010), 4326), NULL, 100, 100),
  (2600001, 26000001, clock_timestamp(), NULL, NULL,
   timestamp '2032-01-01 10:05:00', 100, 100);

SET LOCAL session_replication_role = origin;
COMMIT;

WITH queued_at AS (SELECT clock_timestamp() - interval '1 second' AS value)
INSERT INTO media_correlation_media_work(
  media_id, reason, requested_at, attempt_count, last_error, retry_after)
SELECT id, 'MED_26_failure_isolation', queued_at.value, 0, NULL, queued_at.value
FROM (VALUES (2600000), (2600001)) AS media(id)
CROSS JOIN queued_at
ORDER BY id;
