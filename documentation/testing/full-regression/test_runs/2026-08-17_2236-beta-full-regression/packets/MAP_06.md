# Packet: MAP_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_06
- In scope: Fast pan/zoom stability, loading completion, and visual map integrity.
- Out of scope: Server geometry as a substitute for rendered output.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_05.
- Required app/data state: Settled populated map.
- Required browser context: Unobstructed root map.

## Allowed Mutations

- Allowed: Pan and zoom map view.
- Not allowed: Change stored application data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_06 | Performed three rapid drag paths and five alternating zoom clicks, then inspected loading indicators, canvases, controls, attribution, and console. | No stale lines, missing tiles, or runaway loading spinners. | Interaction settled with two nonzero canvases, zero visible progress indicators, no loading text, controls/attribution intact, and no console errors. Canvas-only stale-line and missing-tile assertions cannot be seen because screenshots are blocked. | BLOCKED | [assets/MAP_06-pan-zoom.txt](../assets/MAP_06-pan-zoom.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_06-pan-zoom.txt](../assets/MAP_06-pan-zoom.txt) | Rapid interaction, settled loading, rendering-surface, and console evidence. |

## Screenshot Evidence

Required for stale-line and missing-tile inspection but unavailable under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Three pans and five zoom actions | About 1.6 s |
| Settle and inspect | About 1.2 s |

## Handoff Notes

- Completed: Rapid pan/zoom exercise plus spinner, loading, canvas, control, attribution, and console checks.
- Remaining unfinished coverage: None; terminally blocked only for direct canvas visual-integrity evidence.
- Blocked or not applicable: ACC_04 screenshot failure and canvas-only tile/track rendering.
- State left for the next packet: Signed-in root map settled after pan/zoom stress.
