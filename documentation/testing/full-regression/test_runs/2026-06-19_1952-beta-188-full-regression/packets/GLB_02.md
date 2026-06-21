# Packet: GLB_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_02
- In scope: Verify zooming in exits globe mode and returns to flat map view.
- Out of scope: Manual globe disable and zoom-limit recovery.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_01
- Required app/data state: Map is in globe mode at low zoom.
- Required browser context: Desktop Chrome context against the remote target.

## Allowed Mutations

- Allowed: Use map zoom controls.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_02 | Used the map zoom-in control from the active globe state. | Map returns to flat view after zooming in past the exit threshold. | After zooming in, scale read 200 km, the globe control was hidden, active state was false, and console zoom logs showed mercator after the higher zoom state. | PASS | [assets/GLB_02-flat-after-zoom-in.webp](../assets/GLB_02-flat-after-zoom-in.webp); [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_02-flat-after-zoom-in.webp](../assets/GLB_02-flat-after-zoom-in.webp) | Flat map after zooming in from globe mode. |
| [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) | Scale, globe visibility, active state, and representative zoom logs. |

## Screenshot Evidence

![Flat map after zoom in](../assets/GLB_02-flat-after-zoom-in.webp)

## Timings

| Step | Timing |
|---|---:|
| Zoom in and capture flat state | 2026-06-20T01:03 CEST |

## Handoff Notes

- Completed: GLB_02 passed.
- Remaining unfinished coverage: GLB_03.
- Blocked or not applicable: None.
- State left for the next packet: Manual-disable evidence captured.
