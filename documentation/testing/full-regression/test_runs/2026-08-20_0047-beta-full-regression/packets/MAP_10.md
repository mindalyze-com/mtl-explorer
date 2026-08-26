# Packet: MAP_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_10
- In scope: Close a selected track and verify normal map restoration.
- Out of scope: Deleting the track.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_09.
- Required app/data state: Track #100013 retained in the active result.
- Required browser context: Matching Tracks list over the main map.

## Allowed Mutations

- Allowed: Open one track's details and use the visible Close action.
- Not allowed: Change filter or track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_10 | Open #100013 details, then activate Close. | Selection closes and the map returns to normal. | Track Details and selected ID disappeared; the two-region main map, nine-track control, zoom controls, attribution, and underlying list remained available. | PASS | [assets/MAP_10-close-selection.txt](../assets/MAP_10-close-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_10-close-selection.txt](../assets/MAP_10-close-selection.txt) | Before/after selected identity, panel, and main map state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible before/after state is linked above.

## Timings

| Step | Timing |
|---|---:|
| Close to restored main-map state | 0.5 s observation wait |

## Handoff Notes

- Completed: Selection close and normal map restoration.
- Remaining unfinished coverage: None for MAP_10.
- Blocked or not applicable: None.
- State left for the next packet: Matching Tracks list open; no track selected.
