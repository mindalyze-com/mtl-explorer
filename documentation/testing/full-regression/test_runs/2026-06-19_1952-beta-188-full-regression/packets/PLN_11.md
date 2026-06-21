# Packet: PLN_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_11
- In scope: Mobile touch placement and touch dragging of planner waypoints.
- Out of scope: Desktop mouse planner behavior already covered by PLN_01 through PLN_10.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_01 through PLN_10
- Required app/data state: Authenticated session available for a separate mobile browser context.
- Required browser context: Temporary mobile Playwright context, `390x844`, `isMobile=true`, `hasTouch=true`, `deviceScaleFactor=2`.

## Allowed Mutations

- Allowed: Open a temporary mobile context, zoom/pan the map, place planner waypoints, drag a waypoint, then close the mobile context.
- Not allowed: Leave mobile browser contexts open.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_11 | Opened mobile planner, zoomed to planning range, panned to Cornwall land/roads, placed two waypoints with touch taps, then dragged the lower waypoint using CDP `Input.dispatchTouchEvent` touch start/move/end. | Touch taps place waypoints and compute a route; touch dragging moves a waypoint and recomputes route stats. | Touch taps at `(230,145)` and `(260,220)` computed a one-leg route: `18.97 km`, `255 m`, `1h 8m`. Touch drag `(260,220)` to `(310,190)` recomputed the route to `22.30 km`, `372 m`, `1h 31m`; chart points changed `568→620`. Temporary mobile context was closed. | PASS | [assets/PLN_11-touch-results.txt](../assets/PLN_11-touch-results.txt); [assets/PLN_11-mobile-touch-route.webp](../assets/PLN_11-mobile-touch-route.webp); [assets/PLN_11-mobile-waypoint-dragged.webp](../assets/PLN_11-mobile-waypoint-dragged.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_11-touch-results.txt](../assets/PLN_11-touch-results.txt) | Mobile context, touch placement, and touch drag stats. |
| [assets/PLN_11-mobile-planning-range.jpg](../assets/PLN_11-mobile-planning-range.jpg) | Mobile planner zoomed enough to clear the planning prompt. |
| [assets/PLN_11-mobile-panned.jpg](../assets/PLN_11-mobile-panned.jpg) | Mobile map panned to land/roads before placement. |
| [assets/PLN_11-mobile-touch-route.webp](../assets/PLN_11-mobile-touch-route.webp) | Route after touch waypoint placement. |
| [assets/PLN_11-mobile-waypoint-dragged.webp](../assets/PLN_11-mobile-waypoint-dragged.webp) | Route after touch dragging the waypoint. |

## Screenshot Evidence

![Route after mobile touch placement](../assets/PLN_11-mobile-touch-route.webp)

![Route after mobile touch dragging waypoint](../assets/PLN_11-mobile-waypoint-dragged.webp)

## Timings

| Step | Timing |
|---|---:|
| Mobile zoom/pan to planning area | ~11 s |
| Touch waypoint placement and route compute | ~4 s |
| Touch waypoint drag and route recompute | ~3 s |

## Handoff Notes

- Completed: Mobile touch placement and touch waypoint drag were verified.
- Remaining unfinished coverage: MCT_01 onward.
- Blocked or not applicable: None.
- State left for the next packet: Temporary mobile context closed; desktop Playwright context remains on planner/load state from PLN_10.
