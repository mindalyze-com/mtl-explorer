# Packet: TRD_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_07
- In scope: track shape previews across user-facing surfaces.
- Out of scope: exact thumbnail rendering dimensions.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_06, FIT_03, MAP_10.
- Required app/data state: imported tracks visible in browser/stats/filter/related/selection contexts.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open existing panels.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_07 | Checked track browser/stat rows, filter context, related tracks, and map selection list. | Track shape preview is visible in browser, filters, stats, related tracks, and selection lists. | Track browser/stat rows displayed line previews; related-track rows and overlap selection list displayed miniature track shapes; filter panel was captured with track geometry visible behind it. | PASS | `assets/FIT_02-track-list-after-fit.webp`, `assets/DEL_03-filter-after-delete.webp`, `assets/FIT_03-fit-related.webp`, `assets/MAP_10-current-selection-open.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_02-track-list-after-fit.webp | Track browser/stat row preview. |
| assets/DEL_03-filter-after-delete.webp | Filter surface with map track geometry behind panel. |
| assets/FIT_03-fit-related.webp | Related-track shape previews. |
| assets/MAP_10-current-selection-open.webp | Selection-list shape previews. |

## Timings

| Step | Timing |
|---|---:|
| Preview surface check | Reused from existing packets |

## Handoff Notes

- Completed: TRD_07 terminal PASS.
- Remaining unfinished coverage: continue with TRD_08.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
