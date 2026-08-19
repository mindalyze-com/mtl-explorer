# Packet: TBS_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_09
- In scope: Daily, weekly, and monthly Trend chart grouping and rendering.
- Out of scope: Chart drill-down navigation covered by TBS_10.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_08.
- Required app/data state: Stable post-delete 13-track resolved set.
- Required browser context: Statistics → Trends → Charts.

## Allowed Mutations

- Allowed: Change trend aggregation grouping.
- Not allowed: Change track filters or data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_09 | Selected daily, weekly, and monthly grouping; inspected summaries, chart cards, Highcharts series/point counts, and x-axis labels. | Time-period charts render and switch correctly. | Daily/weekly/monthly produced 8/5/4 periods and matching labels/points across all eight chart cards. Track/distance/time/energy totals remained the same 13-track values. | PASS | [assets/TBS_09-period-charts.txt](../assets/TBS_09-period-charts.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_09-period-charts.txt](../assets/TBS_09-period-charts.txt) | Per-grouping summaries, labels, chart inventory, and series/point counts. |

## Screenshot Evidence

Unavailable under ACC_04. Highcharts DOM structure, exact axes, series, point counts, card titles, and totals provide direct rendered-chart evidence.

## Timings

| Step | Timing |
|---|---:|
| Three grouping switches and chart inspection | About 6 s |

## Handoff Notes

- Completed: Daily, weekly, and monthly Trend chart rendering/switching.
- Remaining unfinished coverage: None for TBS_09.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Trends Charts remains open in monthly grouping on the 13-track set.
