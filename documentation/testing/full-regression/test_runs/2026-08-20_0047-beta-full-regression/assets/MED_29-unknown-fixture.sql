BEGIN;
SET LOCAL session_replication_role = replica;

-- Retain the selected correlation and its route coordinate, but remove the
-- resolved projection so the live DTO has unknown provenance with a bounded
-- activity-map coordinate.
DELETE FROM media_resolved_location
WHERE media_id = 400002;

SET LOCAL session_replication_role = origin;
COMMIT;
