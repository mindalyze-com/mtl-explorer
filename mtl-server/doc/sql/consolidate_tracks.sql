WITH selected_tracks AS (
    -- Select and transform the tracks to a coordinate system with meters as units (EPSG:3857)
    SELECT
        gt.id AS track_id,
        ST_Transform(gd.track, 3857) AS track_geom,
        ROW_NUMBER() OVER (ORDER BY gt.id) AS rn
    FROM
        gps_track gt
            JOIN gps_track_data gd ON gt.id = gd.gps_track_id
    WHERE
        --      gt.gd_id IN  (  220911,200971,211958,215313,222527,229774) -- ALIEN 201342
        gd.id IN  (  200955,201473,202126,209368,211942,220892,222516,229748) -- ALIEN 223609, 216212
),
     reference_track AS (
         -- Choose one track as the reference for ordering
         SELECT
             track_geom
         FROM
             selected_tracks
         WHERE
             rn = 1
     ),
     track_points AS (
         -- Extract points from all tracks
         SELECT
             st.track_id,
             (dp).path[1] AS point_sequence,
             (dp).geom AS point_geom
         FROM
             selected_tracks st,
             LATERAL ST_DumpPoints(st.track_geom) AS dp
     ),
     clusters AS (
         -- Cluster points based on spatial proximity using DBSCAN
         SELECT
             *,
             --ST_ClusterDBSCAN(point_geom, eps := 5, minpoints := 1) OVER () AS cluster_id
             ST_ClusterKMeans(point_geom, k := 500) OVER () AS cluster_id  -- Set the number of clusters
         FROM
             track_points
     ),
     cluster_centroids AS (
         -- Compute the centroid of each cluster
         SELECT
             cluster_id,
             ST_Centroid(ST_Collect(point_geom)) AS centroid_geom
         FROM
             clusters
         GROUP BY
             cluster_id
     ),
     ordered_centroids AS (
         -- Order centroids along the reference track
         SELECT
             centroid_geom,
             ST_LineLocatePoint(ref.track_geom, centroid_geom) AS measure
         FROM
             cluster_centroids,
             reference_track ref
     ),
     averaged_track AS (
         -- Build the averaged track from ordered centroids
         SELECT
             ST_MakeLine(centroid_geom ORDER BY measure) AS geom,
             ST_Length(ST_MakeLine(centroid_geom ORDER BY measure)) AS total_length
         FROM
             ordered_centroids
     ) ,
     line_length AS (
         -- Calculate the total length of the linestring
         SELECT
             ST_Length(geom) AS total_length,
             geom
         FROM
             averaged_track
     ),
     interpolated_points AS (
         -- Generate points at every 100 meters along the linestring, if total_length is greater than 0
         SELECT
             ST_LineInterpolatePoint(geom, gs / total_length) AS point_geom,
             gs AS distance_from_start
         FROM
             line_length,
             generate_series(0, total_length::numeric, :point_distance) AS gs -- Generates a series every :point_distance meters
         WHERE
             total_length > 0 -- Ensure that we only interpolate if the total length is greater than 0
     )
-- Select the resulting points
SELECT
    point_geom,
    distance_from_start
FROM
    interpolated_points
ORDER BY
    distance_from_start ASC;


select * from gps_track_v gtv where gd_id IN  (   220911,200971,211958, 222527,229774,201473, 201500,202136,209401,211958)
