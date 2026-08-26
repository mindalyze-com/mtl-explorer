# Packet: APP_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_07
- In scope: Selected map style persists across reload.

## Prerequisites

- Required previous coverage IDs or run packets: APP_06.
- Required app/data state: OSM Dark selected with Automatic source; UI theme light.
- Required browser context: `/mtl/map-settings`.

## Allowed Mutations

- Allowed: Reload the live map-settings route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_07 | Reloaded with OSM Dark selected and inspected the restored route/current-map summary. | Selected map style persists. | Route and Current map both restored OSM Dark/Automatic/2D; 8 Tracks remained available and UI stayed independently light. | PASS | [assets/APP_07-map-style-persistence.txt](../assets/APP_07-map-style-persistence.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_07-map-style-persistence.txt](../assets/APP_07-map-style-persistence.txt) | Before/after route, style, source, and UI state. |

## Screenshot Evidence

Direct route/summary state is durable; ACC_04 prevents saved basemap screenshots.

## Timings

| Step | Timing |
|---|---:|
| Reload and settlement | About 0.9 s |

## Handoff Notes

- Completed: OSM Dark persistence across reload.
- Remaining unfinished coverage: None for APP_07.
- Blocked or not applicable: Durable basemap screenshot only.
- State left for the next packet: Light UI; Map overview open; OSM Dark/Automatic/2D restored.
