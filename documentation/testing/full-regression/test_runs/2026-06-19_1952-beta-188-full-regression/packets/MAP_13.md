# Packet: MAP_13

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_13
- In scope: Verify intentional remote raster mode exposes required remote styles, selects OSM Light/Topo/Dark, shows attribution, keeps the map interactive, and avoids `/api/map-proxy` tile requests.
- Out of scope: Local-vector fallback and manual source override, covered by MAP_14 and MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_12.
- Required app/data state: Quick-install stack running with current dataset.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Add temporary compose override for `MTL_MAP_SERVER_TILE_MODE=remote` and recreate only the app service.
- Not allowed: Change imported data or permanently leave deployment mode unrecorded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_13 | Added temporary compose override setting app remote mode, restarted app, checked `/api/map/config`, selected OSM Light/Topo Light/Dark in Map settings, checked attribution/map count, and grepped recent app logs for `/api/map-proxy`. | Remote mode exposes `remoteRasterStyles` for light/light-topo/dark, no legacy `remoteTileUrl`, required styles load with matching attribution, map remains interactive, and no `/api/map-proxy` tile requests are made. | Config showed `tileMode=remote`, remote styles `dark`, `light`, `light-topo`, no `remoteTileUrl`, and null tile archive URLs. OSM Light/Topo Light/Dark each became active with matching attribution and 13 Tracks visible. Recent app logs contained no `/api/map-proxy` matches. | PASS | [assets/MAP_13-remote-raster-mode.txt](../assets/MAP_13-remote-raster-mode.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_13-remote-raster-mode.txt](../assets/MAP_13-remote-raster-mode.txt) | Override, config, UI theme, attribution, and proxy-log evidence. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct config/DOM/log evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Remote override/app restart/readiness | ~1 min |
| Remote theme UI checks | ~2 min |

## Handoff Notes

- Completed: MAP_13.
- Remaining unfinished coverage: MAP_14 onward.
- Blocked or not applicable: none.
- State left for the next packet: The temporary remote-mode compose override is still active and must be restored before local-vector MAP_15 and final cleanup.
