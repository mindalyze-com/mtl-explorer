# Packet: SRC_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SRC_02
- In scope: Location result selection, map fly-to behavior, and marker placement.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_01 terminal; location search results visible.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Select a search result, inspect marker count, capture evidence, and update SRC_02 packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_02 | Selected the first Zurich search result and waited for the map to settle. | The map flies to the selected place and places a marker. | PASS: the search marker count changed from 0 to 1 and the marker used the mtl-location-search-marker class after selecting Zürich. | PASS | [assets/SRC_02-picked-marker.webp](../assets/SRC_02-picked-marker.webp); [assets/SRC_02-picked-marker.txt](../assets/SRC_02-picked-marker.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_02-picked-marker.webp](../assets/SRC_02-picked-marker.webp) | Screenshot evidence |
| [assets/SRC_02-picked-marker.txt](../assets/SRC_02-picked-marker.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SRC_02-picked-marker.webp](../assets/SRC_02-picked-marker.webp)

## Timings

| Step | Timing |
|---|---:|
| Result select and fly-to wait | ~4 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
