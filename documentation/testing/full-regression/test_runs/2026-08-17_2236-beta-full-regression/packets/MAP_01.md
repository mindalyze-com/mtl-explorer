# Packet: MAP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_01
- In scope: Base map and overlays on first signed-in open.
- Out of scope: Pixel-level screenshot comparison, blocked by ACC_04.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_09.
- Required app/data state: Healthy installation with 14 visible tracks.
- Required browser context: Fresh valid sign-in followed by settled main map.

## Allowed Mutations

- Allowed: Close the read-only Admin panel to expose the map.
- Not allowed: Change track or map configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_01 | Opened the signed-in root map and inspected the settled map regions, rendering surfaces, controls, attribution, overlay count, loading state, and console. | Base map and overlays load on first open. | Two full-height nonzero rendering canvases, map controls, OpenStreetMap attribution, and the 14-track overlay were present. Loading cleared and no console errors were captured. | PASS | [assets/MAP_01-base-map.txt](../assets/MAP_01-base-map.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_01-base-map.txt](../assets/MAP_01-base-map.txt) | Map regions, canvases, controls, attribution, track overlay, loading, and console evidence. |

## Screenshot Evidence

Not available because ACC_04 blocks screenshots; semantic and rendering-surface evidence was used.

## Timings

| Step | Timing |
|---|---:|
| Return to and inspect settled map | About 4 s |

## Handoff Notes

- Completed: Base map and overlay first-open validation.
- Remaining unfinished coverage: None for MAP_01.
- Blocked or not applicable: Screenshot capture only.
- State left for the next packet: Signed in on the settled root map with 14 visible tracks.
