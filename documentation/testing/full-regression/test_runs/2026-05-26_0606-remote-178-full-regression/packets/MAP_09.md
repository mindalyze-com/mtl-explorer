# Packet: MAP_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_09
- In scope: overlapping track click selection list.
- Out of scope: deselect/close behavior, covered by MAP_10.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07.
- Required app/data state: overlapping Mosel/VoieVerte tracks available before deletion.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click overlapping map area and choose rows.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_09 | Clicked a map area with overlapping Mosel/VoieVerte tracks and selected each result. | Selection list appears; picking one opens its details. | A two-track selection list appeared; selecting Mosel opened #100002 and selecting VoieVerte opened #100004. | PASS | `assets/IMP_07-map-selection-mosel.webp`, `assets/IMP_07-map-selection-voieverte.webp`, `assets/IMP_07-map-click-results.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_07-map-selection-mosel.webp | Overlap selection list with Mosel selected. |
| assets/IMP_07-map-selection-voieverte.webp | Overlap selection list with VoieVerte selected. |
| assets/IMP_07-map-click-results.txt | Overlap click result summary. |

## Timings

| Step | Timing |
|---|---:|
| Overlap click selection | <1m |

## Handoff Notes

- Completed: MAP_09 terminal PASS.
- Remaining unfinished coverage: continue with MAP_10.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
