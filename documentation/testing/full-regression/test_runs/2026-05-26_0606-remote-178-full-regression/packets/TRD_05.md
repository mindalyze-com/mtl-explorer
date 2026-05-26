# Packet: TRD_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_05
- In scope: graph x-axis, range, point count, and graph height controls.
- Out of scope: chart/mini-map hover sync.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_04.
- Required app/data state: GPX-backed track #100001 detail open on Graphs tab.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: toggle graph controls and sliders/buttons.
- Not allowed: persist track metadata changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_05 | Switched x-axis from Time to Distance, toggled Range, increased chart point count, and increased graph height. | Controls update charts without layout breakage. | Distance became active, Range became inactive, point count increased from 350 to 375, and graph height control moved while charts remained rendered. | PASS | `assets/TRD_05-graphs-controls-top-visible.webp`, `assets/TRD_05-graphs-controls-after-visible.webp`, `assets/TRD_05-graphs-controls-visible.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_05-graphs-controls-top-visible.webp | Controls before interaction in fullscreen detail. |
| assets/TRD_05-graphs-controls-after-visible.webp | Controls after interaction. |
| assets/TRD_05-graphs-controls-visible.txt | Control DOM/state summary. |

## Timings

| Step | Timing |
|---|---:|
| Graph control interaction | <2m |

## Handoff Notes

- Completed: TRD_05 terminal PASS.
- Remaining unfinished coverage: continue with TRD_06.
- Blocked or not applicable: none.
- State left for the next packet: graph controls left in modified display state only.
