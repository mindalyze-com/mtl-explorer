# Packet: FLT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_05
- In scope: Circle, rectangle, and polygon drawing; undo, cancel, finish, persistence, and clear-all.
- Out of scope: Exact geographic match selection covered by FLT_04/FLT_06.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_04.
- Required app/data state: Clean criteria and Activities by keyword active.
- Required browser context: Filter criteria and main MapLibre canvas.

## Allowed Mutations

- Allowed: Draw temporary synthetic shapes in a known map viewport, reload, and reset criteria.
- Not allowed: Leave any area criterion active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_05 | Exercised circle Undo/Cancel, finished rectangle/circle/polygon, reloaded and reopened all three saved shapes, then reset criteria. | All shape types plus undo, cancel, finish, persistence, and clear-all work. | Each instruction/state transition worked. Three areas returned after reload with correct types and edit controls. Reset criteria removed every shape and restored 15 tracks. | PASS | [assets/FLT_05-geo-drawing.txt](../assets/FLT_05-geo-drawing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_05-geo-drawing.txt](../assets/FLT_05-geo-drawing.txt) | Full instruction/state lifecycle and saved/cleared shape inventory. |

## Screenshot Evidence

Unavailable under ACC_04. Exact drawing instructions, enabled/disabled control states, saved shape descriptions, counts, and cleanup state provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Circle undo/cancel | About 3 s |
| Finish three shape types | About 8 s |
| Reload/reopen/clear | About 3 s |

## Handoff Notes

- Completed: All requested geographic drawing lifecycle operations.
- Remaining unfinished coverage: None for FLT_05.
- Blocked or not applicable: None.
- State left for the next packet: Criteria open and reset, no area, all fifteen tracks.
