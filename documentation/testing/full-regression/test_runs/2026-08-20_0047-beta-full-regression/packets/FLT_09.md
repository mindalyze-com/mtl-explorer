# Packet: FLT_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_09
- In scope: Exact two-category selection in a grouped year result and consistency across all named result surfaces.
- Out of scope: Activity category behavior, covered by FLT_10.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_08.
- Required app/data state: Nine-track Smart Base baseline.
- Required browser context: Authenticated Filter, map, and Statistics views.

## Allowed Mutations

- Allowed: Activate Tracks by year and select exactly 2010 and 2026.
- Not allowed: Add or remove dataset files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_09 | Select the 2010 and 2026 result categories, then inspect map, count, Review, heatmap/data layers, Overview, Trends, and Stats Tracks. | Every surface uses the same selected tracks. | All named surfaces used the same seven-track subset; counts, rows, distance, duration, and energy agreed. | PASS | [assets/FLT_09-year-subset.txt](../assets/FLT_09-year-subset.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_09-year-subset.txt](../assets/FLT_09-year-subset.txt) | Exact category selection, cross-view totals, and seven track rows. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible counts, labels, and rows are linked above.

## Timings

| Step | Timing |
|---|---:|
| Select categories and inspect all result surfaces | 6 min |

## Handoff Notes

- Completed: Grouped year selection and cross-view consistency.
- Remaining unfinished coverage: None for FLT_09.
- Blocked or not applicable: Screenshot evidence only (ACC_04).
- State left for the next packet: Tracks by year with exactly 2010 and 2026 selected; seven-track result.
