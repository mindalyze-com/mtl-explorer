# Packet: GLB_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_01
- In scope: Verify automatic globe projection when zooming out far enough.
- Out of scope: Manual globe disable and zoom-limit recovery.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_04
- Required app/data state: Authenticated map view with 13 tracks.
- Required browser context: Desktop Chrome context against the remote target.

## Allowed Mutations

- Allowed: Use map zoom controls.
- Not allowed: Change server data or map source configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_01 | Zoomed in to a flat precondition, then used the map zoom-out control until the globe threshold was crossed. | Globe view engages automatically at low zoom. | After zooming out, the scale read 1000 km, the globe control was visible, `.mtl-globe-active` was present, and the map rendered the globe projection with 13 tracks still visible. | PASS | [assets/GLB_01-auto-globe.webp](../assets/GLB_01-auto-globe.webp); [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_01-auto-globe.webp](../assets/GLB_01-auto-globe.webp) | Globe projection after zooming out. |
| [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) | Globe control active/visible state and scale evidence. |

## Screenshot Evidence

![Auto globe mode](../assets/GLB_01-auto-globe.webp)

## Timings

| Step | Timing |
|---|---:|
| Zoom out and capture globe projection | 2026-06-20T01:03 CEST |

## Handoff Notes

- Completed: GLB_01 passed.
- Remaining unfinished coverage: GLB_02.
- Blocked or not applicable: None.
- State left for the next packet: Shared globe evidence captured.
