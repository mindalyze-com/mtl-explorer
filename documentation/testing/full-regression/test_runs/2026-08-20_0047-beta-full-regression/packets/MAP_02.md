# Packet: MAP_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_02
- In scope: All tracks in the active result appear with the correct total/visible count.
- Out of scope: Duplicate-file index entries excluded by the active Smart Base Filter.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: Smart Base Filter active; no criteria; all activity categories selected.
- Required browser context: Loaded map and Filter panel.

## Allowed Mutations

- Allowed: Inspect filter categories and open Review Tracks; briefly pause and restore the filter to check its state.
- Not allowed: Change server data or saved filter definition.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Compare the map count with active-filter summary, category totals, and Review Tracks rows. | All tracks in the current result appear; total and visible counts agree. | Map and filter showed 9; categories summed to 7 bicycle + 2 walking; Review Tracks showed one page with 9 rows and the expected nine names. | PASS | [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) | Active filter, category arithmetic, map count, and reviewed track names. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible counts and row data are linked above.

## Timings

| Step | Timing |
|---|---:|
| Filter/category/review comparison | <3 min |

## Handoff Notes

- Completed: Active-result map count and all nine reviewed tracks.
- Remaining unfinished coverage: None for MAP_02.
- Blocked or not applicable: None.
- State left for the next packet: Smart Base Filter restored; Matching Tracks panel open.
