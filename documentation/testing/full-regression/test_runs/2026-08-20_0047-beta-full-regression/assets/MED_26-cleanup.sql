BEGIN;
SET LOCAL session_replication_role = replica;

DELETE FROM media_resolved_location WHERE media_id IN (2600000, 2600001);
DELETE FROM media_track_correlation WHERE media_id IN (2600000, 2600001);
DELETE FROM media_correlation_state WHERE media_id IN (2600000, 2600001);
DELETE FROM media_correlation_media_work WHERE media_id IN (2600000, 2600001);
DELETE FROM media_file WHERE id IN (2600000, 2600001);
DELETE FROM gps_track_data_points WHERE id IN (2603000, 2603001);
DELETE FROM gps_track_data WHERE id = 2602000;
DELETE FROM gps_track WHERE id = 2601000;
DELETE FROM indexed_file WHERE id IN (26010000, 26000000, 26000001);

SET LOCAL session_replication_role = origin;
COMMIT;
