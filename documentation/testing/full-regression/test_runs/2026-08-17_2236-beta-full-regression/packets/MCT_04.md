# Packet: MCT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_04
- In scope: Compare several segment tracks and verify chart/map alignment with missing data.
- Out of scope: Extraction API precision and virtual race playback.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_03.
- Required app/data state: Six-track A-B Segment Analyzer result retained from MCT_01.
- Required browser context: Desktop Segment Analyzer result table.

## Allowed Mutations

- Allowed: Restore retained results, select five tracks, and open Compare.
- Not allowed: Modify stored tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_04 | Selected five crossing tracks, including zero-duration/missing-value format fixtures, and opened Compare. | Comparison chart and map align selected tracks correctly even with missing data. | Compare rendered a local 100 m map, five named Speed and Altitude chart lines, aligned 183-198 m segment cards, and explicit `No value` points without errors. | PASS | [assets/MCT_04-comparison.txt](../assets/MCT_04-comparison.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_04-comparison.txt](../assets/MCT_04-comparison.txt) | Selected tracks, map scale, chart series, missing values, and segment-card metrics. |

## Screenshot Evidence

Unavailable under ACC_04. Accessible map/chart structure and exact card metrics provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Restore retained results | Under 1 s |
| Open and inspect Compare | About 2 s |

## Handoff Notes

- Completed: Multi-track comparison, map, charts, and missing-data handling.
- Remaining unfinished coverage: None for MCT_04.
- Blocked or not applicable: None.
- State left for the next packet: A-B Compare open for five tracks.

