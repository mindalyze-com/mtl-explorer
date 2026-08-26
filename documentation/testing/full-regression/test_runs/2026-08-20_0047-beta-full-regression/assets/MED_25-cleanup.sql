DROP TRIGGER IF EXISTS med25_after_track_delete_audit ON gps_track;
DROP FUNCTION IF EXISTS med25_audit_after_track_delete();
DROP TABLE IF EXISTS med25_delete_audit;
DROP TABLE IF EXISTS med25_delete_target;

BEGIN;
SET LOCAL session_replication_role = replica;

DELETE FROM media_resolved_location WHERE media_id = 2500000;
DELETE FROM media_track_correlation WHERE media_id = 2500000;
DELETE FROM media_correlation_state WHERE media_id = 2500000;
DELETE FROM media_correlation_media_work WHERE media_id = 2500000;
DELETE FROM media_file WHERE id = 2500000;
DELETE FROM indexed_file WHERE id = 25000000;
DELETE FROM indexed_file WHERE name IN ('MED_25-primary.gpx', 'MED_25-fallback.gpx');

SET LOCAL session_replication_role = origin;
COMMIT;
