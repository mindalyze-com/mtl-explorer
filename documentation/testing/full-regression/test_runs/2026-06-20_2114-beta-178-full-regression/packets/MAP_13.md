# Packet: MAP_13

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_13
- In scope: Determine whether intentional remote raster server mode applies to this run.
- Out of scope: Manual Remote map-source override in a local-vector deployment; covered by MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: live app reachable.
- Required browser context: authenticated browser able to query `/mtl/api/map/config`.

## Allowed Mutations

- Allowed: read live map config.
- Not allowed: restart/reconfigure the server into a different tile mode for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_13 | Queried live `/mtl/api/map/config` and checked the configured tile mode and remote raster fields. | This row applies when the server is started with `mtl.map-server.tile-mode=remote`; then OSM Light/Topo/Dark remote raster loading must be exercised. | NOT APPLICABLE: this quick-install target is configured as `tileMode: local` with `/mtl/api/map-proxy/prod`; remote raster style definitions exist, but the intentional remote server-mode precondition is not active. | NOT APPLICABLE | [assets/MAP_13-remote-tile-mode-config.txt](../assets/MAP_13-remote-tile-mode-config.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_13-remote-tile-mode-config.txt](../assets/MAP_13-remote-tile-mode-config.txt) | Live map config and applicability decision. |

## Screenshot Evidence

No screenshot: config-only applicability check.

## Timings

| Step | Timing |
|---|---:|
| Config query | ~2 seconds |

## Handoff Notes

- Completed: MAP_13 is terminal as NOT APPLICABLE.
- Remaining unfinished coverage: MAP_14 onward.
- Blocked or not applicable: server-level remote tile mode is not active in this configured run.
- State left for the next packet: no mutations.
