# Packet: MAP_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_14
- In scope: Runtime remote-raster fallback when local-vector PMTiles become unavailable.
- Out of scope: Intentional remote deployment and manual source override, covered by MAP_13 and MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_13.
- Required app/data state: Original local tile mode restored with remote styles configured.
- Required browser context: Selected in-app browser and its advertised capabilities.

## Allowed Mutations

- Allowed: Stop a disposable local map-server sidecar or block only local PMTiles requests when such a safe control exists.
- Not allowed: Disable broad server/browser networking or introduce an uncontrolled new map deployment.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_14 | Restored local mode, checked config/status, running Compose topology, image identity, and browser request-control capabilities. | If safely controllable, local PMTiles failure switches to remote raster and preserves map interaction; otherwise record the missing control. | Quick install is already in hosted `public-fallback`, has no running local map-server sidecar, and the browser has no request blocking/interception capability. No safe isolated PMTiles failure control exists. | BLOCKED | [assets/MAP_14-fallback-control.txt](../assets/MAP_14-fallback-control.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_14-fallback-control.txt](../assets/MAP_14-fallback-control.txt) | Restored config/image, hosted fallback status, topology, and missing safe-control evidence. |

## Screenshot Evidence

Not applicable to the missing-control audit; screenshots remain blocked by ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Restore app to original local mode | About 26 s |
| Config/status/topology/control audit | Under 5 s |

## Handoff Notes

- Completed: Original deployment restoration and safe fallback-control audit.
- Remaining unfinished coverage: None; terminally blocked by the missing isolated failure control.
- Blocked or not applicable: Needs a running disposable local map-server sidecar or browser PMTiles request interception.
- State left for the next packet: Healthy local-mode app on original beta image; optional remote override file exists but is not applied.
