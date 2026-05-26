# Packet: MAP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_08
- In scope: clicking a single map track.
- Out of scope: overlapping-track selection list.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07.
- Required app/data state: imported tracks visible.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click map tracks.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_08 | Clicked single imported tracks on the map. | A clicked track highlights and details open. | Lannion, Vitry, and Jura single-track clicks opened their detail/selection state without stale geometry. | PASS | `assets/IMP_07-cua-click-lannion.webp`, `assets/IMP_07-map-click-vitry.webp`, `assets/IMP_07-map-click-jura.webp`, `assets/IMP_07-map-click-results.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_07-cua-click-lannion.webp | Single-track map click. |
| assets/IMP_07-map-click-vitry.webp | Single-track map click. |
| assets/IMP_07-map-click-jura.webp | Single-track map click. |
| assets/IMP_07-map-click-results.txt | Map click result summary. |

## Timings

| Step | Timing |
|---|---:|
| Single-track clicks | <1m |

## Handoff Notes

- Completed: MAP_08 terminal PASS.
- Remaining unfinished coverage: continue with MAP_09.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
