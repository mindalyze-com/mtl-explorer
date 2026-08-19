# Packet: HMO_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: HMO_02
- In scope: Every worldwide/Swiss route overlay, independent toggles, opacity sliders, attribution, and ordering with tracks.
- Out of scope: Heatmap filter updates.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_01.
- Required app/data state: Heatmap and GPS tracks visible at Bern.
- Required browser context: Signed-in desktop Map > Route overlays.

## Allowed Mutations

- Allowed: Toggle presentation-only route layers and drag their opacity controls.
- Not allowed: Alter track or filter data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_02 | Enabled each of seven route layers independently, changed every layer's opacity, inspected attribution, and checked composition with GPS track/heatmap/media. | OSM/Swiss overlays work independently; opacity sliders work; ordering stays correct. | All seven changed None↔1 of 7 independently; every slider moved from 100 to 57-64; Waymarked/SchweizMobil/swisstopo attribution matched source; user track remained above the route layer. | PASS | [assets/HMO_02-overlays.txt](../assets/HMO_02-overlays.txt); [assets/HMO_02-overlay-order.jpg](../assets/HMO_02-overlay-order.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_02-overlays.txt](../assets/HMO_02-overlays.txt) | Exact seven-layer toggle, attribution, opacity, ordering, and cleanup results. |
| [assets/HMO_02-overlay-order.jpg](../assets/HMO_02-overlay-order.jpg) | Swiss Hiking trails beneath retained purple GPS track, media, and heatmap. |

## Screenshot Evidence

- The saved desktop image shows the yellow Swiss trail network over the base map while the purple user track and media/heatmap remain visible above it.

## Timings

| Step | Timing |
|---|---:|
| Each overlay toggle and paint | Under 1 s |
| Each opacity drag | Under 1 s |

## Handoff Notes

- Completed: Full seven-overlay toggle/opacity/ordering coverage.
- Remaining unfinished coverage: None for HMO_02.
- Blocked or not applicable: None.
- State left for the next packet: Route overlays restored to none; heatmap remains enabled at 52%.
