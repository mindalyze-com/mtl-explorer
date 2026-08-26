# Packet: TRD_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_14
- In scope: Detected event display, selection, mini-map position highlight, and deselection.
- Out of scope: Event-detection algorithm internals.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01 and ACC_04.
- Required app/data state: Public track 100000 with one detected STOP event.
- Required browser context: Authenticated Events tab and detail mini-map.

## Allowed Mutations

- Allowed: Select and deselect a detected event.
- Not allowed: Change event data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_14 | Open Events for track 100000, select Break 1, inspect mini-map event state, then deselect. | Stop/gap events render; selection highlights the map position and clears cleanly. | The one break rendered with time/duration/position; selection became pressed and enabled the one-event map control; deselection cleared. Canvas-highlight visual confirmation is blocked by ACC_04. | BLOCKED | [assets/TRD_14-event-selection.txt](../assets/TRD_14-event-selection.txt); [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_14-event-selection.txt](../assets/TRD_14-event-selection.txt) | Event content and selection/deselection states. |
| [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) | Run-wide canvas visual-inspection constraint. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04, so the canvas highlight cannot be confirmed visually.

## Timings

| Step | Timing |
|---|---:|
| Event selection and cleanup | 2 min |

## Handoff Notes

- Completed: Event rendering, metadata, selection state, overlay enablement, and deselection.
- Remaining unfinished coverage: None; this packet is terminal BLOCKED by ACC_04.
- Blocked or not applicable: Canvas highlight visual confirmation only.
- State left for the next packet: Event deselected; track 100000 details open.
