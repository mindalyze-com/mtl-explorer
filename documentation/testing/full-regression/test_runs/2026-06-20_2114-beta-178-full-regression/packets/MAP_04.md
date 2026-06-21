# Packet: MAP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_04
- In scope: Deleted tracks disappear from map-related surfaces.
- Out of scope: the file deletion operation itself; covered by DEL_01 and DEL_02.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01 through DEL_03.
- Required app/data state: required deletion flow completed.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: use completed deletion-flow evidence.
- Not allowed: delete additional tracks for this duplicate coverage check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_04 | Reused completed deletion-flow evidence after removing two GPX files from the watched folder. | Deleted tracks from the required data-change flow disappear from all map sources, selection lists, and popups. | PASS: deleted tracks were absent from map/browser/filter/heatmap/stats/related surfaces, and the visible track count dropped to three after deletion; remaining tracks still opened normally. | PASS | [packets/DEL_03.md](DEL_03.md); [assets/DEL_03-surfaces.txt](../assets/DEL_03-surfaces.txt); [assets/DEL_03-map-after-delete.webp](../assets/DEL_03-map-after-delete.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [packets/DEL_03.md](DEL_03.md) | Original deleted-track surface verification packet. |
| [assets/DEL_03-surfaces.txt](../assets/DEL_03-surfaces.txt) | Map/browser/filter/heatmap/stats/related surface evidence. |
| [assets/DEL_03-map-after-delete.webp](../assets/DEL_03-map-after-delete.webp) | Map surface after deletion processing. |

## Screenshot Evidence

![Map after deletion](../assets/DEL_03-map-after-delete.webp)

## Timings

| Step | Timing |
|---|---:|
| Cross-reference assessment | ~2 seconds |

## Handoff Notes

- Completed: MAP_04 is terminal.
- Remaining unfinished coverage: MAP_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: no new mutations for MAP_04.
