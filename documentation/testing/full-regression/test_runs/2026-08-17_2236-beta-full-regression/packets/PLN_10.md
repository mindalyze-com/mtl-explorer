# Packet: PLN_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_10
- In scope: Preserve an existing planned route during new-routing trouble.
- Out of scope: Mobile touch editing.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_09.
- Required app/data state: Existing 710 m plan plus an unroutable Honolulu waypoint.
- Required browser context: Planner unavailable notice.

## Allowed Mutations

- Allowed: Change routing profile to retry.
- Not allowed: Clear the original route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_10 | Changed Hiking to Car while the added waypoint was unroutable and checked both pending and settled states. | Existing planned route remains displayed even when new routing fails. | The original 710 m/one-leg values and five-point elevation profile remained during updating and after the unavailable notice returned. | PASS | [assets/PLN_10-preserve-route.txt](../assets/PLN_10-preserve-route.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_10-preserve-route.txt](../assets/PLN_10-preserve-route.txt) | Pending/unavailable comparisons of existing route state. |

## Screenshot Evidence

Unavailable under ACC_04. Exact persisted metrics/profile and notices provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Profile retry and pending check | Under 1 s |
| Settled preservation check | About 2 s |

## Handoff Notes

- Completed: Existing-route preservation during a failed retry.
- Remaining unfinished coverage: None for PLN_10.
- Blocked or not applicable: None.
- State left for the next packet: Car selected; unavailable notice active; original route plus Honolulu waypoint retained.
