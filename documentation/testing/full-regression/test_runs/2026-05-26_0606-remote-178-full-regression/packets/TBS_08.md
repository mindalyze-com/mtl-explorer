# Packet: TBS_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_08
- In scope: statistics updates after the required five-GPX import and after deleting two imported tracks.
- Out of scope: later FIT/format imports and current expanded dataset totals.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_09, DEL_03.
- Required app/data state: five-GPX import evidence captured before deletion; two-GPX deletion evidence captured afterward.
- Required browser context: signed-in desktop browser for captured evidence.

## Allowed Mutations

- Allowed: reuse direct evidence from the earlier required import/delete packets.
- Not allowed: change current data state for this cross-state verification.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_08 | Reviewed the five-GPX import statistics evidence and the post-delete statistics/list evidence. | Stats update after the required five-GPX import and again after deleting two imported tracks; no stale deleted-track totals remain. | Five-GPX import increased stats to 5 tracks, 1,043 km, 23h31m, with rankings, period summaries, browser summary, and heatmap density. Deleting Vitry and VoieVerte dropped visible map/heatmap/list state to 3 tracks and searches for the deleted names returned no matching tracks. | PASS | `assets/IMP_05-stats-after-import.webp`, `assets/IMP_06-track-list-after-import.webp`, `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-track-list-after-delete.webp`, `assets/DEL_03-search-after-delete.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_05-stats-after-import.webp | Five-GPX post-import statistics totals and summaries. |
| assets/IMP_06-track-list-after-import.webp | Five-GPX track-browser summary. |
| assets/DEL_03-map-after-delete.webp | Map/heatmap state after two source-file deletions. |
| assets/DEL_03-track-list-after-delete.webp | Track-browser state after deletion dropped to remaining GPX tracks. |
| assets/DEL_03-search-after-delete.txt | Deleted-name search absence evidence. |

## Timings

| Step | Timing |
|---|---:|
| Cross-state evidence review | <1m |

## Handoff Notes

- Completed: TBS_08 terminal PASS.
- Remaining unfinished coverage: continue with TBS_09.
- Blocked or not applicable: none.
- State left for the next packet: Stats Overview remains the expected starting view.
