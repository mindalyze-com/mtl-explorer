WITH
    tracks_with_points AS (
        SELECT
            id as gps_track_data_id,
            gps_track_id,
            track AS geom,
            _ST_BestSRID(track) as srid,
            ST_Transform(ST_Force3DZ(track), _ST_BestSRID(track)) AS geom2,
            generate_series(1, ST_NPoints(ST_Transform(ST_Force3DZ(track), _ST_BestSRID(track)))) AS point_index,
            ST_NPoints(ST_Transform(ST_Force3DZ(track), _ST_BestSRID(track))) as point_index_total
        FROM
            gps_track_data
        where
            -- id=222891 -- rid through hardwald
            --id=224691 -- ride home bülach, division by zero
            id=222862  -- RAW: 222860 (1525), RAW_CLEANDED: 222861 (1525),  1m: 222862 (293), 5m: 222863 (88), 10m: 222864 (59)
        -- id = 226078
        -- id=200299
        --id = 224626 -- 224630	102736	100m   1m=224626,  5m=224627, 10m=224628, 100m=224630, 1000m=224632, RAW=224624,  RAW_CLEANED=224625
    ),
    extracted_points AS (
        SELECT
            gps_track_data_id,
            gps_track_id,
            point_index,
            point_index_total,
            srid,
            st_x(st_pointn(geom, point_index)) as point_long,
            st_y(st_pointn(geom, point_index)) as point_lat,
            ST_X(ST_PointN(geom2, point_index)) as point_x,
            ST_Y(ST_PointN(geom2, point_index)) as point_y,
            ST_Z(ST_PointN(geom2, point_index)) as point_altitude,
            to_timestamp(ST_M(ST_PointN(geom, point_index))) AS point_timestamp
        FROM
            tracks_with_points
    ),
-- here we calculate the difference between points
    points_with_delta AS (
        SELECT
            *,
            extract( epoch from (point_timestamp)) as point_timestamp_epoch,
            ST_SetSRID(ST_Point( point_long, point_lat), 4326) as point_long_lat,
            point_timestamp - LAG(point_timestamp, 1) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) AS duration_between_points,
                extract(epoch from (point_timestamp - LAG(point_timestamp, 1) OVER (PARTITION BY gps_track_data_id ORDER BY point_index))) AS duration_between_points_in_sec,
            point_altitude - LAG(point_altitude, 1) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) AS ascent_in_meter_between_points,
                ST_Distance(ST_Point(point_x, point_y),LAG(ST_Point(point_x, point_y), 1) OVER (PARTITION BY gps_track_data_id ORDER BY point_index)) AS distance_in_meter_between_points
        FROM
            extracted_points
    ),
-- calculation of speed and slope
    q1 AS (
        SELECT
            *,
            SUM(ascent_in_meter_between_points) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window / 2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS elevation_gain_in_moving_window,
                first_value (point_timestamp) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS moving_window_t0,
                last_value (point_timestamp) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS moving_window_t1,
                first_value (point_altitude) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS point_altitude_at_t0,
                last_value (point_altitude) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS point_altitude_at_t1,
                SUM(CASE WHEN ascent_in_meter_between_points > 0 THEN ascent_in_meter_between_points ELSE 0 END) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) AS ascent_in_meter_since_start,
                SUM(CASE WHEN ascent_in_meter_between_points < 0 THEN ascent_in_meter_between_points ELSE 0 END) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) AS descent_in_meter_since_start,
                SUM(distance_in_meter_between_points) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) AS distance_in_meter_between_points_since_start,
                CASE
                    WHEN duration_between_points_in_sec = 0 THEN NULL
                    ELSE distance_in_meter_between_points / duration_between_points_in_sec * 3.6
                    END AS speed_in_kmh, -- speed in meters per second
            CASE
                WHEN distance_in_meter_between_points = 0 THEN NULL
                ELSE (ascent_in_meter_between_points / distance_in_meter_between_points) * 100
                END AS slope_percentage -- slope in percentage (ascent per distance)
        FROM points_with_delta
    ),
    q2 as (
        select
            p.*,
            first_value (distance_in_meter_between_points_since_start) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS distance_at_t0,
                last_value (distance_in_meter_between_points_since_start) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS distance_at_t1,
                first_value (ascent_in_meter_since_start) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS ascent_at_t0,
                last_value (ascent_in_meter_since_start) OVER (
		      PARTITION BY gps_track_data_id
		      ORDER BY point_timestamp
		      RANGE BETWEEN INTERVAL :moving_window /2 PRECEDING AND INTERVAL :moving_window /2 FOLLOWING
		    ) AS ascent_at_t1,
                    point_x - lag(point_x) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) as diff_x,
                    point_y - lag(point_y) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) as diff_y,
                    point_altitude - lag(point_altitude) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) as diff_z
        from
            q1 p
    ),
-- calculation of total ascent and descent
    q3 AS (
        select
            gps_track_id,
            gps_track_data_id,
            extract(epoch from (interval :moving_window)) moving_window_in_sec,
            point_index,
            point_index_total,
            point_timestamp,
            point_long_lat,
            --point_long,
            --point_lat,
            round(cast (point_x as numeric),1) point_x,
            round(cast (point_y as numeric),1) point_y,
            round(cast (point_altitude as numeric),1) point_altitude,
            round(cast (distance_in_meter_between_points as numeric),1) distance_in_meter_between_points,
            --round(cast(sqrt(power(diff_x, 2) + power(diff_y, 2) + power(diff_z,2)) as numeric), 1) AS distance_in_meter_between_points_3d,
            round(cast (distance_in_meter_between_points_since_start as numeric),1) distance_in_meter_since_start,
            duration_between_points_in_sec,
            point_timestamp - FIRST_VALUE(point_timestamp) OVER (PARTITION BY gps_track_data_id ORDER BY point_index) AS duration_since_start,
                round(cast (ascent_in_meter_between_points as numeric),1) ascent_in_meter_between_points,
            round(cast(ascent_in_meter_since_start as numeric),1) AS ascent_in_meter_since_start,
            round(cast(descent_in_meter_since_start as numeric),1) AS descent_in_meter_since_start,
            --round(cast (((ascent_at_t1 - ascent_at_t0) / NULLIF(extract( epoch from (moving_window_t1-moving_window_t0)), 0)*3600) as numeric), 1) elevation_gain_per_hour_moving_window,
            round(cast (((point_altitude_at_t1 - point_altitude_at_t0) / NULLIF(extract( epoch from (moving_window_t1-moving_window_t0)), 0)*3600) as numeric), 1) elevation_gain_per_hour_moving_window,
            round(cast (((distance_at_t1-distance_at_t0) / NULLIF(extract( epoch from (moving_window_t1-moving_window_t0)), 0)*3.6) as numeric), 1) speed_in_kmh_moving_window,
            --round(cast (speed_in_kmh as numeric),1) speed_in_kmh,
            --round(cast (slope_percentage as numeric),1) slope_percentage,
            round(cast ((point_altitude_at_t1 - point_altitude_at_t0) / nullif((distance_at_t1 - distance_at_t0), 0) * 100 as numeric),1) AS slope_percentage_in_moving_window
        FROM
            q2
    )
select * from q3
order by point_index asc
;



select * from gps_track_data where gps_track_id  = 102540;
select * from gps_track_v gtv where id = 102540;
select * from gps_track_v gtv order by start_date desc;
select * from gps_track_v gtv where gd_id = 200301;

select count(1) from gps_track gt ;
select count(1), sum( ST_NPoints(track)) from gps_track_data gtd ;
