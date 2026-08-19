# Packet: MED_34

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_34
- In scope: Shared viewer dark default, light styling/persistence across map/activity/statistics, independence from app theme, and dark-mode interaction repeat.
- Out of scope: General application-theme coverage, covered by APP.

## Prerequisites

- Required previous coverage IDs or run packets: MED_33 and MED_32.
- Required app/data state: Six-photo map, activity, and statistics viewer entry points.
- Required browser context: Desktop light/dark application themes; browser Fullscreen API for fullscreen child.

## Allowed Mutations

- Allowed: End-user app/viewer theme controls and viewer interactions.
- Not allowed: Direct storage edits.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_34 | Verified dark default, switched viewer light, reloaded/reopened map/activity/statistics entries, switched app dark, then switched viewer dark and repeated interactions. | Viewer defaults dark, light persists only for viewers, every surface adapts/readable, and all dark-mode interactions including browser fullscreen work. | Default dark, light styling, persistence across all three entries, app independence, and dark maximize/Details/Nearby/zoom/navigation passed. True fullscreen remains unreachable because this browser exposes no Fullscreen API. | BLOCKED | [assets/MED_34-viewer-theme.txt](../assets/MED_34-viewer-theme.txt) |

## Issues

- Browser-fullscreen child blocked by the same selected-browser limitation recorded in MED_32.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_34-viewer-theme.txt](../assets/MED_34-viewer-theme.txt) | Theme classes, entry points, persistence, independence, interactions, blocker, and cleanup. |

## Screenshot Evidence

- Live screenshots captured dark default, complete light viewer, light viewer over dark app, and dark app preferences. Exact classes/state are in the linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Viewer theme switch | Under 200 ms |
| Viewer reopen | Under 700 ms |
| App theme switch | Under 250 ms |

## Handoff Notes

- Completed: All shared theme/default/persistence/independence and non-fullscreen interaction checks.
- Remaining unfinished coverage: None for MED_34; fullscreen child is terminally blocked.
- Blocked or not applicable: Browser-fullscreen repeat.
- State left for the next packet: Root map, app light, viewer preference dark, normal three data layers enabled.
