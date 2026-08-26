# Packet: PLN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_01
- In scope: Open Planner and select a routing profile.
- Out of scope: Route creation and editing.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_16.
- Required app/data state: Authenticated map; temporary geo filter reset.
- Required browser context: Desktop Planner panel.

## Allowed Mutations

- Allowed: Reset the temporary filter and change the routing profile.
- Not allowed: Create or save a route in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_01 | Open Planner, inspect routing status, open the profile selector, and choose Road Bike. | Planner opens and the chosen routing profile becomes active. | Planner opened with BRouter ready and Hiking active; Road Bike was selectable and became the active profile. | PASS | [assets/PLN_01-profile.txt](../assets/PLN_01-profile.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_01-profile.txt](../assets/PLN_01-profile.txt) | Accessible Planner/profile state before and after selection. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible control state is linked above.

## Timings

| Step | Timing |
|---|---:|
| Reset filter and open Planner | 1 min |
| Select profile | 1 min |

## Handoff Notes

- Completed: Planner open and profile selection.
- Remaining unfinished coverage: None for PLN_01.
- Blocked or not applicable: None.
- State left for the next packet: Planner maximized, Road Bike active, no route points.
