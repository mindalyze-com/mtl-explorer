# Packet: PLN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_04
- In scope: Move/delete waypoints and verify clear, undo, and redo.
- Out of scope: Save/load/delete saved plans.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_03.
- Required app/data state: Three-waypoint, two-leg temporary route.
- Required browser context: Desktop Planner.

## Allowed Mutations

- Allowed: Move/select/delete temporary waypoints and use edit history.
- Not allowed: Persist the route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_04 | Move the inserted waypoint, select/delete it, clear the route, then undo and redo. | Every edit and history action updates/restores the plan correctly. | Move recomputed the two-leg route, delete restored one leg, clear reached zero, undo restored 5.13 km/one leg, and redo returned to zero. | PASS | [assets/PLN_04-edit-history.txt](../assets/PLN_04-edit-history.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_04-edit-history.txt](../assets/PLN_04-edit-history.txt) | Sequential move/delete/clear/undo/redo evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; all results are captured through accessible control/stat states.

## Timings

| Step | Timing |
|---|---:|
| Move and delete waypoint | 2 min |
| Clear, undo, redo | 1 min |

## Handoff Notes

- Completed: Waypoint movement/deletion and edit-history flow.
- Remaining unfinished coverage: None for PLN_04.
- Blocked or not applicable: None.
- State left for the next packet: Route restored once for further Planner checks.
