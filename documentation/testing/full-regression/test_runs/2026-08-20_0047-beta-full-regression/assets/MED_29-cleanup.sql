BEGIN;
SET LOCAL session_replication_role = replica;

INSERT INTO media_resolved_location
  (media_id, location, position_origin, correlation_id, resolved_at)
SELECT c.media_id, c.route_location, 'TRACK_INTERPOLATED', c.id, clock_timestamp()
FROM media_track_correlation c
WHERE c.media_id = 400002
  AND c.selected;

SET LOCAL session_replication_role = origin;
COMMIT;
