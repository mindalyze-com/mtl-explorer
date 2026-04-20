# Track to Africa (Null Island) - Bug Analysis

## The Problem
When a GPX file (e.g., `2020-07-10_10-34-54_Other.gpx`) lacked elevation (`<ele>`) tags on certain points, the `GPXReader` defaulted their `Z` coordinate to `Double.NaN`. 

During simplification in the database, PostGIS executes `ST_Transform(track, 3857)` which passes the 3D/4D geometry to the PROJ library. The PROJ math cannot handle `NaN` in the Z-axis, which causes the `NaN` to propagate and corrupt the X and Y coordinates as well. This resulted in `NaN NaN NaN [timestamp]` geometries. 

Finally, the backend JSON serializer (`LineStringSerializer`) blindly converted these `NaN` coordinates to `0.0`, rendering the track points at 0/0 (Null Island, off the coast of Africa). Backward time jumps (`M` jumps) were investigated but PostGIS was able to handle them gracefully; the missing `Z` coordinate was the sole culprit.

## Reproduction
We isolated the issue by running artificial geometry calculations directly in PostgreSQL:
```sql
SELECT ST_AsEWKT(
    ST_SnapToGrid(
        ST_Transform(
            ST_Simplify(
                ST_Transform(
                    ST_GeomFromEWKT('SRID=4326;LINESTRING ZM(8.486 47.165 462 1594370, 8.486 47.165 NaN 1594371)'), 
                    3857
                ), 
                10
            ), 
            4326
        ), 
        0.00001
    )
);
```
This confirmed that `NaN` elevations break PostGIS spatial transformations.

## Resolution
1. **GPXReader.java**: Introduced a `lastElevation` state (defaulting to `0.0`). If a waypoint has no elevation, it now inherits the `lastElevation` instead of `Double.NaN`. This completely prevents `NaN`s from entering the `track_raw` table.
2. **LineStringSerializer.java**: Added a strict failsafe checking `Double.isNaN()` and `Double.isInfinite()` on X and Y coordinates. If a severely corrupted geometry point ever reaches serialization, it is skipped and dropped from the output instead of defaulting to `0.0`.