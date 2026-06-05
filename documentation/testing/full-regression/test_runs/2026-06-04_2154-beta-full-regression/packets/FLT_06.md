# Packet: FLT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_06
- In scope: Applied filter updates to visible count, map colors, legend, and stats without full reload.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows through TRD_14 terminal; current dataset has 11 tracks.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: UI filter interactions, local browser storage changes for filter settings, screenshot/text evidence, packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_06 | Selected Tracks by distance (gradient), set distance range 0-5 km, verified action bar and map legend, opened Stats without reloading, and captured navigation entry count. | The applied filter updates visible count, map colors, legend, and statistics without a full page reload. | The filter updated to 8 / 11 tracks with gradient legend bands; Stats showed 8 tracks; browser navigation entry count stayed 1 before and after the interaction. | PASS | [assets/FLT_06-distance-filter-map-legend.webp](../assets/FLT_06-distance-filter-map-legend.webp); [assets/FLT_06-distance-filter-stats.webp](../assets/FLT_06-distance-filter-stats.webp); [assets/FLT_06-live-filter-map-legend-stats.txt](../assets/FLT_06-live-filter-map-legend-stats.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_06-distance-filter-map-legend.webp](../assets/FLT_06-distance-filter-map-legend.webp) | Screenshot evidence |
| [assets/FLT_06-distance-filter-stats.webp](../assets/FLT_06-distance-filter-stats.webp) | Screenshot evidence |
| [assets/FLT_06-live-filter-map-legend-stats.txt](../assets/FLT_06-live-filter-map-legend-stats.txt) | Text/log evidence |

## Screenshot Evidence

![assets/FLT_06-distance-filter-map-legend.webp](../assets/FLT_06-distance-filter-map-legend.webp)
![assets/FLT_06-distance-filter-stats.webp](../assets/FLT_06-distance-filter-stats.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser automation and evidence capture | ~5 minutes |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
