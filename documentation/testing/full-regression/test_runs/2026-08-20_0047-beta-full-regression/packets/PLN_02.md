# Packet: PLN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_02
- In scope: Add map waypoints and verify route computation and drawing.
- Out of scope: Route editing.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_01.
- Required app/data state: Planner open, BRouter ready, Road Bike active.
- Required browser context: Desktop map at a span below the Planner limit.

## Allowed Mutations

- Allowed: Zoom and add two temporary waypoints.
- Not allowed: Save the plan.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_02 | Zoom from the rejected wide span to 10 km scale and add two map waypoints. | A route is computed and drawn. | BRouter computed a one-leg 26.4 km route with live stats and a 144-point elevation profile. Canvas-only proof of the rendered route line cannot be captured under ACC_04. | BLOCKED | [assets/PLN_02-route-compute.txt](../assets/PLN_02-route-compute.txt) |

## Issues

None. The terminal block is the screenshot constraint recorded in ACC_04, not a product defect.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_02-route-compute.txt](../assets/PLN_02-route-compute.txt) | Waypoint, routing, stats, and profile evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the canvas-only line subcheck remains unverified.

## Timings

| Step | Timing |
|---|---:|
| Establish routable map span | 2 min |
| Add waypoints and await route | 1 min |

## Handoff Notes

- Completed: Waypoint entry and successful route computation.
- Remaining unfinished coverage: None; rendered-line proof is terminal BLOCKED by ACC_04.
- Blocked or not applicable: Route-line visual evidence only.
- State left for the next packet: Computed Road Bike route available for editing.
