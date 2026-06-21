# Packet: HMO_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: HMO_01
- In scope: Heatmap layer toggle, visible map state, and heatmap opacity control.
- Out of scope: Other map overlays and filter-driven heatmap refresh; covered by HMO_02 and HMO_03.

## Prerequisites

- Required previous coverage IDs or run packets: MED_05 terminal.
- Required app/data state: Quick-install beta stack running with imported GPS tracks.
- Required browser context: Fresh authenticated desktop context.

## Allowed Mutations

- Allowed: Toggle Heatmap, adjust Heatmap opacity, capture screenshot/text evidence, update packet/run-state.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_01 | Opened Map settings, toggled Heatmap on, adjusted the Heatmap opacity slider from 100 to 50, and verified GPS Tracks stayed enabled with the map still showing `13 Tracks`. | Heatmap draws over the map without hiding tracks and respects opacity. | PASS: Heatmap row changed to enabled, the Heatmap opacity slider was visible and reported `50`, persisted map settings recorded `heatmapVisible:true` and `layerOpacities.heatmap:50`, GPS Tracks remained enabled, two map canvases stayed rendered, and the map still showed `13 Tracks`. | PASS | [assets/HMO_01-heatmap-opacity.webp](../assets/HMO_01-heatmap-opacity.webp); [assets/HMO_01-heatmap-toggle.txt](../assets/HMO_01-heatmap-toggle.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_01-heatmap-opacity.webp](../assets/HMO_01-heatmap-opacity.webp) | Heatmap enabled in Map settings with opacity slider adjusted and track count still visible. |
| [assets/HMO_01-heatmap-toggle.txt](../assets/HMO_01-heatmap-toggle.txt) | Browser evidence for Heatmap/GPS Tracks row state, persisted opacity, canvas count, and assertions. |

## Screenshot Evidence

![Heatmap enabled with opacity adjusted](../assets/HMO_01-heatmap-opacity.webp)

## Timings

| Step | Timing |
|---|---:|
| Heatmap toggle and opacity check | <1 min |

## Handoff Notes

- Completed: HMO_01 PASS.
- Remaining unfinished coverage: HMO_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: Browser context closed; no server data changed.
