# Packet: MAP_14

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_14
- In scope: Verify remote raster fallback from local vector mode when local PMTiles are unavailable.
- Out of scope: Intentional remote mode and manual source override, covered by MAP_13 and MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_13.
- Required app/data state: Local-vector deployment with a safe way to make PMTiles unavailable.
- Required browser context: desktop map tab.

## Allowed Mutations

- Allowed: Use a safe, scoped tile-unavailability control if present.
- Not allowed: Break the app broadly, corrupt data, or stop unrelated services.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_14 | Audited the quick-install stack and map status/config for a safe local-vector tile failure control. | If local PMTiles can be safely made unavailable, the app falls back to remote raster and remains usable; if not, the row is blocked. | The quick-install stack has no local map-server sidecar or safe PMTiles failure switch; normal mode uses a hosted/public map-proxy fallback, so the required local-vector failure could not be simulated safely. | BLOCKED | [assets/MAP_14-local-vector-fallback-blocked.txt](../assets/MAP_14-local-vector-fallback-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_14-local-vector-fallback-blocked.txt](../assets/MAP_14-local-vector-fallback-blocked.txt) | Missing safe-control rationale for local-vector fallback simulation. |

## Screenshot Evidence

No screenshot required for this deployment-control packet.

## Timings

| Step | Timing |
|---|---:|
| Fallback control audit | <1 min |

## Handoff Notes

- Completed: MAP_14 as terminal BLOCKED.
- Remaining unfinished coverage: MAP_15 onward.
- Blocked or not applicable: Unblock with a local-maps profile/map-server sidecar or a safe PMTiles-blocking control.
- State left for the next packet: Temporary remote-mode override from MAP_13 is still active; restore local/Auto mode before MAP_15.
