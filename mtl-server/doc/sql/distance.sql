-- This is the ultimate trick to get a good CRS
-- _ST_BestSRID !
select gtd.*, ST_Length(st_transform(track, _ST_BestSRID(track)))
from gps_track_data gtd;


-- find tracks which are within a given distance in meters from a given point in long/lat
-- approach is to have long/lat in 4326, then transform to web mercator (3857) which works with meters.
-- example below: within 500 meters
-- >>>> BETTER APPROACH WITH _ST_BestSRID()
SELECT *
FROM gps_track
WHERE ST_DWithin(ST_TRANSFORM(track, 3857),
                 ST_Transform(ST_SetSRID(ST_Point(8.018603324890138, 47.37335461270955), 4326), 3857), 500);

-- find all points within a distance of a given point using the track-point detail table:
-- runs 51 seconds
select
    count(1)
from
    gps_track_data_points gtdp
where
    ST_DWithin(point_xy ,
               ST_Transform(ST_SetSRID(ST_Point(8.507752418518068,	47.55829220039317),	4326),	3857),	30);


-- runs 52 sec for count all
SELECT *
FROM gps_track_data
WHERE ST_DWithin(ST_TRANSFORM(track, 3857),
                 ST_Transform(ST_SetSRID(ST_Point(8.507752418518068,	47.55829220039317), 4326), 3857), 30);

-- points of one particular track which are closest to a given point
WITH
    linestring AS (
        select track from gps_track gt where id=:track_id
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
  to_timestamp(ST_M(point)) timestamp,
    -- >>>> BETTER APPROACH WITH _ST_BestSRID()
  ST_Distance(ST_Transform(point,3857), ST_Transform(ST_SetSRID(ST_Point(:longitude, :latitude), 4326), 3857)) as distance_point_1
FROM
    points
order by distance_point_1 asc
limit 1
;