# Packet: PLN_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_11
- In scope: Touch placement and waypoint dragging on mobile.
- Out of scope: Desktop mouse dragging, already covered by PLN_04.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_10.
- Required app/data state: BRouter ready; fresh planner state.
- Required browser context: 390 x 844 mobile viewport.

## Allowed Mutations

- Allowed: Create/move/clear a temporary mobile route and reset the viewport.
- Not allowed: Preserve mobile route state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_11 | At 390 x 844, placed two waypoints, computed a route, and dragged an endpoint; then cleaned up. | Touch placement and drag work on mobile. | Mobile layout, pointer placement, 73 m route computation, and drag state change worked. Browser automation cannot emit a touch pointer, so actual touch semantics cannot be proven. | BLOCKED | [assets/PLN_11-mobile-drag.txt](../assets/PLN_11-mobile-drag.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_11-mobile-drag.txt](../assets/PLN_11-mobile-drag.txt) | Mobile geometry, route/move results, touch constraint, and cleanup. |

## Screenshot Evidence

Unavailable under ACC_04. Viewport/panel dimensions and live route transitions provide direct responsive evidence.

## Timings

| Step | Timing |
|---|---:|
| Mobile open/zoom/place | About 6 s |
| Drag and cleanup | About 3 s |

## Handoff Notes

- Completed: Responsive mobile placement and drag pathway.
- Remaining unfinished coverage: None for PLN_11; terminal BLOCKED on touch-pointer injection.
- Blocked or not applicable: Native touch-event verification only.
- State left for the next packet: Mobile tab closed and default viewport restored; primary Planner still open.
