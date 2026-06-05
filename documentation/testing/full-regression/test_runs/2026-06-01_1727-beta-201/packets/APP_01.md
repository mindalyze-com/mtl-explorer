# Packet: APP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_01
- In scope: Light/dark UI theme switching through Admin Settings.
- Out of scope: Chart-specific recoloring; covered by APP_03.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_07.
- Required app/data state: Authenticated 12-track map.
- Required browser context: Desktop Chromium context.

## Allowed Mutations

- Allowed: Change local UI color scheme.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_01 | Opened Admin Settings and clicked Light then Dark in the Color scheme control. | Whole UI re-themes immediately across text, panels, dialogs, sheets, dropdowns, tooltips, and charts. | `data-theme` changed to `light` after Light click and `dark` after Dark click. Settings/Admin surfaces visibly re-themed. | PASS | [assets/APP_01-theme-switch.txt](../assets/APP_01-theme-switch.txt); [assets/APP_01-light-settings.webp](../assets/APP_01-light-settings.webp); [assets/APP_01-dark-settings.webp](../assets/APP_01-dark-settings.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_01-theme-switch.txt](../assets/APP_01-theme-switch.txt) | Theme attribute after each UI click. |
| [assets/APP_01-light-settings.webp](../assets/APP_01-light-settings.webp) | Settings panel in light mode. |
| [assets/APP_01-dark-settings.webp](../assets/APP_01-dark-settings.webp) | Settings panel in dark mode. |

## Screenshot Evidence

**Settings panel in light mode.**

![Settings panel in light mode.](../assets/APP_01-light-settings.webp)

**Settings panel in dark mode.**

![Settings panel in dark mode.](../assets/APP_01-dark-settings.webp)

## Timings

| Step | Timing |
|---|---:|
| Theme switch check | ~30 s |

## Handoff Notes

- Completed: APP_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with APP_02.
- Blocked or not applicable: None.
- State left for the next packet: Theme was later restored to light during APP cleanup.
