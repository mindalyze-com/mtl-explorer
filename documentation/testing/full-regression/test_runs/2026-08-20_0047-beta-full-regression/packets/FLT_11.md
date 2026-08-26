# Packet: FLT_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_11
- In scope: Exact category selection while date criteria narrow and expand.
- Out of scope: Empty explicit selection, covered by FLT_12.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_10.
- Required app/data state: Activities by exact type with WALKING selected and BICYCLE excluded.
- Required browser context: Authenticated Filter criteria and category sheets.

## Allowed Mutations

- Allowed: Set and clear a 2021 date range.
- Not allowed: Change activity values or dataset files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_11 | Narrow criteria to 2021, then clear the range. | Exact selection survives; categories discovered on expansion remain unchecked. | WALKING stayed selected at one track, then returned to two; newly visible BICYCLE 7 remained unchecked. | PASS | [assets/FLT_11-selection-survives-criteria.txt](../assets/FLT_11-selection-survives-criteria.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_11-selection-survives-criteria.txt](../assets/FLT_11-selection-survives-criteria.txt) | Date values, category states, and result counts before and after expansion. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible checkbox states and counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Narrow, inspect, clear, and re-inspect | 4 min |

## Handoff Notes

- Completed: Exact category selection persistence across criteria changes.
- Remaining unfinished coverage: None for FLT_11.
- Blocked or not applicable: None.
- State left for the next packet: Exact WALKING selected, BICYCLE unchecked, no criteria; category sheet open.
