# Packet: FLT_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_08
- In scope: Clearing/resetting the active filter restores all tracks and baseline configuration.
- Out of scope: Undoing the reset.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_07.
- Required app/data state: Two-track keyword filter, two-category legend, and 5 Colors palette.
- Required browser context: Filter, map, and Statistics Overview.

## Allowed Mutations

- Allowed: Reset filter to the default Smart Base Filter.
- Not allowed: Leave an active restrictive filter.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_08 | Selected Reset filter, inspected configuration, closed to map, and checked Statistics. | Clearing restores all tracks. | Smart Base/No criteria/No coloring returned, result became 15, map toolbar became 15 Tracks with no category legend, and Statistics became 15 tracks / 1,048 km. | PASS | [assets/FLT_08-clear-filter.txt](../assets/FLT_08-clear-filter.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_08-clear-filter.txt](../assets/FLT_08-clear-filter.txt) | Before/reset/map/Statistics baseline restoration evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Exact configuration, counts, legend absence, and Statistics totals provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Reset and configuration check | Under 1 s |
| Map and Statistics verification | About 5 s |

## Handoff Notes

- Completed: Active filter clear and full baseline restoration.
- Remaining unfinished coverage: None for FLT_08.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview open; Smart Base Filter baseline with all fifteen tracks.
