# Packet: PLN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_07
- In scope: Save, list, load, and delete a planned route.
- Out of scope: GPX export validation.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_06.
- Required app/data state: Computed 5.13 km route.
- Required browser context: Desktop Planner Drawing and Load tabs.

## Allowed Mutations

- Allowed: Create then delete one temporary saved plan.
- Not allowed: Leave test plans behind.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_07 | Save named plan, list it, clear current drawing, load it, then confirm deletion. | Full saved-plan lifecycle works and cleanup leaves no saved plans. | Regression Plan 0047 saved with description/summary, appeared in Load, restored the exact route, and was deleted after confirmation; list returned to empty. | PASS | [assets/PLN_07-saved-plan-lifecycle.txt](../assets/PLN_07-saved-plan-lifecycle.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_07-saved-plan-lifecycle.txt](../assets/PLN_07-saved-plan-lifecycle.txt) | Save/list/load/delete lifecycle evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible dialogs/list/stat states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Save and list | 1 min |
| Clear and load | 1 min |
| Delete and verify empty list | 1 min |

## Handoff Notes

- Completed: Entire saved-plan lifecycle.
- Remaining unfinished coverage: None for PLN_07.
- Blocked or not applicable: None.
- State left for the next packet: Loaded route remains in Drawing memory; saved-plan list is empty.
