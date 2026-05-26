# Packet: PLN_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_05
- In scope: planner live stats bar updates while editing.
- Out of scope: route persistence and GPX export.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02, PLN_04.
- Required app/data state: temporary planner route with editable waypoints.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: reuse direct edit evidence from PLN_04.
- Not allowed: save or delete persisted plans.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_05 | Reviewed the live stats bar before and after moving/deleting/clearing the planner route during PLN_04. | Live stats bar updates distance, ascent, time, and leg count as the route is edited. | Stats changed from 0.33 km/0m/0m/0m/1 leg to 5.79 km/1m/6m/15m/1 leg after moving a waypoint, then returned to 0.00 km/0 legs after delete/clear states. | PASS | `assets/PLN_04-route-before-move.webp`, `assets/PLN_04-waypoint-moved.webp`, `assets/PLN_04-waypoint-deleted.webp`, `assets/PLN_04-cleared.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_04-route-before-move.webp | Initial live stats for temporary route. |
| assets/PLN_04-waypoint-moved.webp | Live stats after moving waypoint. |
| assets/PLN_04-waypoint-deleted.webp | Live stats after waypoint deletion. |
| assets/PLN_04-cleared.webp | Live stats after clearing route. |

## Timings

| Step | Timing |
|---|---:|
| Live stats evidence review | <1m |

## Handoff Notes

- Completed: PLN_05 terminal PASS.
- Remaining unfinished coverage: continue with PLN_06.
- Blocked or not applicable: none.
- State left for the next packet: planner route remains cleared; undo can restore the temporary route.
