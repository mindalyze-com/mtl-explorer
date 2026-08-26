# Packet: FLT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_03
- In scope: Filter selection, parameter appearance, immediate apply, clear, result label, count, legend, map, and Stats.
- Out of scope: Persistence across reload.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_02.
- Required app/data state: Nine-track Smart Base baseline.
- Required browser context: Authenticated Filter catalog and Statistics.

## Allowed Mutations

- Allowed: Select Activities by keyword, enter Media, then clear it.
- Not allowed: Leave a narrowed result after the packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_03 | Apply Activities by keyword, enter `Media`, inspect all result surfaces, then clear it. | Parameters appear and auto-apply; clearing resets all dependent UI without stale state. | Keyword control appeared; result changed 9 -> 1 immediately across current-result label, map count, legend, and Stats, then returned to 9 when cleared. | PASS | [assets/FLT_03-auto-apply.txt](../assets/FLT_03-auto-apply.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_03-auto-apply.txt](../assets/FLT_03-auto-apply.txt) | Before/edit/clear state across map, legend, and Stats. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible cross-surface states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Select, edit, cross-view check, and clear | 5 min |

## Handoff Notes

- Completed: Filter selection, parameter auto-apply, clear, and cross-view synchronization.
- Remaining unfinished coverage: None for FLT_03.
- Blocked or not applicable: None.
- State left for the next packet: Activities by keyword active with blank keyword and nine tracks.
