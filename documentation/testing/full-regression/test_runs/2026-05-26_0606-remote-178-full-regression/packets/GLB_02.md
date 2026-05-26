# Packet: GLB_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_02
- In scope: Zoom in from active globe mode and verify the map returns to flat view.
- Out of scope: Manual globe disable persistence.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_01
- Required app/data state: Map at low zoom with globe mode active.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Use map zoom controls.
- Not allowed: Change data or persistent app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_02 | Clicked Zoom in repeatedly from active globe mode. | Map returns to flat view. | At 5 km scale, the globe control was hidden and no longer had `mtl-globe-active`; the map rendered normally with tracks. | PASS | `assets/GLB_02-flat-after-zoom-in.webp`, `assets/GLB_02-flat-state.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GLB_02-flat-after-zoom-in.webp | Screenshot after zooming back into flat view. |
| assets/GLB_02-flat-state.txt | DOM/class evidence for inactive globe state. |

## Timings

| Step | Timing |
|---|---:|
| Zoom in and verify flat state | <1 min |

## Handoff Notes

- Completed: GLB_02 passed.
- Remaining unfinished coverage: GLB_03 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Map is zoomed in with globe mode inactive.
