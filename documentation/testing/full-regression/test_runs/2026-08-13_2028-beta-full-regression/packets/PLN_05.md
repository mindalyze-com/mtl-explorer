# Packet: PLN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_05.
- In scope: live Planner distance, ascent, descent, duration, and leg updates.
- Out of scope: elevation hover, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_04.
- Required app/data state: Planner route edited through add, insert, move, delete, clear, undo, and redo.
- Required browser context: Planner Drawing stats bar.

## Allowed Mutations

- Allowed: assemble the transitions observed during PLN_02-PLN_04.
- Not allowed: substitute server-side estimates.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_05 | Compared live stats after route creation, insertion, move, delete, clear, undo, and redo. | Distance, ascent, time, and legs update as edits occur. | Every edit updated the visible metrics and undo restored the exact prior route values. | PASS | [transitions](../assets/PLN_05-live-stats.txt), [computed route](../assets/PLN_02-route.webp), [inserted route](../assets/PLN_03-inserted.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_05-live-stats.txt](../assets/PLN_05-live-stats.txt) | Exact live metrics at every edit state. |
| [assets/PLN_02-route.webp](../assets/PLN_02-route.webp) | Initial populated stats bar. |
| [assets/PLN_03-inserted.webp](../assets/PLN_03-inserted.webp) | Updated stats after insertion. |

## Screenshot Evidence

Two compact screenshots show distinct populated route states; the full transition is logged.

## Timings

| Step | Timing |
|---|---:|
| Live update | < 2 s after each edit |

## Handoff Notes

- Completed: PLN_05 is terminal `PASS`.
- Remaining unfinished coverage: PLN_06 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: route cleared; redo active; Road Bike selected.
