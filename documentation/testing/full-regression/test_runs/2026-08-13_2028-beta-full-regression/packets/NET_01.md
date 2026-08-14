# Packet: NET_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: NET_01.
- In scope: installed-PWA offline reload behavior when the active browser context is an installed web app.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_06.
- Required app/data state: signed-in 12-track dataset with Q1 filter active.
- Required browser context: current Codex in-app browser context.

## Allowed Mutations

- Allowed: inspect CSS display-mode media-query results.
- Not allowed: install a PWA or alter server/network state for a criterion restricted to an existing installed context.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_01 | Inspected all relevant `display-mode` media queries in the active target context before attempting offline reload. | Run the offline cached-track/tile test only in installed PWA/web-app mode; classify a normal browser tab as not applicable or not covered. | The active context reported `browser=true` and `standalone=false`, `minimal-ui=false`, and `fullscreen=false`. It is a normal browser tab, so the installed-PWA-only criterion does not apply to this configured run. | NOT APPLICABLE | [display mode](../assets/NET_01-display-mode.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_01-display-mode.txt](../assets/NET_01-display-mode.txt) | Active browser display-mode results and classification. |

## Screenshot Evidence

No screenshot is useful for a media-query capability result.

## Timings

| Step | Timing |
|---|---:|
| Display-mode evaluation | < 0.1 s |

## Handoff Notes

- Completed: NET_01 is terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: NET_02 onward.
- Blocked or not applicable: installed-PWA-only offline reload is not applicable in this normal browser-tab run.
- State left for the next packet: signed in, 390 x 844, Q1 filter baseline.

