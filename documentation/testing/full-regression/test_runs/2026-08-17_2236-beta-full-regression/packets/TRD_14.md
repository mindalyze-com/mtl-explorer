# Packet: TRD_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_14
- In scope: Detected stop/GPS-gap list, selection, matching mini-map highlight, and clean deselection.
- Out of scope: Event-detection algorithm tuning.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_02 and ACC_04.
- Required app/data state: Track 100004 with one detected low-displacement GPS gap.
- Required browser context: Track Details Events tab and mini-map.

## Allowed Mutations

- Allowed: Select and deselect the event row.
- Not allowed: Edit event data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_14 | Opened Events on track 100004, inspected the GPS-gap event, selected it with keyboard activation, and deselected it. | Event renders; selection highlights the matching mini-map position and deselects without stale highlight. | The 12m25 GPS gap at 1.84-1.86 km rendered. Selection toggled `aria-pressed` false→true and `event-row--selected`; deselection restored false and removed the class. The actual map highlight is WebGL-only and cannot be visually captured because screenshot export is blocked. | BLOCKED | [assets/TRD_14-events.txt](../assets/TRD_14-events.txt), [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_14-events.txt](../assets/TRD_14-events.txt) | Exact event contents, selection/deselection state, and visual-channel constraint. |
| [packets/ACC_04.md](ACC_04.md) | Selected-browser screenshot limitation. |

## Screenshot Evidence

Blocked by ACC_04. This prevents verifying the WebGL-only marker's exact mini-map position and visual removal.

## Timings

| Step | Timing |
|---|---:|
| Event inspection | Under 1 s |
| Select and deselect | Under 1 s each |

## Handoff Notes

- Completed: Event rendering plus accessible selection and clean deselection state.
- Remaining unfinished coverage: None; terminally blocked only on visual proof of the WebGL map highlight.
- Blocked or not applicable: Requires a working screenshot/render-inspection channel for the WebGL canvas.
- State left for the next packet: Track 100004 Events open with the event deselected.
