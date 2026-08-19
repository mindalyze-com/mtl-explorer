# Packet: TRD_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_04
- In scope: Elevation, speed, distance, and gain chart rendering and readable values.
- Out of scope: Control changes covered by TRD_05.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_03.
- Required app/data state: Track 100004 with populated telemetry.
- Required browser context: Graphs tab selected.

## Allowed Mutations

- Allowed: Read chart accessibility trees and rendered SVG/container state.
- Not allowed: Change graph settings yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_04 | Inspected graph labels, Highcharts container/SVG counts, series counts, accessible data ranges, axes, labels, and units. | Elevation, speed, distance, and gain charts render with readable values. | Six charts rendered. Required charts exposed populated data ranges, time labels, and km/h, m, m/h, and km units; no chart was blank. | PASS | [assets/TRD_04-chart-values.txt](../assets/TRD_04-chart-values.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_04-chart-values.txt](../assets/TRD_04-chart-values.txt) | Required chart labels, series, ranges, axes, and units. |

## Screenshot Evidence

Unavailable under ACC_04; Highcharts accessibility output and rendered SVG/container state supplied readable chart evidence.

## Timings

| Step | Timing |
|---|---:|
| Inspect six charts and labels | Under 1 s |

## Handoff Notes

- Completed: Required graph rendering and readable-value validation.
- Remaining unfinished coverage: None for TRD_04.
- Blocked or not applicable: None.
- State left for the next packet: Graphs tab selected at default Time/Range/350-point settings.
