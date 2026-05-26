# Packet: PLN_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_03
- In scope: inserting a waypoint by dragging an existing route leg.
- Out of scope: ordinary map panning and waypoint move/delete checks, covered separately.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: active unsaved planner route with one computed leg.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: drag route geometry, pan map to expose route, clear/rebuild temporary route for retry.
- Not allowed: save a failed route or change imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_03 | Tried dragging the existing route leg to a new point; then cleared/rebuilt a visible one-leg route and retried dragging the visible route line. | Dragging an existing route leg inserts a waypoint and recomputes route with multiple legs. | Dragging the leg panned/shifted the map or left the route unchanged; the route stayed at 1 leg with unchanged stats after retry. | FAIL | `assets/PLN_03-route-drag-insert.webp`, `assets/PLN_03-route-rebuilt.webp`, `assets/PLN_03-route-drag-retry.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| PLN-01 | P2 | Dragging an existing planner route leg did not insert a waypoint. | Create a planner route, then drag the visible route leg. | A new waypoint is inserted on the leg and the route recomputes with an additional leg. | The map panned or the route stayed unchanged; leg count remained 1. | `assets/PLN_03-route-drag-retry.webp` | Planner route editing cannot add intermediate waypoints through the documented drag-leg workflow. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_03-route-drag-insert.webp | First drag attempt after PLN_02 route. |
| assets/PLN_03-route-rebuilt.webp | Clean rebuilt one-leg route before retry. |
| assets/PLN_03-route-drag-retry.webp | Retry result with unchanged one-leg route. |
| assets/PLN_03-sheet-drag-attempt.webp | Attempt to expose route by dragging planner sheet. |
| assets/PLN_03-sheet-closed.webp | Planner sheet close exposed map but hid/deactivated visible route controls. |
| assets/PLN_03-planner-reopened.webp | Planner route state restored after reopening. |
| assets/PLN_03-pan-reveal-route.webp | Map pan attempt while keeping one-leg route active. |

## Timings

| Step | Timing |
|---|---:|
| Route insertion attempts | <10m |

## Handoff Notes

- Completed: PLN_03 terminal FAIL; issue PLN-01 recorded.
- Remaining unfinished coverage: continue with PLN_04.
- Blocked or not applicable: none.
- State left for the next packet: unsaved one-leg planner route remains active.
