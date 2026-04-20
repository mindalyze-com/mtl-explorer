-- simplification (smart) & compression
with q as (
    select
        id,
        track track_orig,
        ST_NumPoints(track) num_of_points_orig,
        ST_Simplify(st_transform(track, 3857),50) as simplified, -- simplified with meters
        ST_SnapToGrid(st_transform(ST_Simplify(st_transform(track, 3857),50), 4326),0.00001) as simplified_2 -- simplified with meters, yet keep a useful precision for storage
    from gps_track gt
    --where id in (72,198,202)
)
select
    *
from q;
;


-- compression
--     1 meter:   3 x (close to original, can act as 'original')
--     5 meter:   9 x
--    10 meter:  14 x
--    50 meter:  45 x (for overview)
--   100 meter:  75 x
--  1000 meter: 460 x
with q as (
    select
        id,
        track track_orig,
        ST_NumPoints(track) num_of_points_orig,
        ST_Simplify(st_transform(track, 3857),50) as simplified -- simplified with meters
    from gps_track gt
    --where id in (72,198,202)
)
select
    sum(num_of_points_orig), sum(num_of_points_simplified), 1.0 * sum(num_of_points_orig) / sum(num_of_points_simplified)
from (select q.*, ST_NumPoints(simplified) num_of_points_simplified, 1.0 * num_of_points_orig/ST_NumPoints(simplified) as compression_factor from q) t2;
;

