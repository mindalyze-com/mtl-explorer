# Packet: TBS_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_06
- In scope: Statistics overview totals, breakdowns, rankings, milestones, and period-chart availability.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_05 terminal; current dataset has 11 tracks.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only statistics navigation, screenshot/text evidence, packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_06 | Opened Stats > Overview and captured summary tiles, activity breakdown, highlights/rankings, recent rows, active periods, milestones, and date range. | Statistics overview shows total distance, time, elevation-related sections, activity breakdown, rankings, milestones, and period chart entry points. | Overview showed 11 tracks, 966 km, 20h 46m, 4,525 Wh, Bicycle/Walking breakdown, highlight rankings, active periods, and milestones including first/latest and distance/ascent/energy milestones. | PASS | [assets/TBS_06-stats-overview.webp](../assets/TBS_06-stats-overview.webp); [assets/TBS_06-overview-totals-breakdown.txt](../assets/TBS_06-overview-totals-breakdown.txt); [assets/TBS_09-trends-charts-daily.webp](../assets/TBS_09-trends-charts-daily.webp); [assets/TBS_09-period-charts.txt](../assets/TBS_09-period-charts.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_06-stats-overview.webp](../assets/TBS_06-stats-overview.webp) | Screenshot evidence |
| [assets/TBS_06-overview-totals-breakdown.txt](../assets/TBS_06-overview-totals-breakdown.txt) | Text/log evidence |
| [assets/TBS_09-trends-charts-daily.webp](../assets/TBS_09-trends-charts-daily.webp) | Screenshot evidence |
| [assets/TBS_09-period-charts.txt](../assets/TBS_09-period-charts.txt) | Text/log evidence |

## Screenshot Evidence

![assets/TBS_06-stats-overview.webp](../assets/TBS_06-stats-overview.webp)
![assets/TBS_09-trends-charts-daily.webp](../assets/TBS_09-trends-charts-daily.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser automation and evidence capture | ~1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
