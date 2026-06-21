# Packet: MAP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_02
- In scope: Verify all current tracks appear in the map-visible total and the total/visible count is correct.
- Out of scope: Per-track click/geometry precision, covered by MAP_05, MAP_08, MAP_09, and MAP_11.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: Imported dataset after GPX deletion and FIT/FMT imports.
- Required browser context: desktop root map tab.

## Allowed Mutations

- Allowed: Read map DOM and authenticated track API.
- Not allowed: Change data, preferences, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Compared the visible root map count with authenticated `/api/tracks/get` total. | All current tracks are reflected on the map and the visible total is correct. | Root map showed `11 Tracks`; authenticated track API returned 11 track IDs; console warnings/errors were empty. | PASS | [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) | UI/API count comparison and current track IDs. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct DOM/API evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Map/API count comparison | <1 min |

## Handoff Notes

- Completed: MAP_02.
- Remaining unfinished coverage: MAP_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: Root map tab loaded at `/mtl/` with 11 tracks.
