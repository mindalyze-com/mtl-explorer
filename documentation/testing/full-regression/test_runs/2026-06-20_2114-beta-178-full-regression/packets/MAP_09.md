# Packet: MAP_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_09
- In scope: Clicking overlapping tracks shows a selection list and selecting an item opens details.
- Out of scope: closing/deselecting the popup; covered by MAP_10.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07.
- Required app/data state: overlapping imported GPX tracks visible during the earlier map-click pass.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: use completed overlap-click evidence.
- Not allowed: re-import deleted tracks just to repeat this coverage.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_09 | Reused IMP_07 overlap-click evidence for the Voie/Mosel overlapping rendered geometry. | Clicking an area where several tracks overlap shows a selection list; picking one opens details. | PASS: the overlap click opened a two-track selection popup, and choosing the Voie entry opened its detail page. | PASS | [packets/IMP_07.md](IMP_07.md); [assets/IMP_07-overlap-selection.webp](../assets/IMP_07-overlap-selection.webp); [assets/IMP_07-voie-detail-from-map.webp](../assets/IMP_07-voie-detail-from-map.webp); [assets/IMP_07-map-clicks.txt](../assets/IMP_07-map-clicks.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [packets/IMP_07.md](IMP_07.md) | Original overlap-click map packet. |
| [assets/IMP_07-overlap-selection.webp](../assets/IMP_07-overlap-selection.webp) | Two-track overlap selection popup. |
| [assets/IMP_07-voie-detail-from-map.webp](../assets/IMP_07-voie-detail-from-map.webp) | Detail page opened after choosing an overlap popup item. |
| [assets/IMP_07-map-clicks.txt](../assets/IMP_07-map-clicks.txt) | Click coordinate and result log. |

## Screenshot Evidence

![Overlap selection popup](../assets/IMP_07-overlap-selection.webp)

![Detail opened from overlap selection](../assets/IMP_07-voie-detail-from-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Cross-reference assessment | ~2 seconds |

## Handoff Notes

- Completed: MAP_09 is terminal.
- Remaining unfinished coverage: MAP_10 onward.
- Blocked or not applicable: none.
- State left for the next packet: no new mutations for MAP_09.
