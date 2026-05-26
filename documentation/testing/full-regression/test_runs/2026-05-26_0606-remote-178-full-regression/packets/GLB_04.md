# Packet: GLB_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_04
- In scope: Verify zoom limits do not trap the map at edges.
- Out of scope: Full map-rendering performance tests.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_03
- Required app/data state: Main map visible at low zoom/globe state.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Pan and zoom the map.
- Not allowed: Change data or persistent app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_04 | At low zoom, dragged horizontally in both directions, then zoomed back into flat view. | Zoom limits do not trap the map at edges. | Low-zoom panning kept the map rendered, and subsequent Zoom in controls still changed from 2000 km globe to 5 km flat view. | PASS | `assets/GLB_04-low-zoom-pan.webp`, `assets/GLB_04-zoomed-back-in.webp`, `assets/GLB_04-zoom-limits.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GLB_04-low-zoom-pan.webp | Screenshot after low-zoom edge panning. |
| assets/GLB_04-zoomed-back-in.webp | Screenshot after zooming back into flat map view. |
| assets/GLB_04-zoom-limits.txt | Recorded scale/control state before and after edge panning. |

## Timings

| Step | Timing |
|---|---:|
| Pan at low zoom and zoom back in | <1 min |

## Handoff Notes

- Completed: GLB_04 passed.
- Remaining unfinished coverage: ADM_01 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Map is zoomed in with globe inactive.
