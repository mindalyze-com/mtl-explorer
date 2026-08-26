# Packet: FLT_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_15
- In scope: Reload ordering for an exact category selection and cross-view agreement after restore.
- Out of scope: Temporary legend visibility, covered by FLT_16.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_14.
- Required app/data state: Tracks by year with exact 2010+2026 selection.
- Required browser context: Authenticated Filter, Review, and Statistics views.

## Allowed Mutations

- Allowed: Reload and navigate between result views.
- Not allowed: Change the saved category selection during observation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_15 | Reload with exact 2010+2026 selection, observe first resolved state, then inspect Review and Overview. | Exact selection restores before first resolution; views match. | Loading resolved directly to 7/9 with 2 of 4 categories; Review and Overview matched the same seven tracks and totals. | PASS | [assets/FLT_15-reload-order.txt](../assets/FLT_15-reload-order.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_15-reload-order.txt](../assets/FLT_15-reload-order.txt) | Pre-reload state, first resolved state, and cross-view results. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible resolved states and counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Reload observation and cross-view check | 3 min |

## Handoff Notes

- Completed: Exact category restore ordering and view agreement.
- Remaining unfinished coverage: None for FLT_15.
- Blocked or not applicable: None.
- State left for the next packet: Stats Overview with the restored 2010+2026 seven-track selection.
