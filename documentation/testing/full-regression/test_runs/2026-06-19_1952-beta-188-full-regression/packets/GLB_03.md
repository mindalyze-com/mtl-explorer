# Packet: GLB_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_03
- In scope: Verify manual globe disable is respected and does not auto-re-enable until the user re-enables it.
- Out of scope: General zoom-limit recovery.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_02
- Required app/data state: Map can enter low-zoom globe mode.
- Required browser context: Desktop Chrome context against the remote target.

## Allowed Mutations

- Allowed: Use map zoom and globe controls.
- Not allowed: Change server data or map source configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_03 | Re-entered low-zoom globe mode, clicked `Toggle globe mode`, zoomed in and out while disabled, then clicked the toggle again. | Manual disable is respected and globe does not auto-re-enable until the user re-enables it. | Active state changed true -> false after manual disable, stayed false after zoom in to 500 km and zoom out to 1000 km, then changed back to true only after clicking the globe toggle again. | PASS | [assets/GLB_03-manual-disabled.webp](../assets/GLB_03-manual-disabled.webp); [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_03-manual-disabled.webp](../assets/GLB_03-manual-disabled.webp) | Low-zoom map with globe manually disabled. |
| [assets/GLB-globe-mode-results.txt](../assets/GLB-globe-mode-results.txt) | Active-state sequence before disable, after disable, after zooms, and after re-enable. |

## Screenshot Evidence

![Manual globe disable respected](../assets/GLB_03-manual-disabled.webp)

## Timings

| Step | Timing |
|---|---:|
| Disable globe, zoom while disabled, re-enable | 2026-06-20T01:03 CEST |

## Handoff Notes

- Completed: GLB_03 passed.
- Remaining unfinished coverage: GLB_04.
- Blocked or not applicable: None.
- State left for the next packet: Zoom-limit recovery evidence captured.
