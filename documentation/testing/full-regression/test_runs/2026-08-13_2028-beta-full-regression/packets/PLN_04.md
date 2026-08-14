# Packet: PLN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_04.
- In scope: move, delete, clear, undo, and redo.
- Out of scope: dedicated stats update assessment, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_03.
- Required app/data state: selected waypoint on a five-leg route.
- Required browser context: Planner Drawing.

## Allowed Mutations

- Allowed: move and delete selected waypoint; undo/redo; clear; undo/redo clear.
- Not allowed: save the intermediate route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_04 | Moved and deleted a waypoint; undid/redid deletion; cleared and undid/redid clear. | Every route-edit and history action works. | Move recalculated metrics, delete changed 5→4 legs, undo/redo toggled 5↔4, and clear history toggled 0↔4 legs. | PASS | [history](../assets/PLN_04-edit-history.txt), [working route](../assets/PLN_03-inserted.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_04-edit-history.txt](../assets/PLN_04-edit-history.txt) | Exact leg and metric transitions for all actions. |
| [assets/PLN_03-inserted.webp](../assets/PLN_03-inserted.webp) | Route before the move/delete/history sequence. |

## Screenshot Evidence

The working route screenshot is paired with exact history results.

## Timings

| Step | Timing |
|---|---:|
| Move/delete recompute | < 2 s each |
| Undo/redo recompute | < 2 s each |
| Clear | < 1 s |

## Handoff Notes

- Completed: PLN_04 is terminal `PASS`.
- Remaining unfinished coverage: PLN_05 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: route cleared with redo applied; Road Bike remains selected.
