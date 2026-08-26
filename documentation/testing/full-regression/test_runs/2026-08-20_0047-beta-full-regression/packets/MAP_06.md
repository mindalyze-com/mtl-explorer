# Packet: MAP_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_06
- In scope: Rapid map pan/zoom stability, including stale lines, missing tiles, and loading state.
- Out of scope: Direction markers and point popups.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_05.
- Required app/data state: Loaded base map with nine-track overlay.
- Required browser context: Desktop in-app browser at the map.

## Allowed Mutations

- Allowed: Pointer-drag the map and activate visible zoom controls rapidly.
- Not allowed: Change map source or server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_06 | Execute three rapid pan gestures and five rapid zoom actions; inspect after settling. | No stale lines, missing tiles, or runaway loading spinner. | Map stayed interactive with two full-size canvases, attribution, nine-track count, and no accessible loading state. ACC_04 prevents reliable visual inspection of stale lines or missing tile areas. | BLOCKED | [assets/MAP_06-pan-zoom.txt](../assets/MAP_06-pan-zoom.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

No product defect is asserted; remaining visual assertions are blocked by the run's capture limitation.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_06-pan-zoom.txt](../assets/MAP_06-pan-zoom.txt) | Stress sequence and stable rendered/accessibility state. |

## Screenshot Evidence

BLOCKED by ACC_04. Stale-line and missing-tile assertions require visual inspection unavailable in this run.

## Timings

| Step | Timing |
|---|---:|
| Pan/zoom sequence plus settle | 3.792 s |

## Handoff Notes

- Completed: Rapid interaction and nonvisual stability checks.
- Remaining unfinished coverage: None; terminally blocked for stale-line and missing-tile visual assertions.
- Blocked or not applicable: BLOCKED by ACC_04.
- State left for the next packet: Map at 30 km scale with settings panel open and nine-track data intact.
