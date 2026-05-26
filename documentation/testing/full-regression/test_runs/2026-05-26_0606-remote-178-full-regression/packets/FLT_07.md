# Packet: FLT_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_07
- In scope: active filter legend, group visibility toggles, and legend collapse behavior.
- Out of scope: custom color editing.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_06.
- Required app/data state: filter enabled with category colors/legend visible.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: hide/show legend categories and collapse/expand the legend.
- Not allowed: change source data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_07 | Observed the active legend, clicked the Bicycle visibility toggle, collapsed the legend, then expanded/restored Bicycle. | Legend reflects the active filter; collapsing/hiding groups updates the map immediately. | Legend showed Bicycle=9 and Walking=1. Hiding Bicycle immediately changed the map count to 1/10 and changed the Bicycle visibility icon. Collapsing hid the group list; expanding/restoring Bicycle returned count to 10/10. | PASS | `assets/FLT_07-legend-before.webp`, `assets/FLT_07-bicycle-hidden.webp`, `assets/FLT_07-legend-collapsed.webp`, `assets/FLT_07-legend-restored.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_07-legend-before.webp | Active legend before toggles. |
| assets/FLT_07-bicycle-hidden.webp | Bicycle hidden and visible count reduced to 1/10. |
| assets/FLT_07-legend-collapsed.webp | Legend collapsed with groups hidden. |
| assets/FLT_07-legend-restored.webp | Bicycle restored and count back to 10/10. |

## Timings

| Step | Timing |
|---|---:|
| Legend toggle/collapse check | <3m |

## Handoff Notes

- Completed: FLT_07 terminal PASS.
- Remaining unfinished coverage: continue with FLT_08.
- Blocked or not applicable: none.
- State left for the next packet: filter enabled and all legend groups visible.
