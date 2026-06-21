# Packet: TRD_14

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_14
- In scope: Events tab for tracks with detected stops/GPS gaps, event selection, mini-map highlight, and deselection.
- Out of scope: Event detection algorithm correctness beyond verifying existing detected event data is surfaced.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_13.
- Required app/data state: At least one imported track with detected events.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Open track details, switch to Events, select and deselect an event.
- Not allowed: Import, delete, reclassify, or edit track metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_14 | Scanned track event data, opened Track #100004, selected Events, clicked the detected break/GPS-gap event, and clicked it again to deselect. | Events tab shows detected stops/GPS gaps where present; selecting an event highlights the matching mini-map position and deselects cleanly. | The Events tab showed one long break/GPS-gap event and the mini-map event overlay count. Row selection and deselection worked via `event-row--selected` and `aria-pressed`, but the canvas-rendered mini-map highlight could not be directly verified because screenshot capture and canvas readback were unavailable. | BLOCKED | [assets/TRD_14-events-selection-blocked.txt](../assets/TRD_14-events-selection-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_14-events-selection-blocked.txt](../assets/TRD_14-events-selection-blocked.txt) | API event scan, Events tab text, selection/deselection state, and mini-map visual verification blocker. |

## Screenshot Evidence

No screenshot asset was captured for this packet; browser screenshot capture timed out, and mini-map canvas readback was unavailable.

## Timings

| Step | Timing |
|---|---:|
| Event discovery and UI verification | ~12 min |

## Handoff Notes

- Completed: TRD_14 terminal as BLOCKED.
- Remaining unfinished coverage: FLT_01 onward.
- Blocked or not applicable: Mini-map highlight pixel/layer verification requires a working screenshot or canvas readback path.
- State left for the next packet: Track data unchanged; event row deselected on Track #100004.
