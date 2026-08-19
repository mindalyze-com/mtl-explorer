# Packet: APP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_03
- In scope: Highcharts recolor on application theme switch without reload.
- Out of scope: Chart data correctness, covered by statistics coverage.

## Prerequisites

- Required previous coverage IDs or run packets: APP_02.
- Required app/data state: Same 15-track Trends data.
- Required browser context: Statistics Trends in one SPA session.

## Allowed Mutations

- Allowed: Switch the local application theme and reopen the same sheet.
- Not allowed: Reload the browser or change grouping/data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_03 | Recorded light SVG/chart styles, selected Dark without reload, and recorded the same nine chart surfaces. | Charts recolor without reload. | Nine charts persisted; series edge changed white→dark and grid changed black→white at 0.06 alpha while data/axes remained intact. | PASS | [assets/APP_03-chart-colors.txt](../assets/APP_03-chart-colors.txt); [assets/APP_03-light-charts.jpg](../assets/APP_03-light-charts.jpg); [assets/APP_01-dark-charts.jpg](../assets/APP_01-dark-charts.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_03-chart-colors.txt](../assets/APP_03-chart-colors.txt) | Exact light/dark SVG colors and chart counts. |
| [assets/APP_03-light-charts.jpg](../assets/APP_03-light-charts.jpg) | Light Statistics Trends. |
| [assets/APP_01-dark-charts.jpg](../assets/APP_01-dark-charts.jpg) | Same Trends surface in dark mode. |

## Screenshot Evidence

- Paired chart captures preserve the same data/axes under light and dark themes.

## Timings

| Step | Timing |
|---|---:|
| Theme switch to chart repaint | About 2 s |

## Handoff Notes

- Completed: Charts recolor correctly without browser reload.
- Remaining unfinished coverage: None for APP_03.
- Blocked or not applicable: None.
- State left for the next packet: Dark Statistics Trends open; theme intentionally Dark.
