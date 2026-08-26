# Packet: FLT_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_14
- In scope: Category-selection rules for reselecting the same view and switching to a different view.
- Out of scope: Reload timing, covered by FLT_15.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_13.
- Required app/data state: Activities by exact type with WALKING-only selection available.
- Required browser context: Authenticated Filter view chooser.

## Allowed Mutations

- Allowed: Reselect exact activity type and switch to Tracks by year.
- Not allowed: Change criteria or files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_14 | Reselect the current view, then switch to a different view. | Same view retains selection; different view clears it. | Exact WALKING stayed 2/9 after reselecting the same view; switching to year returned 9/9 and All 4 categories. | PASS | [assets/FLT_14-view-switch-selection.txt](../assets/FLT_14-view-switch-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_14-view-switch-selection.txt](../assets/FLT_14-view-switch-selection.txt) | Same-view retained state and different-view reset state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible summaries and counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Same-view and different-view selection | 3 min |

## Handoff Notes

- Completed: Filter-view category selection rules.
- Remaining unfinished coverage: None for FLT_14.
- Blocked or not applicable: None.
- State left for the next packet: Tracks by year with All 4 categories and nine tracks.
