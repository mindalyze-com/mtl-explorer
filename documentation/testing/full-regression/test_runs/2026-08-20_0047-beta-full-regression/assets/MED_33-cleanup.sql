BEGIN;
SET LOCAL session_replication_role = replica;

DELETE FROM media_resolved_location
WHERE media_id BETWEEN 33000000 AND 33099991;

DELETE FROM media_track_correlation
WHERE media_id BETWEEN 33000000 AND 33099991;

DELETE FROM media_file
WHERE id BETWEEN 33000000 AND 33099991;

DELETE FROM indexed_file
WHERE id BETWEEN 33000000 AND 33099991;

SET LOCAL session_replication_role = origin;
COMMIT;

ANALYZE indexed_file;
ANALYZE media_file;
ANALYZE media_track_correlation;
ANALYZE media_resolved_location;
