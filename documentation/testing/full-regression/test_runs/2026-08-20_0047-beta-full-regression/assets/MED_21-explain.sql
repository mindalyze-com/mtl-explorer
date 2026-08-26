\timing on

EXPLAIN (ANALYZE, BUFFERS)
SELECT resolved.media_id,
       ROUND(CAST(ST_Y(resolved.location) AS numeric), 5) AS lat,
       ROUND(CAST(ST_X(resolved.location) AS numeric), 5) AS lng
FROM media_resolved_location resolved
WHERE resolved.location && ST_MakeEnvelope(20.2, 10.0, 20.25, 10.1, 4326)
UNION ALL
SELECT media.id,
       ROUND(CAST(ST_Y(media.exif_gps_location) AS numeric), 5) AS lat,
       ROUND(CAST(ST_X(media.exif_gps_location) AS numeric), 5) AS lng
FROM media_file media
WHERE media.exif_gps_location && ST_MakeEnvelope(20.2, 10.0, 20.25, 10.1, 4326)
  AND NOT EXISTS (
      SELECT 1
      FROM media_resolved_location resolved
      WHERE resolved.media_id = media.id
  );

EXPLAIN (ANALYZE, BUFFERS)
SELECT correlation.media_id AS id,
       indexed.name AS file_name,
       media.camera_make,
       media.camera_model,
       correlation.captured_at,
       correlation.adjusted_capture_time,
       correlation.applied_camera_offset_seconds,
       correlation.time_source,
       ST_Y(media.exif_gps_location) AS exif_lat,
       ST_X(media.exif_gps_location) AS exif_lng,
       ST_Y(correlation.route_location) AS route_lat,
       ST_X(correlation.route_location) AS route_lng,
       ST_Y(resolved.location) AS resolved_lat,
       ST_X(resolved.location) AS resolved_lng,
       ST_Y(manual.location) AS manual_lat,
       ST_X(manual.location) AS manual_lng,
       manual.note AS manual_note,
       resolved.position_origin,
       correlation.distance_in_meter_since_start,
       correlation.duration_since_start_seconds,
       correlation.track_point_index,
       correlation.track_point_time_delta_seconds,
       correlation.ambiguous,
       correlation.alternative_count,
       COUNT(*) OVER () AS total_elements
FROM media_track_correlation correlation
JOIN media_file media ON media.id = correlation.media_id
JOIN indexed_file indexed ON indexed.id = media.file_id
LEFT JOIN media_resolved_location resolved ON resolved.media_id = media.id
LEFT JOIN media_manual_location manual ON manual.media_id = media.id
WHERE correlation.track_id = 2000000
  AND correlation.selected
ORDER BY correlation.adjusted_capture_time, correlation.media_id
LIMIT 100
OFFSET 0;
