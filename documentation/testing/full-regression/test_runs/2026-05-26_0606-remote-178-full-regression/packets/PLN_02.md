# Packet: PLN_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_02
- In scope: adding waypoints on the map and computing/drawing a route.
- Out of scope: waypoint insertion/move/delete and save/download workflows.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_01.
- Required app/data state: Planner open in Drawing mode with Road Bike selected.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: zoom map and add planner waypoints.
- Not allowed: save or delete persisted plans.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_02 | Zoomed past the planner span guard and clicked two points on the visible map. | Map clicks add waypoints, and the planner computes and draws a route. | After zooming to 10 km scale, two map clicks enabled Undo/Clear/Save, produced 1 leg, 1.93 km distance, 5 m descent, and 4m duration. | PASS | `assets/PLN_02-zoomed-under-span.webp`, `assets/PLN_02-route-computed.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_02-zoomed-under-span.webp | Planner ready at sufficiently close zoom. |
| assets/PLN_02-route-computed.webp | Two-waypoint computed route stats. |
| assets/PLN_02-zoomed-ready.webp | Earlier zoom stage still showing guard. |
| assets/PLN_02-current-zoom.webp | Intermediate map context before final route clicks. |

## Timings

| Step | Timing |
|---|---:|
| Zoom and two waypoint clicks | <5m |

## Handoff Notes

- Completed: PLN_02 terminal PASS.
- Remaining unfinished coverage: continue with PLN_03.
- Blocked or not applicable: none.
- State left for the next packet: unsaved two-waypoint route remains active with one computed leg.
