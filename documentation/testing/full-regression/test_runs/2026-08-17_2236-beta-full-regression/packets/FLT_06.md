# Packet: FLT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_06
- In scope: Applied filter updates visible count, map colors, legend, and statistics without a full page reload.
- Out of scope: Legend visibility toggles covered by FLT_07.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_05 and FLT_03.
- Required app/data state: Activities by keyword with clean criteria.
- Required browser context: Filter, map legend, and Statistics Overview.

## Allowed Mutations

- Allowed: Set Keyword `Synthetic` and change palette from 8 Colors to 5 Colors.
- Not allowed: Trigger browser reload during the propagation assertion.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_06 | Entered `Synthetic`, changed palette 8→5 Colors, inspected count/legend swatches, then opened Statistics. | Visible count, map colors, legend, and statistics update without a full reload. | Result/toolbar changed 15→2; legend became two categories and changed exact swatch colors with the palette; Statistics showed 2 tracks / 451.89 m. No loading screen appeared. | PASS | [assets/FLT_06-live-updates.txt](../assets/FLT_06-live-updates.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_06-live-updates.txt](../assets/FLT_06-live-updates.txt) | Before/after count, legend labels/counts/swatches, Statistics totals, and no-reload evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered counts, labels, RGB swatches, totals, and loading-screen absence provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Keyword propagation | About 1 s |
| Palette propagation | About 1 s |
| Statistics check | About 4 s |

## Handoff Notes

- Completed: Live count, map color, legend, and Statistics propagation without full reload.
- Remaining unfinished coverage: None for FLT_06.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview open; Activities by keyword with `Synthetic`, 5 Colors, and two matched tracks.
