# Packet: PLN_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_09
- In scope: Clear pending/unavailable UI when routing data or a route is unavailable.
- Out of scope: Recovery of a previously drawn route, covered by PLN_10.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_08.
- Required app/data state: BRouter running with limited downloaded segments; loaded route present.
- Required browser context: Planner recentered to Honolulu.

## Allowed Mutations

- Allowed: Add an out-of-area waypoint and allow re-route to fail.
- Not allowed: Stop BRouter or corrupt its data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_09 | Recentered to a fresh segment and added a waypoint that could not be routed from the loaded plan. | UI shows segment updating/downloading or a clear unavailable state, not an unhandled error. | Status changed to "updating route", then settled with an explicit route-unavailable notice and guidance; planner controls stayed usable. | PASS | [assets/PLN_09-unavailable-state.txt](../assets/PLN_09-unavailable-state.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_09-unavailable-state.txt](../assets/PLN_09-unavailable-state.txt) | Immediate and settled routing states. |

## Screenshot Evidence

Unavailable under ACC_04. Exact status/notice text provides direct UI evidence.

## Timings

| Step | Timing |
|---|---:|
| Recenter and trigger re-route | About 4 s |
| Pending to unavailable | About 2 s |

## Handoff Notes

- Completed: Pending and unavailable routing-state handling.
- Remaining unfinished coverage: None for PLN_09.
- Blocked or not applicable: None.
- State left for the next packet: Unavailable notice visible; original 710 m route still rendered; extra Honolulu waypoint retained.
