Point 0/0

Track: /app/gpx/2020-07-10_10-34-54_Other.gpx


The GPS file has an entry, which jumps back in time..

      <trkpt lat="47.165751000" lon="8.486844000">
        <ele>462.698</ele>
        <time>2020-07-10T08:37:15Z</time>
      </trkpt>
      <trkpt lat="47.165751000" lon="8.486844000">
        <time>2020-07-10T07:57:23Z</time>
      </trkpt>
      <trkpt lat="47.176965000" lon="8.496014000">
        <ele>464.235</ele>
        <time>2020-07-10T07:57:26Z</time>
      </trkpt>


DB Value for 10m simplified:
LINESTRING ZM(8.48789 47.16722 465.7814511117262 1594370094, NaN NaN NaN 1594380704)

The RAW data string, does NOT have the issue...



Query on the correct RAW reproduces the wrong corrupted track:
```
select
    gt.id as gps_track_id,
    gtd.id as gps_track_data_id,
    gtd.track_type,
    st_asewkt(
        st_snaptogrid(
            st_transform(
                st_simplify(
                    st_transform(track, 3857),
                    10
                ),
                4326
            ),
            0.00001
        )
    ) as line_string
from gps_track gt
join gps_track_data gtd on gt.id = gtd.gps_track_id
where gt.id = 104449
  and gtd.track_type = 'RAW_OUTLIER_CLEANED';
```

SRID=4326;LINESTRING(8.48789 47.16722 465.7814511117262 1594370094,NaN NaN NaN 1594380704)


Checking the behaviour with artificial data :
```
WITH test_data AS (
    -- 1. Control case: Normal progression of time and elevation
    SELECT 
        '1. Normal (Control)' as scenario, 
        ST_GeomFromEWKT('SRID=4326;LINESTRING ZM(8.4868 47.1657 462.6 1594370200, 8.4868 47.1657 462.6 1594370210, 8.4960 47.1769 464.2 1594370220)') as geom
    UNION ALL
    -- 2. Only missing elevation (NaN) for the middle point, but time is monotonic
    SELECT 
        '2. Missing Elevation (NaN Z)', 
        ST_GeomFromEWKT('SRID=4326;LINESTRING ZM(8.4868 47.1657 462.6 1594370200, 8.4868 47.1657 NaN 1594370210, 8.4960 47.1769 464.2 1594370220)') as geom
    UNION ALL
    -- 3. Only backward time jump, but elevation is valid
    SELECT 
        '3. Backward Time Jump', 
        ST_GeomFromEWKT('SRID=4326;LINESTRING ZM(8.4868 47.1657 462.6 1594370235, 8.4868 47.1657 462.6 1594367843, 8.4960 47.1769 464.2 1594367846)') as geom
    UNION ALL
    -- 4. The exact issue case: Identical X/Y, NaN Z, and backward jumping M
    SELECT 
        '4. Backward Time Jump + NaN Z', 
        ST_GeomFromEWKT('SRID=4326;LINESTRING ZM(8.486844 47.165751 462.698 1594370235, 8.486844 47.165751 NaN 1594367843, 8.496014 47.176965 464.235 1594367846)') as geom
)
SELECT 
    scenario,
    ST_AsEWKT(geom) as original_geom,
    ST_AsEWKT(
        ST_SnapToGrid(
            ST_Transform(
                ST_Simplify(
                    ST_Transform(geom, 3857), 
                    10 -- 10m precision (tolerance)
                ), 
                4326
            ), 
            0.00001
        )
    ) as simplified_geom
FROM test_data;
```

Result of the query above against Postgres:
Missing elevation has a NAN... Backward time too, but only if no Z..
```
1. Normal (Control)	SRID=4326;LINESTRING(8.4868 47.1657 462.6 1594370200,8.4868 47.1657 462.6 1594370210,8.496 47.1769 464.2 1594370220)	SRID=4326;LINESTRING(8.4868 47.1657 462.6 1594370200,8.496 47.1769 464.2 1594370220)
2. Missing Elevation (NaN Z)	SRID=4326;LINESTRING(8.4868 47.1657 462.6 1594370200,8.4868 47.1657 NaN 1594370210,8.496 47.1769 464.2 1594370220)	SRID=4326;LINESTRING(8.4868 47.1657 462.6 1594370200,8.496 47.1769 464.2 1594370220)
3. Backward Time Jump	SRID=4326;LINESTRING(8.4868 47.1657 462.6 1594370235,8.4868 47.1657 462.6 1594367843,8.496 47.1769 464.2 1594367846)	SRID=4326;LINESTRING(8.4868 47.1657 462.6 1594370235,8.496 47.1769 464.2 1594367846)
4. Backward Time Jump + NaN Z	SRID=4326;LINESTRING(8.486844 47.165751 462.698 1594370235,8.486844 47.165751 NaN 1594367843,8.496014 47.176965 464.235 1594367846)	SRID=4326;LINESTRING(8.48684 47.16575 462.698 1594370235,8.49601 47.17696 464.235 1594367846)```
```