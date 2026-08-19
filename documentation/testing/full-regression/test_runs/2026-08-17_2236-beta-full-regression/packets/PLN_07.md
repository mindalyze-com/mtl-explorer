# Packet: PLN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_07
- In scope: Save, list, load, and delete a planned route.
- Out of scope: Download/export validation.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_06.
- Required app/data state: Computed 710 m Hiking route.
- Required browser context: Planner Drawing and Load.

## Allowed Mutations

- Allowed: Create and delete one named saved plan.
- Not allowed: Leave the saved record behind after this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_07 | Saved a named/dated plan, listed it, loaded it, then confirmed deletion. | Save/list/load/delete all work. | List metadata matched; load restored the exact route and elevation data; confirmed delete returned the list to its empty state. | PASS | [assets/PLN_07-save-load-delete.txt](../assets/PLN_07-save-load-delete.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_07-save-load-delete.txt](../assets/PLN_07-save-load-delete.txt) | Exact save/list/load/delete lifecycle. |

## Screenshot Evidence

Unavailable under ACC_04. Dialog/list text and restored route values provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Save and list | About 3 s |
| Load and delete | About 3 s |

## Handoff Notes

- Completed: Full saved-plan lifecycle.
- Remaining unfinished coverage: None for PLN_07.
- Blocked or not applicable: None.
- State left for the next packet: Load tab open and saved-plan list empty; loaded route remains in planner state.
