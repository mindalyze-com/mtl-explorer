# Packet: GLB_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_01
- In scope: Zoom out far enough and verify globe mode engages automatically.
- Out of scope: Zooming back into flat view or manual globe disable.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_04
- Required app/data state: Main map visible with imported tracks.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Use map zoom controls.
- Not allowed: Change map style, data, or persistent app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_01 | Clicked Zoom out repeatedly from the main map. | Globe view engages automatically at low zoom. | At world-scale zoom, the globe toggle became visible and active (`mtl-globe-active`), with the map still rendered and usable. | PASS | `assets/GLB_01-globe-active-retry.webp`, `assets/GLB_01-globe-state.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GLB_01-globe-active-retry.webp | Screenshot of active globe mode at world-scale zoom. |
| assets/GLB_01-globe-state.txt | DOM/class evidence for active globe state. |

## Timings

| Step | Timing |
|---|---:|
| Zoom out and verify active globe state | <1 min |

## Handoff Notes

- Completed: GLB_01 passed.
- Remaining unfinished coverage: GLB_02 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Map is at low zoom with globe mode active.
