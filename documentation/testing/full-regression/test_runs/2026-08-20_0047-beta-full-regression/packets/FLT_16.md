# Packet: FLT_16

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_16
- In scope: Map-only legend hiding, Statistics invariance, and reset after global category change.
- Out of scope: First-time Filter guidance, covered by FLT_17.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_15.
- Required app/data state: Tracks by year with 2010+2026 selected.
- Required browser context: Authenticated map, Filter, and Statistics.

## Allowed Mutations

- Allowed: Temporarily hide 2010 and add 2013 to the global selection.
- Not allowed: Leave a temporary legend group hidden.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_16 | Hide 2010 in the legend, inspect Stats, then add 2013 globally. | Legend hide affects map only; global change resets it. | Map changed 7->3 while Stats stayed 7; adding 2013 produced 8 everywhere and restored all legend groups shown. | PASS | [assets/FLT_16-map-hide-vs-global.txt](../assets/FLT_16-map-hide-vs-global.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_16-map-hide-vs-global.txt](../assets/FLT_16-map-hide-vs-global.txt) | Map-visible and Stats counts before and after global selection change. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible pressed states and counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Hide, inspect, change globally, and verify | 4 min |

## Handoff Notes

- Completed: Temporary map visibility and global-selection reset semantics.
- Remaining unfinished coverage: None for FLT_16.
- Blocked or not applicable: None.
- State left for the next packet: Statistics with Tracks by year selecting 2010, 2013, and 2026; eight tracks.
