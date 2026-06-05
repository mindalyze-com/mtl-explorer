# Packet: MAP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_02
- In scope: Verify all current tracks appear on the map and the visible count is correct.
- Out of scope: Newly imported freshness behavior; covered by MAP_03.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: Eleven visible tracks after FMT_01-FMT_02.
- Required browser context: Authenticated desktop browser context.

## Allowed Mutations

- Allowed: Read track count from UI and API.
- Not allowed: Import/delete tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Compared map visible count with authenticated simplified-track API count. | All tracks appear on map; total/visible count is correct. | Map showed `11 Tracks`; `/mtl/api/tracks/get-simplified` returned `standardFilterCount=11` and `numberOfFilteredMatchedTracks=11`. | PASS | [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt), [assets/MAP_02-eleven-tracks-map.webp](../assets/MAP_02-eleven-tracks-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) | UI/API track-count comparison. |
| [assets/MAP_02-eleven-tracks-map.webp](../assets/MAP_02-eleven-tracks-map.webp) | Map screenshot with eleven-track count. |

## Screenshot Evidence

**Map screenshot with eleven-track count.**

![Map screenshot with eleven-track count.](../assets/MAP_02-eleven-tracks-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Map/API count check | ~2 seconds |

## Handoff Notes

- Completed: MAP_02 terminal as `PASS`.
- Remaining unfinished coverage: Continue with MAP_03.
- Blocked or not applicable: None.
- State left for the next packet: Eleven tracks remain visible.
