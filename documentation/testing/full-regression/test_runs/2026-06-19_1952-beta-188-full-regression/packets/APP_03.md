# Packet: APP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_03
- In scope: Verify chart colors update when the UI theme changes without a page reload.
- Out of scope: Detailed chart interaction behavior already covered in stats/detail packets.

## Prerequisites

- Required previous coverage IDs or run packets: APP_01.
- Required app/data state: Stats overview visible in light and dark themes.
- Required browser context: Desktop Chrome context.

## Allowed Mutations

- Allowed: Switch local UI theme.
- Not allowed: Reload the browser before checking chart recolor.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_03 | Captured Stats Overview in light theme, switched to dark theme through Settings, and captured Stats Overview again without page reload. | Charts re-color with the theme switch. | The activity chart remained rendered; chart variables changed from light `--chart-text=#64748b` / `--chart-grid=rgba(0,0,0,.06)` to dark `--chart-text=#94a3b8` / `--chart-grid=rgba(255,255,255,.06)`, and chart labels/panel styling changed visibly. | PASS | [assets/APP_01-light-stats.webp](../assets/APP_01-light-stats.webp); [assets/APP_01-dark-stats.webp](../assets/APP_01-dark-stats.webp); [assets/APP_01_APP_05-theme-results.txt](../assets/APP_01_APP_05-theme-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_01-light-stats.webp](../assets/APP_01-light-stats.webp) | Light chart state. |
| [assets/APP_01-dark-stats.webp](../assets/APP_01-dark-stats.webp) | Dark chart state after switch. |
| [assets/APP_01_APP_05-theme-results.txt](../assets/APP_01_APP_05-theme-results.txt) | Chart theme variable summary. |

## Screenshot Evidence

![Light chart state](../assets/APP_01-light-stats.webp)

![Dark chart state](../assets/APP_01-dark-stats.webp)

## Timings

| Step | Timing |
|---|---:|
| Theme switch and chart check | ~2 min |

## Handoff Notes

- Completed: APP_03 passed.
- Remaining unfinished coverage: APP_04 onward.
- Blocked or not applicable: None.
- State left for the next packet: Dark theme selected.
