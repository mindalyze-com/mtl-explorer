WITH
    transformed_tracks AS (
        SELECT
            ST_Transform(ST_Force3DZ(track), 3857) AS geom,
            -- Extract the M values (time) from the 4D LineString as an array of timestamps
            ARRAY(
                SELECT
        to_timestamp(ST_M(ST_PointN(track, i))) AS timestamp
      FROM
        generate_series(1, ST_NPoints(track)) AS i
    ) AS timestamps
        FROM
            gps_track_data
        where
                id=222461 -- auto NZ  10%max 27, med 12
        --  id = 203714-- auto -- 10% max 32, med 10
        --  id = 203840 -- auto 10% max 27 , med 10.5
        --   id = 226331 -- velo  10% max 4, meda 1.3
        -- id = 226367 -- velo  10% max 9 , med 3.3
        --  id = 226376 -- velo  10% max 11, med 2.7
        -- id=208979 -- velo ZHsee  10%max 13, med 3.0
        -- id=201113 -- velo Berg  10max 13, med 3
    ),
    segments AS (
        SELECT
            i,
            ST_Length(ST_LineSubstring(geom, (i-1)::float / (ST_NPoints(geom)-2), i::float / (ST_NPoints(geom)-1))) / (EXTRACT(EPOCH FROM (timestamps[i] - timestamps[i-1]))) AS speed
        FROM (
                 SELECT
                     generate_series(1, ST_NPoints(geom)-1) AS i,
                     geom,
                     timestamps
                 FROM transformed_tracks
             ) AS subquery
    ),
    speed_stats AS (
        SELECT
            MAX(speed) AS max_speed,
            percentile_cont(0.95) WITHIN GROUP (ORDER BY speed) AS five_percent_max_speed,
    percentile_cont(0.9) WITHIN GROUP (ORDER BY speed) AS ten_percent_max_speed,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY speed) AS median_speed,
    percentile_cont(0.1) WITHIN GROUP (ORDER BY speed) AS ten_percent_lowest_speed
FROM segments
    )
SELECT * FROM speed_stats;



