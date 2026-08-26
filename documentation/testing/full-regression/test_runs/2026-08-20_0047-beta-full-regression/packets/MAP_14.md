# Packet: MAP_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_14
- In scope: Runtime local-vector failure and configured remote-raster fallback.
- Out of scope: Manual source override, covered by MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_13.
- Required app/data state: Original Compose app healthy in local mode; OSM Dark selected; nine-track cache/result available.
- Required browser context: Signed-in local-vector main map.

## Allowed Mutations

- Allowed: Briefly stop/start only the disposable app container after staging an uncached location.
- Not allowed: Stop database/helpers, change image, or modify data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_14 | Stop app after staging New York; select location to fail PMTiles; pan/zoom; restore app; recenter on Bern and click tracks; reload to reset. | Map switches to configured remote style and keeps pan, zoom, tracks, and selection usable. | Client logged runtime fallback and showed CARTO dark raster; pan/zoom worked during outage; after recovery, Bern click listed three expected tracks; reload restored healthy local vector. | PASS | [assets/MAP_14-runtime-fallback.txt](../assets/MAP_14-runtime-fallback.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_14-runtime-fallback.txt](../assets/MAP_14-runtime-fallback.txt) | Baseline, controlled outage, fallback warning/UI, interaction, track selection, and restoration. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact attribution, warning, controls, selection list, and recovery state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Uncached location selection to fallback observation | 5.315 s |
| Original app recovery to HTTP 200 | <30 s |

## Handoff Notes

- Completed: Runtime fallback, interactive raster map, track display/selection, service recovery, and local-vector reset.
- Remaining unfinished coverage: None for MAP_14.
- Blocked or not applicable: None.
- State left for the next packet: Healthy original Compose app and local-vector browser state; OSM Dark remains selected in Automatic source mode.
