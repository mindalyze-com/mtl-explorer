# Packet: PLN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_04
- In scope: Move/delete waypoints and clear/undo/redo.
- Out of scope: Route save/load.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_03.
- Required app/data state: Computed two-waypoint route.
- Required browser context: Desktop Planner.

## Allowed Mutations

- Allowed: Move/select/delete waypoints and use history/clear controls.
- Not allowed: Leave the route cleared.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_04 | Moved and selected/deleted an endpoint; used Undo, Redo, Clear, and Undo Clear while recording metrics. | Waypoint edits and all history controls work. | Move recomputed 2.93 km to 710 m; accessible delete removed the route; undo/redo reproduced both states; clear removed all and undo restored the 710 m route. | PASS | [assets/PLN_04-edit-history.txt](../assets/PLN_04-edit-history.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_04-edit-history.txt](../assets/PLN_04-edit-history.txt) | Metric transitions for move/delete/history/clear and final state. |

## Screenshot Evidence

Unavailable under ACC_04. The selected-waypoint delete DOM control and exact route-state transitions provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Move and delete | About 3 s |
| Undo/redo/clear cycle | About 3 s |

## Handoff Notes

- Completed: Move, delete, clear, undo, and redo.
- Remaining unfinished coverage: None for PLN_04.
- Blocked or not applicable: None.
- State left for the next packet: Moved two-waypoint route restored at 710.00 m / one leg.
