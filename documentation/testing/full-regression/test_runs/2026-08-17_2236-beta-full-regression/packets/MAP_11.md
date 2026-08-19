# Packet: MAP_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_11
- In scope: Clicking an actual rendered point marker and validating its metrics popup.
- Out of scope: Clicking only the connecting line or an unverified coordinate.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_10 and DAT_07.
- Required app/data state: Four-point Segment B track 100017 and enabled point/direction layer.
- Required browser context: Track detail with its mini-map and high-zoom main map.

## Allowed Mutations

- Allowed: Click a positively identified rendered point marker.
- Not allowed: Guess a canvas coordinate.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_11 | Selected four-point Segment B, confirmed nonzero detail/main canvases and known timestamp/elevation point data, then assessed marker targetability. | Clicking an actual marker opens expected time/speed/elevation metrics. | Markers are canvas-only with no semantic target. ACC_04 blocks the visual channel needed to distinguish a marker from its connecting line, so the click/popup was not guessed. | BLOCKED | [assets/MAP_11-point-popup.txt](../assets/MAP_11-point-popup.txt), [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_11-point-popup.txt](../assets/MAP_11-point-popup.txt) | Valid four-point target data, enabled rendering setup, and exact-target constraint. |
| [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) | Frozen synthetic track provenance. |

## Screenshot Evidence

Required to identify the actual rendered point marker but unavailable under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Select Segment B and inspect rendering surfaces | About 5 s |

## Handoff Notes

- Completed: Valid point-bearing track and rendered-map preparation.
- Remaining unfinished coverage: None; terminally blocked for actual marker targeting and popup observation.
- Blocked or not applicable: ACC_04 plus canvas-only point markers.
- State left for the next packet: Segment B details open with mini-map loaded.
