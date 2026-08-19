# Packet: PLN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_03
- In scope: Insert a waypoint by dragging an existing route leg.
- Out of scope: Adding a waypoint by an ordinary map click.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: One computed 2.93 km leg.
- Required browser context: Desktop Planner with a WebGL map.

## Allowed Mutations

- Allowed: Drag the route leg to insert a waypoint.
- Not allowed: Clear or save the route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_03 | Attempted two controlled drags at the expected canvas route-leg location and checked leg count/metrics after each. | Dragging the leg inserts a waypoint and recomputes two legs. | Both gestures left 1 leg and 2.93 km. The WebGL-only line exposes no DOM target, and screenshot/pixel targeting is unavailable, so a reliable hit cannot be made. | BLOCKED | [assets/PLN_03-route-drag.txt](../assets/PLN_03-route-drag.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_03-route-drag.txt](../assets/PLN_03-route-drag.txt) | Baseline, attempted targeting, invariant metrics, and blocker. |

## Screenshot Evidence

Unavailable under ACC_04; this is the direct blocker for reliable canvas-line targeting.

## Timings

| Step | Timing |
|---|---:|
| Two controlled drag attempts | About 4 s |

## Handoff Notes

- Completed: Direct leg-drag attempts and invariant-state checks.
- Remaining unfinished coverage: None for PLN_03; terminal BLOCKED.
- Blocked or not applicable: Successful canvas-line targeting.
- State left for the next packet: Original route remains 2.93 km with one leg.
