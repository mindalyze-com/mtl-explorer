# Packet: MED_32

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_32
- In scope: Independent panel maximize and browser fullscreen, Details/Nearby state retention, navigation/zoom, fullscreen exit, and panel restore.
- Out of scope: Mobile layout.

## Prerequisites

- Required previous coverage IDs or run packets: MED_31.
- Required app/data state: Six-photo viewer.
- Required browser context: Desktop browser with Fullscreen API.

## Allowed Mutations

- Allowed: Viewer window, disclosure, navigation, and image-viewport state.
- Not allowed: Persisted media changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_32 | Exercised panel maximize/restore, state changes, Next/zoom, then activated browser fullscreen twice and inspected the document state. | Panel and browser fullscreen are independent; states and interactions persist; fullscreen can exit without closing viewer. | Panel maximize/restore and all state/navigation/zoom checks passed. The in-app browser exposes no Fullscreen API: both activations left fullscreenElement null and no Exit action, so true-fullscreen state/exit checks are unreachable. | BLOCKED | [assets/MED_32-window-controls.txt](../assets/MED_32-window-controls.txt) |

## Issues

- Browser Fullscreen API unavailable in this selected testing context; no product assertion is made for the blocked branch.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_32-window-controls.txt](../assets/MED_32-window-controls.txt) | Panel transitions, retained states, exact zoom, fullscreen attempts, and API state. |

## Screenshot Evidence

- Live screenshots showed the viewport-filling panel-maximized viewer and the normal still-open viewer after each blocked fullscreen attempt.

## Timings

| Step | Timing |
|---|---:|
| Panel maximize/restore | Under 300 ms each |
| Browser fullscreen attempt settling | 500 ms each |

## Handoff Notes

- Completed: Panel maximize/restore, state retention, navigation, and zoom.
- Remaining unfinished coverage: None for MED_32; true browser-fullscreen children are terminally blocked.
- Blocked or not applicable: Fullscreen entry, in-fullscreen changes, and fullscreen exit.
- State left for the next packet: Viewer normal size at photo 2/6, Details hidden, Nearby collapsed.
