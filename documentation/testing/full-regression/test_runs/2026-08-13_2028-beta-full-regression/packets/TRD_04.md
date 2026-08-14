# Packet: TRD_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_04.
- In scope: elevation, speed, distance, and elevation-gain chart rendering and readability.
- Out of scope: chart-control mutations.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_03.
- Required app/data state: #100000 has populated metrics and chart series.
- Required browser context: Track Details Graphs tab.

## Allowed Mutations

- Allowed: inspect chart accessibility text and axes.
- Not allowed: edit source data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_04 | Opened Graphs and audited the accessible series descriptions, data ranges, units, and ticks for speed, elevation, gain rate, and distance. | Elevation, speed, distance, and gain charts render with readable values. | All four required chart groups rendered populated series with numeric ranges, time ticks, and matching km/h, m, m/h, or km units. | PASS | [chart values](../assets/TRD_04-chart-values.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_04-chart-values.txt](../assets/TRD_04-chart-values.txt) | Required chart names, series types, ranges, ticks, and units. |

## Screenshot Evidence

The chart accessibility descriptions provide more precise evidence than a scaled screenshot.

## Timings

| Step | Timing |
|---|---:|
| Chart render | < 1 s |
| Four-chart audit | < 1 min |

## Handoff Notes

- Completed: TRD_04.
- Remaining unfinished coverage: TRD_05 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Graphs open with Time axis, Range detail, 350 points, and default height.

