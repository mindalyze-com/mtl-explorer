# Packet: MAP_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_10
- In scope: Verify closing/deselecting an overlap selection returns the map to normal.
- Out of scope: Opening the selection list itself, covered by MAP_09.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_09.
- Required app/data state: An overlap selection list or selected overlap detail is open.
- Required browser context: desktop map tab.

## Allowed Mutations

- Allowed: Close/deselect an already-open selection.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_10 | Checked the state left by MAP_09. | An open selection can be closed/deselected and the map returns to normal. | MAP_09 could not open the overlap selection list, so there was no selection state to close or deselect. | BLOCKED | [assets/MAP_10-selection-close-blocked.txt](../assets/MAP_10-selection-close-blocked.txt); [assets/MAP_09-overlap-selection-blocked.txt](../assets/MAP_09-overlap-selection-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_10-selection-close-blocked.txt](../assets/MAP_10-selection-close-blocked.txt) | Dependency/blocking explanation for close/deselect coverage. |
| [assets/MAP_09-overlap-selection-blocked.txt](../assets/MAP_09-overlap-selection-blocked.txt) | Upstream overlap selection attempt evidence. |

## Screenshot Evidence

No screenshot asset was captured for this packet; the required selection UI was never opened.

## Timings

| Step | Timing |
|---|---:|
| Dependency/state check | <1 min |

## Handoff Notes

- Completed: MAP_10 as terminal BLOCKED.
- Remaining unfinished coverage: MAP_11 onward.
- Blocked or not applicable: Unblock by first opening the MAP_09 overlap selection list with manual visual testing or instrumented map feature-hit targeting.
- State left for the next packet: Dataset remains 14 API tracks / 13 visible simplified tracks after MAP_09 synthetic imports.
