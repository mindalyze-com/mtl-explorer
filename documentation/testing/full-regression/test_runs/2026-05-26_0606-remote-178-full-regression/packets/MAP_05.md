# Packet: MAP_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_05
- In scope: zooming in on track geometry.
- Out of scope: direction-arrow settings and point popups.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07.
- Required app/data state: imported GPX tracks visible on map.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: zoom/pan/click map.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_05 | Zoomed to imported GPX tracks and opened track selections/details. | Detail/precision improves at zoom; no duplicated or broken lines. | Zoomed/clicked imported tracks rendered continuous line geometry and opened selections/details without stale duplicate lines. | PASS | `assets/IMP_07-zoom-lannion.webp`, `assets/IMP_07-map-click-jura.webp`, `assets/IMP_07-map-click-results.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_07-zoom-lannion.webp | Zoomed track line geometry. |
| assets/IMP_07-map-click-jura.webp | Clicked track after zoom/navigation. |
| assets/IMP_07-map-click-results.txt | Map click/selection summary. |

## Timings

| Step | Timing |
|---|---:|
| Zoom/click check | <1m |

## Handoff Notes

- Completed: MAP_05 terminal PASS.
- Remaining unfinished coverage: continue with MAP_06.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
