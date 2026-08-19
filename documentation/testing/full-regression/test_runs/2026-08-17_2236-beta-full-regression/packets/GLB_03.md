# Packet: GLB_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_03
- In scope: Manual globe disable persistence and explicit re-enable.
- Out of scope: Absolute zoom boundaries.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_02.
- Required app/data state: Auto globe enabled and low-zoom globe reachable.
- Required browser context: Signed-in desktop map.

## Allowed Mutations

- Allowed: Globe toggle and reversible zoom camera changes.
- Not allowed: Reload or reset map settings during the check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_03 | Disabled globe, crossed the threshold in/out, then explicitly re-enabled. | Manual disable prevents auto-re-enable until user re-enables. | Flat world persisted through two Zoom in/two Zoom out transitions; second manual toggle restored circular globe. | PASS | [assets/GLB_03-preference.txt](../assets/GLB_03-preference.txt); [assets/GLB_03-disabled-respected.jpg](../assets/GLB_03-disabled-respected.jpg); [assets/GLB_03-reenabled.jpg](../assets/GLB_03-reenabled.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_03-preference.txt](../assets/GLB_03-preference.txt) | Toggle and threshold-crossing sequence. |
| [assets/GLB_03-disabled-respected.jpg](../assets/GLB_03-disabled-respected.jpg) | Flat world after threshold crossing with manual disable active. |
| [assets/GLB_03-reenabled.jpg](../assets/GLB_03-reenabled.jpg) | Circular globe after explicit re-enable. |

## Screenshot Evidence

- Paired images preserve the same low-zoom state with manual flat preference and after explicit sphere restoration.

## Timings

| Step | Timing |
|---|---:|
| Manual projection toggle | Under 700 ms |
| Each threshold zoom | Under 300 ms |

## Handoff Notes

- Completed: Manual disable persistence and re-enable.
- Remaining unfinished coverage: None for GLB_03.
- Blocked or not applicable: None.
- State left for the next packet: Globe explicitly re-enabled at low zoom.
