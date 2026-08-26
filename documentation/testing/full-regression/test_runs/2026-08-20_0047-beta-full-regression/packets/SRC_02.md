# Packet: SRC_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_02
- In scope: Select one location result and confirm the map moves there and places a marker.
- Out of scope: Marker removal and no-result handling covered by SRC_03-04.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_01.
- Required app/data state: Populated `Zurich` location results.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Select the first Zürich city result.
- Not allowed: Clear the resulting marker in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_02 | Selected the first Zürich city result and inspected the settled map controls. | Map flies to the selected result and places a marker. | Results closed, the map settled at 100 m, and a labelled map marker with `Clear search marker` appeared. | PASS | [assets/SRC_02-search-marker.txt](../assets/SRC_02-search-marker.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_02-search-marker.txt](../assets/SRC_02-search-marker.txt) | Selected result and post-selection map/marker state. |

## Screenshot Evidence

Live desktop inspection confirmed the search marker and moved map. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Result selection and settlement | About 1.2 s |

## Handoff Notes

- Completed: Result selection, map move, and marker placement.
- Remaining unfinished coverage: None for SRC_02.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Zürich marker present at 100 m scale.
