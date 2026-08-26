# Packet: PLN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_05
- In scope: Live distance, ascent, and duration updates while editing.
- Out of scope: Elevation-profile hover linking.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_04.
- Required app/data state: Temporary Planner route and edit history.
- Required browser context: Desktop Planner statistics bar.

## Allowed Mutations

- Allowed: Reuse observed route edits from PLN_02-PLN_04.
- Not allowed: Save the plan.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_05 | Observe the live stats bar across create, insert, move, delete, clear, undo, and redo. | Distance, ascent, and duration track each edit. | All three values changed with route geometry, reset on clear, returned on undo, and reset again on redo. | PASS | [assets/PLN_05-live-stats.txt](../assets/PLN_05-live-stats.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_05-live-stats.txt](../assets/PLN_05-live-stats.txt) | Ordered live-stat values for every edit state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible statistic values are linked above.

## Timings

| Step | Timing |
|---|---:|
| Correlate live values with edits | 1 min |

## Handoff Notes

- Completed: Live Planner statistics.
- Remaining unfinished coverage: None for PLN_05.
- Blocked or not applicable: None.
- State left for the next packet: One-leg 5.13 km route active.
