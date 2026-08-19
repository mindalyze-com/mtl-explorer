# Packet: FLT_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_09
- In scope: Exact two-category year selection across map/count, track browser, heatmap, Statistics Overview, Trends, and Stats Tracks.
- Out of scope: Main/exact activity selections covered by FLT_10.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_08 and FLT_06.
- Required app/data state: Fifteen tracks spanning 2010, 2013, 2021, and 2026.
- Required browser context: Filter, map, Review tracks, Map data layers, and all Statistics tabs.

## Allowed Mutations

- Allowed: Select 2013/2021 and temporarily enable heatmap.
- Not allowed: Leave heatmap enabled.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_09 | Selected year categories 2013 and 2021, then compared map/count, Filter Review, heatmap, Statistics Overview, Trends, and Stats Tracks. | Every filter-aware view uses the same selected tracks. | Every view reported exactly two tracks and 29.5 km; both browser tables contained only track 100005 and the 2013 Lannion track. Trends also reported two periods/tracks and 2h13m. | PASS | [assets/FLT_09-year-selection.txt](../assets/FLT_09-year-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_09-year-selection.txt](../assets/FLT_09-year-selection.txt) | Exact category selection and per-view counts, totals, rows, and heatmap state. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered selections, counts, totals, row identities, legend labels, and heatmap state provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Select and apply categories | About 2 s |
| Map/browser/heatmap checks | About 6 s |
| Three Statistics views | About 7 s |

## Handoff Notes

- Completed: Exact grouped-year selection across every requested filter-aware view.
- Remaining unfinished coverage: None for FLT_09.
- Blocked or not applicable: None.
- State left for the next packet: Map sheet open; heatmap off; Tracks by year exact selection 2013/2021 remains active.
