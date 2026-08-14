# Packet: PLN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_02.
- In scope: adding map waypoints and computing/drawing a route.
- Out of scope: route-leg insertion, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_01.
- Required app/data state: Road Bike selected; no waypoints.
- Required browser context: map centered and zoomed on Zürich.

## Allowed Mutations

- Allowed: add four waypoints.
- Not allowed: save the plan yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_02 | Centered on Zürich and clicked four map points. | Waypoints appear and a route is computed and drawn. | Road Bike computed a three-leg 4.86 km route with populated elevation and live statistics. | PASS | [route](../assets/PLN_02-route.txt), [planner](../assets/PLN_02-route.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_02-route.txt](../assets/PLN_02-route.txt) | Waypoint count, route metrics, and chart bounds. |
| [assets/PLN_02-route.webp](../assets/PLN_02-route.webp) | Computed route and populated Planner. |

## Screenshot Evidence

The compact WebP shows the working route, stats, and elevation profile.

## Timings

| Step | Timing |
|---|---:|
| Route compute | < 2 s after final waypoint |

## Handoff Notes

- Completed: PLN_02 is terminal `PASS`.
- Remaining unfinished coverage: PLN_03 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Road Bike route with four waypoints, three legs, 4.86 km.
