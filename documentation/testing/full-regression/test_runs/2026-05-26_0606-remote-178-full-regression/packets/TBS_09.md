# Packet: TBS_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_09
- In scope: statistics Trends period charts for daily, weekly, and monthly periods.
- Out of scope: detailed metric validation for every chart series.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_06.
- Required app/data state: filtering off; 10 tracks visible.
- Required browser context: signed-in desktop browser with Stats open.

## Allowed Mutations

- Allowed: switch Trends period selector and chart/table view controls.
- Not allowed: change data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_09 | Opened Stats > Trends, kept Charts mode, and switched the period selector to monthly, weekly, and daily modes. | Time-period charts render and switch correctly for daily, weekly, and monthly period groupings. | Each selected mode updated the active combobox label, kept the 10-track/1,262 km/1d 03h totals, and rendered duration/distance bar charts without blank panels or layout errors. | PASS | `assets/TBS_09-monthly.webp`, `assets/TBS_09-weekly.webp`, `assets/TBS_09-daily.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_09-monthly.webp | Monthly trend charts. |
| assets/TBS_09-weekly.webp | Weekly trend charts. |
| assets/TBS_09-daily.webp | Daily trend charts. |

## Timings

| Step | Timing |
|---|---:|
| Trends chart switching | <4m |

## Handoff Notes

- Completed: TBS_09 terminal PASS.
- Remaining unfinished coverage: continue with TBS_10.
- Blocked or not applicable: none.
- State left for the next packet: Stats Trends tab open in daily chart mode.
