# Packet: IMP_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_05
- In scope: Apply freshness reload and verify map, track browser, filters, and statistics show new data.
- Out of scope: Per-file search/map-detail checks.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_04.
- Required app/data state: Server has five tracks; browser stale with Reload banner.
- Required browser context: Same signed-in desktop context.

## Allowed Mutations

- Allowed: Click freshness Reload; use visible Reset filter to remove an unrelated persisted criterion.
- Not allowed: Full browser restart or direct client cache manipulation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_05 | Click banner Reload; confirm matching server/client tokens; reset the persisted filter through UI; inspect Filter result, Map count, Review tracks, and Statistics Overview. | All main filter-aware views show the newly indexed data without a full browser restart. | Data status became In sync; Filter/Map show 5 tracks; browser lists all 5 with 1,043 km/23h31m; Stats shows 5 tracks and populated totals/highlights. | PASS | [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Freshness synchronization and cross-view imported-data evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; direct visible-state values are recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| Freshness reload and cross-view check | 3 min |

## Handoff Notes

- Completed: Banner reload, filter reset, and map/browser/filter/stats visibility of all five tracks.
- Remaining unfinished coverage: None for IMP_05.
- Blocked or not applicable: None.
- State left for the next packet: Browser is in sync, all five tracks visible, no active narrowing criteria.
