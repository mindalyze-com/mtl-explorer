# Packet: FLT_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_09.
- In scope: grouped year filter, exact two-category selection, and consistency across every named filter-aware view.
- Out of scope: main activity and exact activity category types.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_08.
- Required app/data state: 12 tracks across four years.
- Required browser context: Filter, map/legend/heatmap, Review tracks, and all Statistics tabs.

## Allowed Mutations

- Allowed: select years 2010/2013 and temporarily enable Heatmap.
- Not allowed: leave Heatmap enabled.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_09 | Applied Tracks by year with exact categories 2010 and 2013; audited map/count/legend, Review tracks, Heatmap, Stats Overview, Trends, and Stats Tracks. | Every view uses the same exact selected tracks. | All surfaces agreed on eight tracks. Browser and stats totals were 919 km/20h 43m, Trends showed two expected periods and eight tracks, and Heatmap enabled over the same 8/12 map result. | PASS | [year selection](../assets/FLT_09-year-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_09-year-selection.txt](../assets/FLT_09-year-selection.txt) | Exact categories and per-view counts/totals/periods/heatmap state. |

## Screenshot Evidence

Exact per-surface values provide direct consistency evidence.

## Timings

| Step | Timing |
|---|---:|
| Category apply | < 1 s |
| Each filtered view | < 1 s after open |
| Heatmap enable | < 1 s |

## Handoff Notes

- Completed: FLT_09.
- Remaining unfinished coverage: FLT_10 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Tracks by year with exact 2010/2013 selection; Heatmap restored off; main map visible.

