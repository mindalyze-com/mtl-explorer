# Packet: APP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_01
- In scope: Immediate whole-interface light/dark repaint.
- Out of scope: Persistence and flash behavior, covered by APP_04/APP_05.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_07.
- Required app/data state: Authenticated 15-track client.
- Required browser context: Admin Preferences, Statistics Trends, dropdown, About sheet.

## Allowed Mutations

- Allowed: Change only the local color-scheme preference.
- Not allowed: Reload during the immediate repaint check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_01 | Recorded light styles, selected Dark without reload, then inspected Preferences, Statistics charts, an open dropdown, and About sheet. | Whole UI re-themes immediately. | Toggle and CSS scheme flipped within 250 ms; body/sheet foreground/background values changed coherently and representative charts, dropdown, sheet, text, and controls all rendered dark. | PASS | [assets/APP_01-theme-switch.txt](../assets/APP_01-theme-switch.txt); [assets/APP_01-light-preferences.jpg](../assets/APP_01-light-preferences.jpg); [assets/APP_01-dark-preferences.jpg](../assets/APP_01-dark-preferences.jpg); [assets/APP_01-dark-charts.jpg](../assets/APP_01-dark-charts.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_01-theme-switch.txt](../assets/APP_01-theme-switch.txt) | Toggle/CSS before-after values and cross-surface observations. |
| [assets/APP_01-light-preferences.jpg](../assets/APP_01-light-preferences.jpg) | Preferences in light mode. |
| [assets/APP_01-dark-preferences.jpg](../assets/APP_01-dark-preferences.jpg) | Same Preferences surface after immediate dark switch. |
| [assets/APP_01-dark-charts.jpg](../assets/APP_01-dark-charts.jpg) | Dark Statistics Trends chart surface. |

## Screenshot Evidence

- The paired Preferences captures show the same content under both schemes;
  the Statistics capture extends the check to data visualization.

## Timings

| Step | Timing |
|---|---:|
| Light to Dark repaint | Under 250 ms |
| Cross-surface checks | About 3 s |

## Handoff Notes

- Completed: Immediate light/dark whole-interface repaint passed.
- Remaining unfinished coverage: None for APP_01.
- Blocked or not applicable: None.
- State left for the next packet: Dark Statistics Trends with About/source overlay open.
