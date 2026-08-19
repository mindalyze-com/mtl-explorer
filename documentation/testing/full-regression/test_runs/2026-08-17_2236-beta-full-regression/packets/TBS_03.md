# Packet: TBS_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_03
- In scope: Bidirectional sorting for every sortable table column and summary aggregation for visible rows.
- Out of scope: Quick-view subset switching covered by TBS_04.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_02.
- Required app/data state: Statistics Tracks All view with 15 tracks and clear search.
- Required browser context: Desktop track table.

## Allowed Mutations

- Allowed: Activate sort headers and enter/clear a search query.
- Not allowed: Modify track data or filters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_03 | Clicked Start, Track, Activity, Distance, Duration, Avg speed, Energy, Exploration, and Imported headers twice; then sorted a two-row Walking result and read the summary. | Every sortable column works in both directions; summary reflects visible rows. | All nine fields reversed correctly. The Walking rows totaled exactly 2 of 15, 3.79 km, and 59m 57s before/after sorting. Clear restored 15 rows. | PASS | [assets/TBS_03-sort-summary.txt](../assets/TBS_03-sort-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_03-sort-summary.txt](../assets/TBS_03-sort-summary.txt) | Per-column sort endpoints, direction state, and visible-summary calculation. |

## Screenshot Evidence

Unavailable under ACC_04. Extracted column endpoints, aria-sort states, exact row values, and summary values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Nine-column bidirectional sort matrix | About 9 s |
| Visible-summary check and clear | About 2 s |

## Handoff Notes

- Completed: All sortable columns and visible-result summary behavior.
- Remaining unfinished coverage: None for TBS_03.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Tracks All view remains open; search is clear; Distance is the active sort.
