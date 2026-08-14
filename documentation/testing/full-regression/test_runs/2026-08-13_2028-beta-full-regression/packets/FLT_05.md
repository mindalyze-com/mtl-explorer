# Packet: FLT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_05.
- In scope: circle, rectangle, polygon, undo, cancel, finish, persistence, and clear.
- Out of scope: filter-wide result synchronization, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_04.
- Required app/data state: persisted rectangle plus date/text criteria.
- Required browser context: Filter criteria and map drawing toolbar.

## Allowed Mutations

- Allowed: create, redraw/cancel, reload, and clear all three shape types.
- Not allowed: leave test shapes active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_05 | Exercised rectangle, circle, and polygon drawing; used Undo, Cancel, Finish, reload, and Clear shape; restored no-area baseline. | Every geo drawing and lifecycle control works; shapes persist and clear. | Each shape finalized with a saved summary. Circle and polygon Undo changed step/point state, Cancel discarded an in-progress redraw, Finish saved a 3-point polygon, reload restored all three, and Clear removed all shapes. | PASS | [geo drawing log](../assets/FLT_05-geo-drawing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_05-geo-drawing.txt](../assets/FLT_05-geo-drawing.txt) | Per-shape lifecycle actions, saved summaries, persistence, clear, and restored state. |

## Screenshot Evidence

Exact toolbar hints, point counts, and saved shape summaries provide direct lifecycle evidence.

## Timings

| Step | Timing |
|---|---:|
| Each draw interaction | < 1 s per point |
| Reload persistence | < 2 s |
| Clear all | < 1 s |

## Handoff Notes

- Completed: FLT_05.
- Remaining unfinished coverage: FLT_06 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Activities by keyword, no criteria, no geo shapes, 12 matching tracks.

