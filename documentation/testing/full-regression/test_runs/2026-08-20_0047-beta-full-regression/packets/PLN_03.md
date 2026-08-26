# Packet: PLN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_03
- In scope: Insert a waypoint by dragging an existing route leg.
- Out of scope: General edit-history controls.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: Computed route with one leg.
- Required browser context: Desktop Planner with exposed map strip.

## Allowed Mutations

- Allowed: Clear/recreate a short temporary route and drag its route leg.
- Not allowed: Save the plan.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_03 | Create a short one-leg route and drag its midpoint away from the leg. | A new intermediate waypoint is inserted and the route recomputes as two legs. | Route changed from 5.13 km/one leg to 9.43 km/two legs, with updated ascent, descent, duration, and chart. | PASS | [assets/PLN_03-insert-waypoint.txt](../assets/PLN_03-insert-waypoint.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_03-insert-waypoint.txt](../assets/PLN_03-insert-waypoint.txt) | Before/after leg count and route-stat evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; state change is captured in text evidence.

## Timings

| Step | Timing |
|---|---:|
| Recreate short route | 1 min |
| Drag leg and verify insertion | 1 min |

## Handoff Notes

- Completed: Route-drag waypoint insertion.
- Remaining unfinished coverage: None for PLN_03.
- Blocked or not applicable: None.
- State left for the next packet: Three-waypoint, two-leg route active.
