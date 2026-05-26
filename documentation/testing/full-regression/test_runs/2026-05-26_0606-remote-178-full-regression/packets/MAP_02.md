# Packet: MAP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_02
- In scope: map-visible track count versus imported/indexed track data.
- Out of scope: individual track-click behavior, covered by MAP_08/MAP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP, FIT, FMT, MAP_01.
- Required app/data state: imported GPX/FIT/FMT dataset.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: read map UI and installed-app track API.
- Not allowed: change data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Compared map-visible track count against indexed tracks with point data after imports. | All tracks appear on the map and the visible/total count is correct. | UI showed 10 tracks, but the installed-app API listed 14 tracks with point data after imports, including valid TCX/KML/GDB entries not represented in the visible count/list. | FAIL | `assets/SGN_02-valid-login-map.webp`, `assets/MAP_02-api-valid-track-count.txt`, `assets/FMT_01-all-track-browser-after-unique.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MAP-01 | P2 | Valid indexed tracks are omitted from the map/list visible count. | Complete the format imports, reload the map, then compare visible track count with `/api/tracks/get` entries that have point data. | Visible map/list count matches usable indexed tracks or clearly explains hidden/excluded tracks. | UI reports 10 tracks while API reports 14 point-bearing tracks. | `assets/MAP_02-api-valid-track-count.txt`, `assets/SGN_02-valid-login-map.webp` | Users may not see all imported tracks and cannot tell which tracks are hidden. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_02-api-valid-track-count.txt | API count of point-bearing indexed tracks. |
| assets/SGN_02-valid-login-map.webp | Map UI showing 10 tracks. |
| assets/FMT_01-all-track-browser-after-unique.txt | Track browser rows showing 10 visible rows. |

## Timings

| Step | Timing |
|---|---:|
| Count comparison | <1m |

## Handoff Notes

- Completed: MAP_02 terminal FAIL.
- Remaining unfinished coverage: continue with MAP_03.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
