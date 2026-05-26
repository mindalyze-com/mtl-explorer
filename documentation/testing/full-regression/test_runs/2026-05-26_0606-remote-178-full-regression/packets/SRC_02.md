# Packet: SRC_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SRC_02
- In scope: Select a search result and verify the map moves to it with a marker.
- Out of scope: Clearing the marker.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_01
- Required app/data state: Search panel open with `Bern` results.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Select the `Bern, Switzerland` result.
- Not allowed: Change imported data or app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_02 | Selected the top `Bern, Switzerland` search result. | Map flies to the selected result and places a marker. | Search panel closed back to the map and a visible `mtl-location-search-marker` appeared with an associated clear-marker button. | PASS | `assets/SRC_02-bern-selected.webp`, `assets/SRC_02-bern-selected.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SRC_02-bern-selected.webp | Screenshot after selecting the Bern result. |
| assets/SRC_02-bern-selected.txt | DOM evidence for the placed location-search marker. |

## Timings

| Step | Timing |
|---|---:|
| Result selection and marker verification | <1 min |

## Handoff Notes

- Completed: SRC_02 passed.
- Remaining unfinished coverage: SRC_03 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Bern search marker is visible on the map.
