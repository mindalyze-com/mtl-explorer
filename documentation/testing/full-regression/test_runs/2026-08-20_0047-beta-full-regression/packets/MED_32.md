# Packet: MED_32

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_32
- In scope: Viewer panel maximize versus native browser fullscreen, state retention, navigation, zoom, and restore paths.
- Out of scope: Phone layout, covered by MED_31.

## Prerequisites

- Required previous coverage IDs or run packets: MED_31 terminal result.
- Required app/data state: Eight-item activity viewer baseline.
- Required browser context: Authenticated desktop viewer with separate Maximize panel and Enter fullscreen controls.

## Allowed Mutations

- Allowed: Ephemeral viewer mode, media selection, Details, Activity media, and zoom state.
- Not allowed: App/data mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_32 | Tested panel maximize geometry, independent Details/Activity media changes, Next/Previous, zoom/reset, Escape restore, and explicit panel restore; then invoked the adjacent native-fullscreen control and inspected the browser fullscreen state. | Panel maximize fills the app; browser fullscreen fills the browser and clears maximize; controls/states work in both; either fullscreen control or Escape returns to a still-open viewer without state loss. | Panel maximize fully passed at 1049 × 942: it preserved states, allowed both states to change, and retained working navigation/zoom; Escape and Restore panel size returned to the normal still-open viewer without state loss. Native fullscreen could not be entered because the connected in-app browser exposes no usable Fullscreen API (`fullscreenEnabled` unavailable and `fullscreenElement` null), so its required end-user paths remain unexercisable. | BLOCKED | [assets/MED_32-window-modes.txt](../assets/MED_32-window-modes.txt) |

## Issues

- None. Native fullscreen is blocked by the connected browser environment, not recorded as a product finding.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_32-window-modes.txt](../assets/MED_32-window-modes.txt) | Exact panel bounds, state transitions, interaction results, and native-fullscreen capability diagnosis. |

## Screenshot Evidence

Live inspection confirmed the full-viewport panel layout. Durable local screenshot saving remains blocked by ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Panel mode/state transitions | Under 1 s each |
| Native fullscreen attempt | Under 1 s |

## Handoff Notes

- Completed: Every panel-maximize path and exact native-fullscreen capability diagnosis.
- Remaining unfinished coverage: None; MED_32 is terminal BLOCKED.
- Blocked or not applicable: Native browser fullscreen interaction/exit paths cannot run in the connected in-app browser.
- State left for the next packet: Normal still-open viewer at item 1, Details hidden, Activity media collapsed; data baseline unchanged.
