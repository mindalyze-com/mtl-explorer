BEGIN;
SET LOCAL session_replication_role = replica;

DELETE FROM media_resolved_location
WHERE media_id BETWEEN 10000000 AND 10099999;

DELETE FROM media_track_correlation
WHERE media_id BETWEEN 10000000 AND 10099999;

DELETE FROM media_file
WHERE id BETWEEN 10000000 AND 10099999;

DELETE FROM gps_track
WHERE id BETWEEN 2000000 AND 2000299;

DELETE FROM indexed_file
WHERE id BETWEEN 10000000 AND 10099999
   OR id BETWEEN 20000000 AND 20000299;

SET LOCAL session_replication_role = origin;
COMMIT;

ANALYZE indexed_file;
ANALYZE media_file;
ANALYZE gps_track;
ANALYZE media_track_correlation;
ANALYZE media_resolved_location;
