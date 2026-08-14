# Packet: TRD_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_14.
- In scope: detected break content, selection-to-mini-map highlighting, and deselection.
- Out of scope: tracks without events.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_13.
- Required app/data state: #100000 contains one detected break.
- Required browser context: Track Details Events with mini-map visible.

## Allowed Mutations

- Allowed: select and deselect an event.
- Not allowed: edit event data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_14 | Opened #100000 Events, selected Break 1, inspected mini-map, then selected it again to clear. | Stops/gaps appear; selection highlights matching mini-map position and deselects cleanly. | One 1m 03s break at 235.89 km appeared. Selection placed an orange on-track target near Lausanne; deselection removed it without disturbing the list or map. | PASS | [selected](../assets/TRD_14-event-selected.webp), [cleared](../assets/TRD_14-event-cleared.webp), [selection log](../assets/TRD_14-event-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_14-event-selected.webp](../assets/TRD_14-event-selected.webp) | Selected event card and orange mini-map target. |
| [assets/TRD_14-event-cleared.webp](../assets/TRD_14-event-cleared.webp) | Same event list with mini-map target removed. |
| [assets/TRD_14-event-selection.txt](../assets/TRD_14-event-selection.txt) | Exact event and toggle results. |

## Screenshot Evidence

Both working-state screenshots are compact WebP files below 85 KB.

## Timings

| Step | Timing |
|---|---:|
| Select to highlight | < 0.5 s |
| Deselect to clear | < 0.5 s |

## Handoff Notes

- Completed: TRD_14.
- Remaining unfinished coverage: TRD_15 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: direct #100000 Track Details URL, Events tab, no selected event.

