# Packet: TBS_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_09
- In scope: Daily, weekly, and monthly Trends chart rendering and switching.
- Out of scope: Clicking Statistics entries, covered by TBS_10.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_08.
- Required app/data state: Seven-track synchronized post-delete result.
- Required browser context: Statistics Trends in Charts mode.

## Allowed Mutations

- Allowed: Switch time grouping.
- Not allowed: Change filter or data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_09 | Switch month, week, and day groupings in Charts. | Each grouping renders populated charts. | All three selected correctly and rendered nine chart roots/ten series with expected period counts. | PASS | [assets/TBS_09-time-group-charts.txt](../assets/TBS_09-time-group-charts.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_09-time-group-charts.txt](../assets/TBS_09-time-group-charts.txt) | Group, periods, chart roots, and series counts. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; rendered chart DOM counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Month/week/day switch and render checks | 4 min |

## Handoff Notes

- Completed: Time-period chart switching.
- Remaining unfinished coverage: None for TBS_09.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Trends Charts grouped by day.
