---------------------------------------------------------------------
-- GET SIMILAR TRACKS... THIS REALLY SEEMS WORKING
---------------------------------------------------------------------
SELECT *
FROM (
         SELECT track.*, ST_HausdorffDistance(reference_track.gd_track, track.gd_track) AS distance
         FROM gps_track_v AS track, (SELECT gd_track FROM gps_track_v WHERE id = :reference_id) AS reference_track
     ) AS result
ORDER BY distance
LIMIT 10;





-- find most similar tracks (NOT SURE)  ID: 3033 1861    3871   simplify: 0.0002
WITH orig_track AS (
    SELECT ST_Transform(ST_Simplify(track, :simplify), 3857) as orig_track, id, gps_track_id
    FROM gps_track_data
    WHERE gps_track_id  = :gps_track_id
      and precision_in_meter = 10
)
SELECT
    gtd.id as id_duplicate ,
    gtd.gps_track_id as gps_track_id_simiar,
    orig_track.id as id_orig_track,
    orig_track.gps_track_id as orig_track_gps_track_id,
    ST_FrechetDistance(ST_Transform(ST_Simplify(gtd.track, :simplify), 3857), orig_track.orig_track) as similarity,
    ST_Transform(ST_Simplify(track, :simplify), 3857) as track_duplicate_simplified,
    orig_track.orig_track
FROM gps_track_data gtd
         INNER JOIN orig_track ON 1=1
         INNER JOIN gps_track gt on gt.id = gtd.gps_track_id
where 1=1
  and gtd.precision_in_meter = 10
  AND gt.start_date  > :start_date -- TO_TIMESTAMP( '2017-03-31 9:30:20', 'YYYY-MM-DD HH24:MI:SS')
  and gt.start_date < :end_date
ORDER BY similarity, gps_track_id_simiar ;



-- get a track and it's duplicate
select
    t1.*,   original_trackpoints - duplicate_trackpoins as diff_trackpoins
from (
         select gtv.*,
                gtv.gd_track track_original,
                (select gd2.gd_track from gps_track_v gd2 where gd2.id = gtv.duplicate_of) gd_track_duplicate,
                gtv.file_name original_name,
                (select gd2.file_name  from gps_track_v gd2 where gd2.id = gtv.duplicate_of) duplicate_name,
                gtv.start_date original_start,
                (select gd2.start_date from gps_track_v gd2 where gd2.id = gtv.duplicate_of) duplicate_start,
                gtv.number_of_track_points original_trackpoints,
                (select gd2.number_of_track_points from gps_track_v gd2 where gd2.id = gtv.duplicate_of) duplicate_trackpoins,
                gtv.id,
                gtv.duplicate_of
         from gps_track_v gtv
         where gtv.duplicate_status = 'DUPLICATE'
     ) t1
;
