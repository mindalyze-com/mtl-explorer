WITH
    linestring AS (
        select track from gps_track gt where id=1
    ),
    num_points AS (
        SELECT
            ST_NPoints(track) AS count
FROM
    linestring
    ),
    points AS (
SELECT
    i,
    ST_PointN(track, i) AS point
FROM
    linestring,
    generate_series(1, (SELECT count FROM num_points)) AS i
    )
SELECT
    i,
    ST_X(point) AS longitude,
    ST_Y(point) AS latitude,
    ST_Z(point) AS elevation,
    ST_M(point) AS time,
  to_timestamp(ST_M(point)) timestamp
FROM
    points;