# Packet: FLT_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_15
- In scope: Exact result-category selection restores before first resolution and remains consistent across filter-aware views after reload.
- Out of scope: Heatmap activation mechanics already covered for the same selection in FLT_09.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_14 and FLT_09.
- Required app/data state: Tracks by year with exact 2013+2021 selection.
- Required browser context: Filter reload, Review tracks, and all Statistics tabs.

## Allowed Mutations

- Allowed: Reload the route and restore controlled track 100017 Hiking→Running after assertions.
- Not allowed: Reset the year selection before FLT_16.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_15 | Reloaded Filter, inspected immediate and resolved DOM, then rechecked Review/Overview/Trends/Stats Tracks and restored controlled activity data. | Exact selection restores before first resolution and all filter-aware views still match. | Initial state was loading with no wrong aggregate; first resolved state was 2013+2021 / 2-of-4 / 2 tracks. All four rechecked views remained 2 tracks / 29.5 km with the same rows. | PASS | [assets/FLT_15-reload.txt](../assets/FLT_15-reload.txt), [assets/FLT_09-year-selection.txt](../assets/FLT_09-year-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_15-reload.txt](../assets/FLT_15-reload.txt) | Immediate/resolved reload states, post-reload view totals, and controlled activity restoration. |
| [assets/FLT_09-year-selection.txt](../assets/FLT_09-year-selection.txt) | Same exact selection's heatmap and complete cross-view identity evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Exact loading/resolved states, selections, counts, totals, rows, and restored activity values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Reload and first resolution | About 1 s |
| Review and Statistics rechecks | About 7 s |
| Restore Running | About 3 s |

## Handoff Notes

- Completed: Exact selection reload restoration and post-reload filter-aware consistency.
- Remaining unfinished coverage: None for FLT_15.
- Blocked or not applicable: None.
- State left for the next packet: Track 100017 Overview open and restored to Running; saved year selection 2013+2021 remains persisted.
