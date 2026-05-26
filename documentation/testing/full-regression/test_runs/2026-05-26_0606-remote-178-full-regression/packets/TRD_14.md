# Packet: TRD_14

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_14
- In scope: Events tab stop/gap display, event selection, mini-map highlight, and deselection.
- Out of scope: changing event detection thresholds.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_02.
- Required app/data state: GPX track #100001 is open and has one detected break.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: select/deselect an event in the detail UI.
- Not allowed: edit track source or event data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_14 | Opened #100001 Events tab, selected Break 1, then clicked it again to clear selection. | Events tab shows detected stops/GPS gaps where present; selecting an event highlights the matching mini-map position and deselects cleanly. | Events showed `1 break`. Selecting Break 1 set the event button pressed and highlighted the matching location on the mini-map; clicking again removed the map highlight and cleared the pressed state. | PASS | `assets/TRD_14-events-before-select.webp`, `assets/TRD_14-event-selected.webp`, `assets/TRD_14-event-deselected.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_14-events-before-select.webp | Events tab showing one detected break. |
| assets/TRD_14-event-selected.webp | Selected break with map highlight. |
| assets/TRD_14-event-deselected.webp | Event deselected with map highlight cleared. |

## Timings

| Step | Timing |
|---|---:|
| Event select/deselect | <2m |

## Handoff Notes

- Completed: TRD_14 terminal PASS.
- Remaining unfinished coverage: continue with FLT_01.
- Blocked or not applicable: none.
- State left for the next packet: detail panel open on #100001 Events tab.
