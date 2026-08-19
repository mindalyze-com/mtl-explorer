# Packet: HMO_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: HMO_01
- In scope: Heatmap toggle, visual rendering over tracks, and opacity response.
- Out of scope: Other overlays and filter-driven updates.

## Prerequisites

- Required previous coverage IDs or run packets: MED_12.
- Required app/data state: Thirteen visible tracks around a known Bern activity.
- Required browser context: Signed-in desktop main map.

## Allowed Mutations

- Allowed: Toggle heatmap and change its presentation opacity.
- Not allowed: Change tracked data or filters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_01 | Enabled Heatmap, inspected the map, and dragged opacity from 100 to 52. | Heatmap draws over map without hiding tracks and responds to opacity. | Visible density rendered while purple track lines stayed present; accessible slider reported 100 then 52 and the density intensity reduced live. | PASS | [assets/HMO_01-heatmap.txt](../assets/HMO_01-heatmap.txt); [assets/HMO_01-heatmap-100.jpg](../assets/HMO_01-heatmap-100.jpg); [assets/HMO_01-heatmap-52.jpg](../assets/HMO_01-heatmap-52.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_01-heatmap.txt](../assets/HMO_01-heatmap.txt) | Toggle state, numeric opacity, and visual observations. |
| [assets/HMO_01-heatmap-100.jpg](../assets/HMO_01-heatmap-100.jpg) | Full-opacity heatmap over visible route lines. |
| [assets/HMO_01-heatmap-52.jpg](../assets/HMO_01-heatmap-52.jpg) | Same map with heatmap reduced to 52%. |

## Screenshot Evidence

- The paired desktop screenshots preserve the visible Bern density plume at 100% and 52%, with route lines still visible in both.

## Timings

| Step | Timing |
|---|---:|
| Toggle to rendered heatmap | Under 1 s |
| Opacity drag and repaint | Under 1 s |

## Handoff Notes

- Completed: Full heatmap toggle/render/opacity behavior.
- Remaining unfinished coverage: None for HMO_01.
- Blocked or not applicable: None.
- State left for the next packet: Main map at Bern; heatmap enabled at 52%; GPS tracks/media/track-points enabled.
