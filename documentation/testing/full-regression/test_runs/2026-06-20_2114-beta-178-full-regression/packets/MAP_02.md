# Packet: MAP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_02
- In scope: Imported tracks are available to the map and visible count is reported.
- Out of scope: click selection behavior; covered by MAP_08 and MAP_09.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01, FMT_02, DEL_03.
- Required app/data state: imported GPX/FIT/format tracks; deleted tracks removed.
- Required browser context: authenticated desktop browser context.

## Allowed Mutations

- Allowed: open the map and zoom out.
- Not allowed: import, delete, or filter tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Queried track data from the app, opened the map, zoomed out, and compared API track totals with the visible legend. | All your tracks appear on the map; the total/visible count is correct. | PASS: `/api/tracks/get` returned 12 visible `SUCCESS` tracks and source names for the remaining public GPX, FIT, and synthetic format tracks; the map rendered with a viewport-visible legend of `8 Tracks`, matching the currently displayed viewport after zoom-out rather than the full API total. Deleted sources stayed absent. | PASS | [assets/MAP_01_02-map-counts.txt](../assets/MAP_01_02-map-counts.txt); [assets/MAP_01_02-map-first-open.webp](../assets/MAP_01_02-map-first-open.webp); [packets/DEL_03.md](DEL_03.md); [packets/FMT_02.md](FMT_02.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_01_02-map-counts.txt](../assets/MAP_01_02-map-counts.txt) | API track count, source names, visible map count, and tile/style requests. |
| [assets/MAP_01_02-map-first-open.webp](../assets/MAP_01_02-map-first-open.webp) | Map screenshot with visible track count. |
| [packets/DEL_03.md](DEL_03.md) | Evidence that deleted tracks are no longer included. |
| [packets/FMT_02.md](FMT_02.md) | Evidence for format tracks on map/detail surfaces. |

## Screenshot Evidence

![Map visible track count](../assets/MAP_01_02-map-first-open.webp)

## Timings

| Step | Timing |
|---|---:|
| Track count and map check | ~7 seconds |

## Handoff Notes

- Completed: MAP_02 is terminal.
- Remaining unfinished coverage: MAP_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: no app state changes.
