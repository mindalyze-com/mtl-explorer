# Packet: SRC_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_02
- In scope: Select a search result, map flight, and placed marker.
- Out of scope: Clearing the marker.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_01.
- Required app/data state: Bern result visible first.
- Required browser context: Open location-search sheet.

## Allowed Mutations

- Allowed: Select the Bern result and move the map camera.
- Not allowed: Clear the marker before checkpoint.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_02 | Activated Bern, Switzerland. | Map flies to result and places a marker. | Sheet closed, map reframed to Bern, and a distinct semantic Map marker plus Clear search marker action appeared. | PASS | [assets/SRC_02-pick.txt](../assets/SRC_02-pick.txt); [assets/SRC_02-selected.jpg](../assets/SRC_02-selected.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_02-pick.txt](../assets/SRC_02-pick.txt) | Selection, camera, marker, and coexistence observations. |
| [assets/SRC_02-selected.jpg](../assets/SRC_02-selected.jpg) | Durable map screenshot with purple Bern search pin. |

## Screenshot Evidence

- The saved image shows the purple result pin beside existing retained media/heatmap data at the settled Bern viewport.

## Timings

| Step | Timing |
|---|---:|
| Result selection to settled map | About 1.3 s |

## Handoff Notes

- Completed: Result selection, map flight, and marker placement.
- Remaining unfinished coverage: None for SRC_02.
- Blocked or not applicable: None.
- State left for the next packet: Bern search marker remains visible with a semantic clear action.
