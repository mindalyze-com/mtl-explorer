# Packet: MAP_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_05
- In scope: Track line detail and integrity when zooming in.
- Out of scope: Server geometry validation as a substitute for end-user rendering.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_04.
- Required app/data state: Populated map with a real multi-point public track.
- Required browser context: Signed-in map and Review Tracks.

## Allowed Mutations

- Allowed: Select a track and change map zoom.
- Not allowed: Change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_05 | Opened real track 100004 and clicked main-map Zoom in four times. Checked scale, rendering surfaces, loading state, and console. | Detail improves with no duplicate or broken lines. | Zoom worked (500 km to 50 km), all map canvases stayed nonzero, loading cleared, and no console errors appeared. The required canvas line-quality assertion cannot be seen because screenshots are blocked and lines lack semantic targets. | BLOCKED | [assets/MAP_05-zoom.txt](../assets/MAP_05-zoom.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_05-zoom.txt](../assets/MAP_05-zoom.txt) | Selected-track zoom exercise, rendering health, and visual-channel constraint. |

## Screenshot Evidence

Required for the remaining line-integrity assertion but unavailable under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Open track details | About 4 s |
| Four-step zoom and settle | About 2 s |

## Handoff Notes

- Completed: Real-track selection, high-zoom exercise, scale, canvas, loading, and console checks.
- Remaining unfinished coverage: None; terminally blocked for direct visual line-quality evidence.
- Blocked or not applicable: ACC_04 screenshot failure plus canvas-only line rendering.
- State left for the next packet: Track 100004 open at main-map 50 km scale.
