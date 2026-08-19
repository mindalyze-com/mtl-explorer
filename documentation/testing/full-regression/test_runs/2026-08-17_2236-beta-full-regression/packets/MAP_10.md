# Packet: MAP_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_10
- In scope: Close/deselect after an overlapping-track selection.
- Out of scope: New selection behavior covered by MAP_09.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_09.
- Required app/data state: Segment B details open from the overlap list.
- Required browser context: High-zoom Bern map.

## Allowed Mutations

- Allowed: Close the details and accept an in-app freshness reload.
- Not allowed: Change stored tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_10 | Closed Segment B details, accepted the pending in-app freshness reload, and inspected route, panels, canvases, loading state, and console. | Deselect/close returns the map to its normal state. | Root `/mtl/` returned with details and overlap list not visible, 15-track control present, two full map canvases, no visible progress indicator, and no console errors. | PASS | [assets/MAP_10-deselect.txt](../assets/MAP_10-deselect.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_10-deselect.txt](../assets/MAP_10-deselect.txt) | Close action and settled no-selection map state. |

## Screenshot Evidence

Unavailable under ACC_04; visible-state and route evidence was sufficient.

## Timings

| Step | Timing |
|---|---:|
| Close details | About 4 s |
| Freshness settle and inspect | About 1.5 s |

## Handoff Notes

- Completed: Close/deselect and normal-state restoration.
- Remaining unfinished coverage: None for MAP_10.
- Blocked or not applicable: None.
- State left for the next packet: Normal settled root map with 15 visible tracks.
