# Packet: PLN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_01.
- In scope: opening Planner and selecting a routing profile.
- Out of scope: adding waypoints, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_13.
- Required app/data state: normal twelve-track map.
- Required browser context: desktop Planner.

## Allowed Mutations

- Allowed: select Road Bike.
- Not allowed: add waypoints yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_01 | Opened Planner, inspected its profile menu, and selected Road Bike. | Planner opens and the chosen routing profile becomes active. | Drawing tab opened; four profiles were offered; toolbar updated to Road Bike. | PASS | [profile](../assets/PLN_01-profile.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_01-profile.txt](../assets/PLN_01-profile.txt) | Available profiles and selected state. |

## Screenshot Evidence

Exact toolbar and menu states provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Planner open | < 1 s |
| Profile selection | < 1 s |

## Handoff Notes

- Completed: PLN_01 is terminal `PASS`.
- Remaining unfinished coverage: PLN_02 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop Planner Drawing tab open; Road Bike selected; no waypoints.
