# Packet: MAP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_01
- In scope: Base map and overlays on first visible Map open.
- Out of scope: Interaction stress and track selection, covered later.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_09.
- Required app/data state: Healthy signed-in app with synchronized nine-track client data.
- Required browser context: In-app browser on Planner before opening Map.

## Allowed Mutations

- Allowed: Open Map through visible navigation and inspect rendered elements.
- Not allowed: Change map settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_01 | Open Map and inspect its rendered base-map canvases, attribution, controls, and data overlay. | Base map and overlays load on first open. | Two visible 981×998 MapLibre canvases rendered with OpenStreetMap attribution, scale/controls, OSM Topo Contrast, and the 9-track overlay. | PASS | [assets/MAP_01-base-map.txt](../assets/MAP_01-base-map.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_01-base-map.txt](../assets/MAP_01-base-map.txt) | Rendered canvas dimensions, attribution, settings summary, and controls. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; rendered DOM dimensions and accessible state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Open and inspect Map | <1 min |

## Handoff Notes

- Completed: Base-map and overlay first-open verification.
- Remaining unfinished coverage: None for MAP_01.
- Blocked or not applicable: None.
- State left for the next packet: Map settings panel open over the loaded nine-track map.
