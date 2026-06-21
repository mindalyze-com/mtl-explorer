# Packet: GLB_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_04
- In scope: Verify zoom limits do not trap the map at edges.
- Out of scope: Manual globe disable semantics.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_03
- Required app/data state: Map controls available; globe mode re-enabled.
- Required browser context: Desktop Chrome context against the remote target.

## Allowed Mutations

- Allowed: Repeated zoom out and zoom in using map controls.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_04 | Repeatedly clicked zoom out, then zoomed back in. | Zoom limits do not trap the map at edges; the map remains controllable and can recover. | Ten zoom-out clicks reached a 2000 km scale with globe active and controls still enabled; four zoom-in clicks returned to a 200 km flat state with 13 tracks visible. | PASS | [assets/GLB_04-zoom-limits-recovered.webp](../assets/GLB_04-zoom-limits-recovered.webp); [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_04-zoom-limits-recovered.webp](../assets/GLB_04-zoom-limits-recovered.webp) | Map after zooming back in from repeated low-zoom interactions. |
| [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) | Zoom-out/zoom-in counts, scale states, and control enabled state. |

## Screenshot Evidence

![Zoom limits recovered](../assets/GLB_04-zoom-limits-recovered.webp)

## Timings

| Step | Timing |
|---|---:|
| Repeated zoom out and zoom back in | 2026-06-20T01:03 CEST |

## Handoff Notes

- Completed: GLB_04 passed; globe mode section complete.
- Remaining unfinished coverage: ADM_01.
- Blocked or not applicable: None.
- State left for the next packet: Queue advances to Admin Tools.
