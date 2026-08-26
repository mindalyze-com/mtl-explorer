# Packet: TRD_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_04
- In scope: Elevation, speed, distance, and elevation-gain charts.
- Out of scope: Chart-control mutations and cross-surface hover synchronization.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01-TRD_03.
- Required app/data state: FIT track 100005 with time, elevation, and speed data.
- Required browser context: Authenticated Graphs tab.

## Allowed Mutations

- Allowed: Inspect rendered chart structure, series, axis ranges, and units.
- Not allowed: Save track changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_04 | Open Graphs for FIT track 100005 and inspect the required chart series and axes. | Elevation, speed, distance, and gain charts render with readable values. | All four required charts rendered as interactive Highcharts regions with 345-350 data points, numeric ranges, tick labels, and the expected km/h, m, m/h, and km units. | PASS | [assets/TRD_04-required-charts.txt](../assets/TRD_04-required-charts.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_04-required-charts.txt](../assets/TRD_04-required-charts.txt) | Required charts, series sizes, and units. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible chart descriptions are linked above.

## Timings

| Step | Timing |
|---|---:|
| Required chart inspection | 1 min |

## Handoff Notes

- Completed: Four required charts rendered with readable values.
- Remaining unfinished coverage: None for TRD_04.
- Blocked or not applicable: None.
- State left for the next packet: Graphs selected on FIT track 100005.
