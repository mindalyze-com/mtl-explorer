# Packet: MAP_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_04
- In scope: Deleted-track removal from all map sources, selection lists, and popups.
- Out of scope: Heatmap density, covered by DEL_03/DEL_05.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01-DEL_04 and MAP_03.
- Required app/data state: Tracks 100002/100003 deleted and freshness synchronized.
- Required browser context: Synchronized signed-in map.

## Allowed Mutations

- Allowed: Read-only map searches, known-point activations, and list checks.
- Not allowed: Restore deleted sources during this check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_04 | Inspect lists, then activate the former Bussang overlap and Joinville Vitry points. | Deleted tracks disappear from map sources, selection lists, and popups. | Both lists omit deleted tracks; Bussang opens only Mosel; Joinville opens no deleted detail/chooser. | PASS | [assets/MAP_04-post-delete-map.txt](../assets/MAP_04-post-delete-map.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_04-post-delete-map.txt](../assets/MAP_04-post-delete-map.txt) | Post-delete list and known-point map interaction results. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; known-point activation and accessible popup/detail states are linked above.

## Timings

| Step | Timing |
|---|---:|
| List checks and two known-point map probes | 5 min |

## Handoff Notes

- Completed: Post-delete map sources, selection lists, and popup behavior.
- Remaining unfinished coverage: None for MAP_04.
- Blocked or not applicable: None.
- State left for the next packet: Main map at Joinville, 5 km scale, seven-track dataset.
