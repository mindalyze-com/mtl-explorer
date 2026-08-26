# Packet: MCT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_04
- In scope: Compare several measured tracks in chart and map, including missing values.
- Out of scope: Exact extracted-slice and global-line sanity, covered by MCT_05/MCT_06.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_03.
- Required app/data state: Recreated two-zone result with four selected rows.
- Required browser context: Desktop Segment Analyzer Compare flow.

## Allowed Mutations

- Allowed: Open four-track Compare view and switch metrics.
- Not allowed: Remove fixture tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_04 | Compare all four selected tracks, including zero/missing sensor values. | Comparison chart and map align tracks and tolerate missing data. | Compare opened four track cards, local 100 m map, and four-line charts; missing speed/altitude values remained explicit without dropping rows or crashing. | PASS | [assets/MCT_04-comparison.txt](../assets/MCT_04-comparison.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_04-comparison.txt](../assets/MCT_04-comparison.txt) | Track cards, local map scale, chart series, and missing-value behavior. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible map scale/chart series evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Recreate result and open Compare | 2 min |
| Chart/map/missing-data audit | 1 min |

## Handoff Notes

- Completed: Multi-track comparison with missing data.
- Remaining unfinished coverage: None for MCT_04.
- Blocked or not applicable: Pixel-level line overlap is unavailable; local-map and series evidence passed.
- State left for the next packet: Four-track comparison open.
