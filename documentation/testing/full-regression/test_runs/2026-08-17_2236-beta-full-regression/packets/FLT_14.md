# Packet: FLT_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_14
- In scope: Switching filter views clears prior result-category selection; choosing the current view again preserves its selection.
- Out of scope: Reload persistence covered by FLT_15.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_13.
- Required app/data state: Exact activity view with HIKING+WALKING selected.
- Required browser context: Filter view and Included categories selectors.

## Allowed Mutations

- Allowed: Switch to Tracks by year and set 2013+2021.
- Not allowed: Reset the year exact selection before FLT_15.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_14 | Switched exact activity→year, set 2013+2021, then selected Tracks by year again. | Different-filter switch clears selection; selecting current filter again retains its selection. | Year initialized All 4 / 15 tracks, proving the activity selection cleared. Re-selecting current year view preserved 2013+2021 / 2 tracks / two-item legend. | PASS | [assets/FLT_14-view-switch.txt](../assets/FLT_14-view-switch.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_14-view-switch.txt](../assets/FLT_14-view-switch.txt) | Starting, different-view, exact year, and same-view reapply states. |

## Screenshot Evidence

Unavailable under ACC_04. Exact view labels, summaries, counts, and legends provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Switch and inspect cleared selection | About 2 s |
| Set exact year selection | About 2 s |
| Re-select current view | About 1 s |

## Handoff Notes

- Completed: Different-view clear and same-view preservation.
- Remaining unfinished coverage: None for FLT_14.
- Blocked or not applicable: None.
- State left for the next packet: Tracks by year with exact 2013+2021 selection, 2 / 15 Tracks; controlled track 100017 remains temporarily Hiking.
