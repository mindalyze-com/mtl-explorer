# Packet: FLT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_04
- In scope: Date, text, and geo parameter persistence through reload.
- Out of scope: Full geo drawing interaction matrix.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03.
- Required app/data state: Activities by keyword active.
- Required browser context: Authenticated Filter Criteria.

## Allowed Mutations

- Allowed: Set one date, keyword, and circle; reload.
- Not allowed: Claim persistence without reopening exact values.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_04 | Set From date, Keyword, and a circle area, reload Filter, then inspect all values. | Date, text, and geo parameters save and re-apply after reload. | Reload retained 3 active criteria, exact date/keyword, and the exact saved circle center/radius. | PASS | [assets/FLT_04-parameter-persistence.txt](../assets/FLT_04-parameter-persistence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_04-parameter-persistence.txt](../assets/FLT_04-parameter-persistence.txt) | Exact parameters before and after reload. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible parameter values are linked above.

## Timings

| Step | Timing |
|---|---:|
| Set three parameter types and reload | 5 min |

## Handoff Notes

- Completed: Date, text, and geo persistence.
- Remaining unfinished coverage: None for FLT_04.
- Blocked or not applicable: None.
- State left for the next packet: Date, keyword, and one circle area active; area editor expanded.
