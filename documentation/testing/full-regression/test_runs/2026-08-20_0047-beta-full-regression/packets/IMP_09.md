# Packet: IMP_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_09
- In scope: Verify post-import totals, elevation direction, activity breakdown, period charts, rankings, heatmap density, and track-browser summary.
- Out of scope: Later FIT and multi-format imports.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 and IMP_08.
- Required app/data state: Five public GPX imports settled; current filter includes all five.
- Required browser context: Signed-in Statistics and Map settings views.

## Allowed Mutations

- Allowed: Switch Overview/Trends/Table/Charts views and inspect map data-layer settings.
- Not allowed: Change data, filter membership, or layer opacity.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_09 | Compare imported totals with the empty baseline; inspect breakdown, highlights, active periods, rendered charts, heatmap control, and track-browser summary. | All totals and derived displays move in the expected positive direction without blank or stale output. | Five tracks produce 1,043 km, 23h31m, 12,936 m ascent, positive descent details, populated Bicycle breakdown/rankings, two rendered period buckets, an enabled 100% heatmap layer, and a matching track-browser summary. | PASS | [assets/IMP_09-totals.txt](../assets/IMP_09-totals.txt); [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_09-totals.txt](../assets/IMP_09-totals.txt) | Exact overview, ranking, period, chart, heatmap, and browser-summary evidence. |
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Empty pre-import comparison. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; visible values and rendered component checks are recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| Overview, trends, chart, heatmap, and summary checks | 4 min |

## Handoff Notes

- Completed: Positive-direction totals and all required derived statistics/map-density checks.
- Remaining unfinished coverage: None for IMP_09.
- Blocked or not applicable: None.
- State left for the next packet: Map > Your data is open; all four layers remain shown at 100%.
