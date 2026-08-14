# Packet: MAP_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MAP_14.
- In scope: audit and, when safely possible, simulate local-vector PMTiles unavailability.
- Out of scope: break the whole app or bypass a missing scoped control.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_13.
- Required app/data state: original local tile-mode deployment restored on frozen image 1.331.
- Required browser context: remote-theme browser state is not used as fallback proof.

## Allowed Mutations

- Allowed: inspect Compose services and authenticated map status/config.
- Not allowed: stop the application itself or create a non-scoped failure that cannot distinguish fallback behavior.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| MAP_14 | Restored local mode and audited the quick-install service topology, map status, and config for a safe local PMTiles failure control. | If safely controllable, local-vector failure switches to remote raster and stays interactive; otherwise record the missing control. | No local map-server sidecar or isolated PMTiles-failure switch exists. Normal `tileMode: local` is already using a hosted/public PMTiles proxy fallback, so local archive failure cannot be safely simulated in this target. | BLOCKED | [assets/MAP_14-fallback-control.txt](../assets/MAP_14-fallback-control.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_14-fallback-control.txt](../assets/MAP_14-fallback-control.txt) | Restored image/mode, live fallback state, topology, and missing safe control. |

## Screenshot Evidence

No screenshot can prove an unexecutable failure condition; the live status/config evidence is authoritative.

## Timings

| Step | Timing |
|---|---:|
| Restore local mode and readiness | 27 s |
| Scoped-control audit | < 1 min |

## Handoff Notes

- Completed: required fallback-control audit.
- Remaining unfinished coverage: MAP_15 onward.
- Blocked or not applicable: MAP_14 is terminal `BLOCKED`; needs a local-maps profile/map-server sidecar or safe PMTiles-blocking control.
- State left for the next packet: original local mode and frozen image restored; browser still has prior OSM Dark preference until reloaded.
