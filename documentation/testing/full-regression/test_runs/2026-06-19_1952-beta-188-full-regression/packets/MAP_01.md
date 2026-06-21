# Packet: MAP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_01
- In scope: Verify base map and overlays load on first open.
- Out of scope: Per-track count/geometry correctness, covered by MAP_02 and later map packets.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_09.
- Required app/data state: Authenticated app shell with imported dataset.
- Required browser context: desktop browser fresh root tab.

## Allowed Mutations

- Allowed: Open a fresh app tab.
- Not allowed: Change data, preferences, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_01 | Opened a fresh tab at `/mtl/` and inspected visible map DOM, canvas layers, controls, loading state, and console warnings/errors. | Base map and overlays load on first open with usable map controls and no frozen loading state. | Root view showed two MapLibre canvases, base/overlay map containers, zoom/search/terrain controls, `11 Tracks`, no loading splash, and no console warnings/errors. | PASS | [assets/MAP_01-first-open-map.txt](../assets/MAP_01-first-open-map.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_01-first-open-map.txt](../assets/MAP_01-first-open-map.txt) | DOM, canvas, control, and console evidence for first-open map loading. |

## Screenshot Evidence

No screenshot asset was captured for this packet; browser screenshot capture remained unstable after the SGN_07 outage/recovery test, so direct DOM/canvas evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| First-open map check | <1 min |

## Handoff Notes

- Completed: MAP_01.
- Remaining unfinished coverage: MAP_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: Fresh root tab loaded at `/mtl/` with 11 tracks.
