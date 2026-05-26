# Packet: GLB_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GLB_03
- In scope: Manual disable of globe mode and re-enable behavior.
- Out of scope: General zoom-limit panning edge cases.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_02
- Required app/data state: Main map visible.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Use zoom controls and globe toggle.
- Not allowed: Change data or persistent app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_03 | Activated globe at low zoom, manually toggled it off, nudged low zoom, then toggled it back on. | Manual disable is respected and globe does not auto-re-enable until the user re-enables it. | After manual disable, the globe control stayed visible but inactive through low-zoom nudges; it became active again only after clicking the toggle a second time. | PASS | `assets/GLB_03-manual-disabled.webp`, `assets/GLB_03-reenabled.webp`, `assets/GLB_03-manual-disable.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/GLB_03-manual-disabled.webp | Screenshot with globe manually disabled at low zoom. |
| assets/GLB_03-reenabled.webp | Screenshot after manually re-enabling globe mode. |
| assets/GLB_03-manual-disable.txt | Step-by-step DOM/class evidence for disable, nudge, and re-enable behavior. |

## Timings

| Step | Timing |
|---|---:|
| Manual disable/nudge/re-enable | <1 min |

## Handoff Notes

- Completed: GLB_03 passed.
- Remaining unfinished coverage: GLB_04 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Map is at low zoom with globe mode active.
