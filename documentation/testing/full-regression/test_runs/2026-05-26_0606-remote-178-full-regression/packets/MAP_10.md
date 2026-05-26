# Packet: MAP_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_10
- In scope: closing/deselecting an overlap selection list.
- Out of scope: choosing overlap rows, covered by MAP_09.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_09.
- Required app/data state: current map has overlapping Jura/format tracks.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click map overlap and close selection sheet.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_10 | Clicked current overlap cluster, then clicked the selection sheet `Close` button. | Closing/deselecting selection returns map to normal state. | An 8-track selection list opened; after Close, the selection text disappeared and the normal 10-track map state remained. | PASS | `assets/MAP_10-current-selection-open.webp`, `assets/MAP_10-current-selection-closed.webp`, `assets/MAP_10-current-selection-close.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_10-current-selection-open.webp | Overlap selection list open. |
| assets/MAP_10-current-selection-closed.webp | Map after closing selection. |
| assets/MAP_10-current-selection-close.txt | Close result summary. |

## Timings

| Step | Timing |
|---|---:|
| Open/close selection | <1m |

## Handoff Notes

- Completed: MAP_10 terminal PASS.
- Remaining unfinished coverage: continue with MAP_11.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
