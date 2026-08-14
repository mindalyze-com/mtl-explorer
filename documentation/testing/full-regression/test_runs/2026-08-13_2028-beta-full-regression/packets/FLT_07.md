# Packet: FLT_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_07.
- In scope: legend contents, collapse/expand, per-group map visibility, and restore.
- Out of scope: whether map visibility changes statistics, covered by FLT_16.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_06.
- Required app/data state: Activities by keyword view with CYCLING 11 and ON_FOOT 1.
- Required browser context: main map with expanded legend.

## Allowed Mutations

- Allowed: collapse/expand legend and hide/show CYCLING.
- Not allowed: leave a category hidden.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_07 | Collapsed/expanded Map visibility, hid CYCLING, checked live count/state, then showed CYCLING. | Legend matches active filter; collapse/hide updates map immediately. | Legend showed CYCLING 11 and ON_FOOT 1, collapsed and expanded cleanly, and hiding CYCLING immediately changed map count 12/12→1/12 with `1 hidden`; restore returned 12/12. | PASS | [legend controls](../assets/FLT_07-legend-controls.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_07-legend-controls.txt](../assets/FLT_07-legend-controls.txt) | Exact category, pressed, collapsed, hidden, count, and restored states. |

## Screenshot Evidence

Exact accessibility and live-count transitions provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Collapse/expand | < 0.5 s each |
| Hide/show group | < 0.5 s each |

## Handoff Notes

- Completed: FLT_07.
- Remaining unfinished coverage: FLT_08 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: all 12 tracks visible, both legend categories shown, Filter closed.

