# Packet: DEL_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_03
- In scope: delete-two-track flow for imported GPX source files.
- Out of scope: FIT import and non-deleted track format coverage.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01-IMP_09.
- Required app/data state: five GPX tracks imported before deletion.
- Required browser context: desktop signed-in browser.

## Allowed Mutations

- Allowed: delete selected GPX source files from `./data/gpx`, wait for watcher, refresh UI.
- Not allowed: delete unrelated files or perform Docker cleanup.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_03 | Refreshed UI and checked map, browser/search, filter context, heatmap, and stats/list totals after deletion. | Deleted tracks disappear from map, browser, filter results, heatmap, selection/detail surfaces, related lists, and stats totals. | Map and heatmap dropped from 5 to 3 tracks; track list contained only Jura/Mosel/Lannion; searches for Vitry and Voie returned no matching tracks; filter panel opened against the 3-track state. Related-list deletion will also be rechecked in later TRD related coverage. | PASS | `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-track-list-after-delete.webp`, `assets/DEL_03-track-list-after-delete.txt`, `assets/DEL_03-search-after-delete.txt`, `assets/DEL_03-filter-after-delete.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/DEL_03-map-after-delete.webp | Map/heatmap after deletion. |
| assets/DEL_03-track-list-after-delete.webp | Track list after deletion. |
| assets/DEL_03-track-list-after-delete.txt | Remaining track rows after deletion. |
| assets/DEL_03-search-after-delete.txt | Search absence for deleted names and presence of remaining names. |
| assets/DEL_03-filter-after-delete.webp | Filter panel after deletion. |

## Timings

| Step | Timing |
|---|---:|
| Delete flow step | <1m |

## Handoff Notes

- Completed: DEL_03 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: three GPX tracks remain (Jura, Mosel, Lannion); FIT not imported yet.
