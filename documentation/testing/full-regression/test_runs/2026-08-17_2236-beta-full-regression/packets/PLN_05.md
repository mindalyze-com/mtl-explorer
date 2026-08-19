# Packet: PLN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_05
- In scope: Live distance, ascent, duration, and leg statistics during edits.
- Out of scope: Statistical formula validation beyond coherent transitions.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_04.
- Required app/data state: Editable computed route and working history controls.
- Required browser context: Planner Drawing.

## Allowed Mutations

- Allowed: Move/delete/undo/clear/profile edits already used to exercise the bar.
- Not allowed: Leave the route empty.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_05 | Monitored every live statistic across compute, move, delete, undo, clear, restore, and a profile change. | Distance, ascent, and time update as the route changes. | Metrics changed immediately and coherently from 2.93 km/5 m/8m to 710 m/0 m/2m, to zero, and back to 710 m; leg count and elevation state agreed. | PASS | [assets/PLN_05-live-stats.txt](../assets/PLN_05-live-stats.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_05-live-stats.txt](../assets/PLN_05-live-stats.txt) | Exact live-bar values across edits. |

## Screenshot Evidence

Unavailable under ACC_04. Exact live text transitions provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Live-stat transition cycle | About 6 s |

## Handoff Notes

- Completed: Live statistics across all core route edit types.
- Remaining unfinished coverage: None for PLN_05.
- Blocked or not applicable: None.
- State left for the next packet: Hiking selected; 710.00 m one-leg route restored.
