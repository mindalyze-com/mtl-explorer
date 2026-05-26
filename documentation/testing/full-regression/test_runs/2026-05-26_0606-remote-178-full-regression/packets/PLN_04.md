# Packet: PLN_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_04
- In scope: move and delete waypoints; clear, undo, and redo.
- Out of scope: persisted saved-route delete, covered by PLN_07.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: active planner route or ability to rebuild temporary route.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: edit the unsaved planner route.
- Not allowed: save or delete persisted plans.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_04 | Rebuilt a two-waypoint route, dragged one endpoint, deleted the selected waypoint, then exercised undo/redo and clear/undo/redo. | Waypoints can be moved and deleted; clear, undo, and redo all work. | Dragging the selected waypoint changed distance from 0.33 km to 5.79 km; `Delete selected waypoint` reduced the route; Undo restored it, Redo deleted it again; Clear set 0.00 km/0 legs; Undo restored route, Redo cleared it. | PASS | `assets/PLN_04-route-before-move.webp`, `assets/PLN_04-waypoint-moved.webp`, `assets/PLN_04-waypoint-deleted.webp`, `assets/PLN_04-undo-restored.webp`, `assets/PLN_04-redo-deleted.webp`, `assets/PLN_04-cleared.webp`, `assets/PLN_04-undo-clear-restored.webp`, `assets/PLN_04-redo-clear.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_04-route-before-move.webp | Rebuilt two-waypoint route before editing. |
| assets/PLN_04-waypoint-moved.webp | Moved waypoint with updated route stats. |
| assets/PLN_04-waypoint-deleted.webp | Selected waypoint deleted. |
| assets/PLN_04-undo-restored.webp | Undo after waypoint deletion restored route. |
| assets/PLN_04-redo-deleted.webp | Redo after undo deleted route state again. |
| assets/PLN_04-cleared.webp | Clear route emptied route stats. |
| assets/PLN_04-undo-clear-restored.webp | Undo after clear restored route. |
| assets/PLN_04-redo-clear.webp | Redo after undo clear emptied route again. |

## Timings

| Step | Timing |
|---|---:|
| Edit/delete/clear/undo/redo workflow | <8m |

## Handoff Notes

- Completed: PLN_04 terminal PASS.
- Remaining unfinished coverage: continue with PLN_05.
- Blocked or not applicable: none.
- State left for the next packet: planner route is cleared; undo is available to restore the temporary route.
