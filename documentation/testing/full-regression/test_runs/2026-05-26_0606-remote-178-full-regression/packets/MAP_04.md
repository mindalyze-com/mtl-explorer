# Packet: MAP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_04
- In scope: map/list disappearance after required delete-two-track flow.
- Out of scope: deleted-track API URL probing, per DEL_05.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01-DEL_05.
- Required app/data state: Vitry and VoieVerte source files deleted and watcher processed removal.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: observe post-delete map/list/filter/heatmap state.
- Not allowed: re-import deleted tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_04 | Verified map and user-visible surfaces after deleting two source files. | Deleted tracks disappear from map sources, selection lists, and popups. | Map/heatmap dropped to 3 GPX tracks at that stage; searches for Vitry and Voie returned no matches; remaining tracks still opened. | PASS | `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-search-after-delete.txt`, `assets/DEL_04-remaining-jura-opens.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/DEL_03-map-after-delete.webp | Map/heatmap after deletion. |
| assets/DEL_03-search-after-delete.txt | Deleted names absent from search. |
| assets/DEL_04-remaining-jura-opens.webp | Remaining track still opens. |

## Timings

| Step | Timing |
|---|---:|
| Post-delete map check | <1m |

## Handoff Notes

- Completed: MAP_04 terminal PASS.
- Remaining unfinished coverage: continue with MAP_05.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
