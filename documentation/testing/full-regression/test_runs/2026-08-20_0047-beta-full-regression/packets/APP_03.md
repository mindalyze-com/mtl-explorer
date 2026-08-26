# Packet: APP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_03
- In scope: Charts recolor on theme switch without reload.

## Prerequisites

- Required previous coverage IDs or run packets: APP_01.
- Required app/data state: Populated eight-track Statistics Overview.
- Required browser context: Statistics and Admin Preferences in one live session.

## Allowed Mutations

- Allowed: Switch Dark to Light and reopen Statistics; no reload.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_03 | Compared Highcharts SVG attributes in dark, switched to light without reload, and reopened the same overview. | Charts recolor without reload. | Nine charts remained rendered; point/grid contrast strokes changed from dark-theme to light-theme values while series colors stayed stable. | PASS | [assets/APP_03-chart-colors.txt](../assets/APP_03-chart-colors.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_03-chart-colors.txt](../assets/APP_03-chart-colors.txt) | Dark/light SVG counts and exact color attributes. |

## Screenshot Evidence

Direct SVG attribute evidence confirms recoloring. ACC_04 prevents saved screenshots.

## Timings

| Step | Timing |
|---|---:|
| Full no-reload switch/reopen comparison | About 17 s |

## Handoff Notes

- Completed: Dark/light chart recoloring without page reload.
- Remaining unfinished coverage: None for APP_03.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Statistics Overview open in Light theme.
