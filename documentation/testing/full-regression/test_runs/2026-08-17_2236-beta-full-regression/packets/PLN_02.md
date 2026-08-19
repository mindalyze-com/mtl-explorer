# Packet: PLN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_02
- In scope: Add waypoints and compute/draw a route.
- Out of scope: Drag insertion or waypoint editing.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_01.
- Required app/data state: BRouter ready; Road Bike selected; zero waypoints.
- Required browser context: Desktop Planner and map.

## Allowed Mutations

- Allowed: Zoom and add two waypoints.
- Not allowed: Save the route yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_02 | Zoomed to a local span and clicked two map points. | A route is computed and drawn. | One 2.93 km Road Bike leg computed with ascent/descent/time statistics and a four-point elevation profile; route actions enabled. | PASS | [assets/PLN_02-route.txt](../assets/PLN_02-route.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_02-route.txt](../assets/PLN_02-route.txt) | Zoom, waypoint transition, route metrics, and elevation corroboration. |

## Screenshot Evidence

Unavailable under ACC_04. Computed metrics, enabled actions, and chart data provide direct evidence for the canvas route.

## Timings

| Step | Timing |
|---|---:|
| Zoom and two clicks | About 3 s |
| Routing response | About 2 s |

## Handoff Notes

- Completed: Two-waypoint route computation.
- Remaining unfinished coverage: None for PLN_02.
- Blocked or not applicable: None.
- State left for the next packet: One 2.93 km Road Bike leg with two waypoints.
