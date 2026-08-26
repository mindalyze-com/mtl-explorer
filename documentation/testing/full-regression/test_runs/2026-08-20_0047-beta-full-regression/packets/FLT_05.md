# Packet: FLT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_05
- In scope: Circle, rectangle, polygon, undo, cancel, finish, clear, and saved-shape reload.
- Out of scope: Visual anti-aliasing of canvas geometry.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_04.
- Required app/data state: One persisted circle area.
- Required browser context: Authenticated Filter Criteria and main map.

## Allowed Mutations

- Allowed: Create and remove synthetic map areas.
- Not allowed: Leave geo shapes active after the packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_05 | Exercise circle, rectangle, and polygon drawing; use Undo, Cancel, Finish; reload; clear all. | All drawing actions work and saved shapes reappear. | Every action completed; reload restored all three shape types and exact summaries; all three were removed cleanly afterward. | PASS | [assets/FLT_05-geo-drawing.txt](../assets/FLT_05-geo-drawing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_05-geo-drawing.txt](../assets/FLT_05-geo-drawing.txt) | Shape/action/persistence/clear matrix. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible drawing modes and saved summaries are linked above.

## Timings

| Step | Timing |
|---|---:|
| Draw, undo/cancel/finish, reload, and clear | 10 min |

## Handoff Notes

- Completed: All three shape types and every required action.
- Remaining unfinished coverage: None for FLT_05.
- Blocked or not applicable: None.
- State left for the next packet: No geo areas; date and keyword remain active.
