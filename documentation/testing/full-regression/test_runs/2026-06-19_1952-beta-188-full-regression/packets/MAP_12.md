# Packet: MAP_12

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_12
- In scope: Verify Swiss Mobility/routes popup shows nearby official routes and closes cleanly where applicable.
- Out of scope: General map provider mode checks, covered by MAP_13-MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_11.
- Required app/data state: Map route overlays available.
- Required browser context: desktop map tab with deterministic feature hit targeting.

## Allowed Mutations

- Allowed: Inspect map settings and click route features.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_12 | Inspected map config/status and Map settings for Swiss/route overlay availability, then evaluated whether the popup could be clicked. | If route overlays are applicable, clicking nearby official routes opens a popup that closes cleanly. | Swiss map themes and route overlay labels are present, so the row is applicable, but the required canvas-rendered route popup could not be targeted or visually verified with available browser controls. | BLOCKED | [assets/MAP_12-swiss-routes-blocked.txt](../assets/MAP_12-swiss-routes-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_12-swiss-routes-blocked.txt](../assets/MAP_12-swiss-routes-blocked.txt) | Map config/status, Swiss overlay DOM evidence, and popup blocking rationale. |

## Screenshot Evidence

No screenshot asset was captured for this packet; the required popup interaction was blocked by canvas hit-targeting limits.

## Timings

| Step | Timing |
|---|---:|
| Config/UI applicability check | ~2 min |

## Handoff Notes

- Completed: MAP_12 as terminal BLOCKED.
- Remaining unfinished coverage: MAP_13 onward.
- Blocked or not applicable: Unblock with manual visual browser testing or instrumented map feature-hit targeting for route overlays.
- State left for the next packet: Map settings remains open; dataset remains 14 API tracks / 13 visible simplified tracks.
