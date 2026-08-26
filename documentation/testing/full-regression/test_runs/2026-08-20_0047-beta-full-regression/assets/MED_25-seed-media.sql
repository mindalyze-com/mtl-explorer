BEGIN;
SET LOCAL session_replication_role = replica;

INSERT INTO indexed_file
  (id, "index", name, path, base_path, full_path, size, create_date,
   index_added_date, index_update_date, version, indexer_status, indexer_id)
VALUES
  (25000000, 'MEDIA', 'med25-camera-time.jpg', 'med25',
   '/synthetic/med25', '/synthetic/med25/med25-camera-time.jpg', 1024,
   clock_timestamp(), clock_timestamp(), clock_timestamp(), 1, 'SUCCESS', 'MED_25');

INSERT INTO media_file
  (id, file_id, cre_date, exif_date_image_taken, width_pixels, height_pixels)
VALUES
  (2500000, 25000000, clock_timestamp(), timestamp '2031-01-01 10:05:00', 100, 100);

SET LOCAL session_replication_role = origin;
COMMIT;

INSERT INTO media_correlation_media_work(media_id, reason, requested_at)
VALUES (2500000, 'MED_25_seed', clock_timestamp())
ON CONFLICT (media_id) DO UPDATE SET
  reason = EXCLUDED.reason,
  requested_at = EXCLUDED.requested_at,
  attempt_count = 0,
  last_error = NULL,
  retry_after = EXCLUDED.requested_at;
