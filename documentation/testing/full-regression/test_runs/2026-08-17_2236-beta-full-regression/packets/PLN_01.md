# Packet: PLN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_01
- In scope: Open Planner and choose a routing profile.
- Out of scope: Waypoint and route computation.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_16.
- Required app/data state: Healthy map and BRouter status.
- Required browser context: Desktop map.

## Allowed Mutations

- Allowed: Open Planner and change profile.
- Not allowed: Save or create a route yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_01 | Opened Planner, expanded the profile selector, and chose Road Bike. | Planner opens and accepts a routing profile. | Planner rendered with BRouter ready; Road Bike replaced the default Hiking profile. | PASS | [assets/PLN_01-profile.txt](../assets/PLN_01-profile.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_01-profile.txt](../assets/PLN_01-profile.txt) | Planner controls, status, choices, and selected profile. |

## Screenshot Evidence

Unavailable under ACC_04. Exact control names, options, and selected state provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Open and choose profile | About 2 s |

## Handoff Notes

- Completed: Planner open and Road Bike selected.
- Remaining unfinished coverage: None for PLN_01.
- Blocked or not applicable: None.
- State left for the next packet: Planner Drawing open; Road Bike selected; zero waypoints.
