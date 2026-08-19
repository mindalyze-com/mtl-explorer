# Packet: MAP_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_08
- In scope: Direct single-track canvas click, highlight, and details opening.
- Out of scope: Selection through Review Tracks as a substitute for the map click.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_07.
- Required app/data state: Real track 100004 and high-zoom Lannion map.
- Required browser context: Signed-in map with functional track details route.

## Allowed Mutations

- Allowed: Click a visually identified single rendered track.
- Not allowed: Guess an unverified canvas coordinate.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_08 | Prepared a high-zoom real-track area and confirmed the same track's semantic table selection opens details. Assessed whether the rendered line could be targeted directly. | Clicking a single line highlights it and opens details. | Detail opening works through Review Tracks, but the canvas line exposes no semantic target and ACC_04 blocks the visual channel needed to click it reliably. Direct highlight path was not guessed. | BLOCKED | [assets/MAP_08-single-track-click.txt](../assets/MAP_08-single-track-click.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_08-single-track-click.txt](../assets/MAP_08-single-track-click.txt) | Prepared surface, working detail route, and direct-line targeting constraint. |

## Screenshot Evidence

Required to locate the canvas line for a non-guessed click but unavailable under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Targetability assessment | Under 1 s after MAP_07 setup |

## Handoff Notes

- Completed: Real single-track area and working semantic details path confirmed.
- Remaining unfinished coverage: None; terminally blocked for the direct canvas click/highlight path.
- Blocked or not applicable: ACC_04 plus canvas-only line hit target.
- State left for the next packet: Track 100004 detail remains open at high zoom.
