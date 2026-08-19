# Packet: PLN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_06
- In scope: Elevation profile rendering and matching map-point hover highlight.
- Out of scope: Route saving.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_05.
- Required app/data state: Computed 710 m route with elevation samples.
- Required browser context: Planner Drawing and map.

## Allowed Mutations

- Allowed: Hover and leave the elevation graph.
- Not allowed: Change route geometry.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_06 | Inspected the five-point profile, hovered its tracker line, checked tooltip/map marker, then moved away. | Elevation renders and hover highlights the corresponding map point. | Tooltip showed 0.06 km/643 m/+0.1%; a planner-hover map marker appeared during hover and was removed on leave. | PASS | [assets/PLN_06-elevation-hover.txt](../assets/PLN_06-elevation-hover.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_06-elevation-hover.txt](../assets/PLN_06-elevation-hover.txt) | Profile points, tooltip, marker lifecycle. |

## Screenshot Evidence

Unavailable under ACC_04. Profile accessibility data, tooltip text, and exact MapLibre marker lifecycle provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Profile inspect and hover/leave | About 2 s |

## Handoff Notes

- Completed: Elevation render and linked map highlight.
- Remaining unfinished coverage: None for PLN_06.
- Blocked or not applicable: None.
- State left for the next packet: Hiking selected; 710 m route remains ready to save.
