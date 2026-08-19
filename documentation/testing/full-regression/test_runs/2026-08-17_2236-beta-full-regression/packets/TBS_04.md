# Packet: TBS_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_04
- In scope: Track-browser quick-view subsets and search/sort continuity while switching.
- Out of scope: Creating new excluded/no-activity records.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_03.
- Required app/data state: Restored 15-track set with no active statistics exclusion and all activities assigned.
- Required browser context: Statistics Tracks desktop view.

## Allowed Mutations

- Allowed: Switch quick views, search, sort, and clear search.
- Not allowed: Change track curation or activity fields.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_04 | Switched All/Excluded/Stats excluded/No activity; carried a Path query and descending Name sort through every subset and back. | Presets select the correct subset and retain usable sorting/search. | All showed 15 tracks. The three correctly empty subsets showed 0 totals. Query and Name sort persisted through every switch; returning to All restored two Path rows and clearing restored 15. | PASS | [assets/TBS_04-quick-views.txt](../assets/TBS_04-quick-views.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_04-quick-views.txt](../assets/TBS_04-quick-views.txt) | Per-preset totals, pressed state, empty state, and query/sort continuity. |

## Screenshot Evidence

Unavailable under ACC_04. Pressed states, exact subset totals, rows, query value, sort direction, and empty messages provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Subset matrix | About 3 s |
| Search/sort continuity cycle | About 6 s |

## Handoff Notes

- Completed: Correct preset subsets plus preserved search/sort usability.
- Remaining unfinished coverage: None for TBS_04.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Tracks All view is active; search is clear; all 15 tracks are visible.
