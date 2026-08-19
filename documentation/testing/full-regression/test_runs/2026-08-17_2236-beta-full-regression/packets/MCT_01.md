# Packet: MCT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_01
- In scope: Place segment start/end zones and list crossing tracks with speed/time/distance.
- Out of scope: Open a result or compare selections.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_11.
- Required app/data state: Bern cluster with synthetic and format tracks.
- Required browser context: Desktop Segment Analyzer.

## Allowed Mutations

- Allowed: Place zones and adjust detection radius.
- Not allowed: Modify tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| MCT_01 | Placed A/B zones, increased radius, and analyzed the crossing set. | Result list shows crossing tracks with speed/time/distance. | Six tracks appeared with names, starts, durations, segment speeds, selection, and speed/time/distance metric controls. | PASS | [assets/MCT_01-crossings.txt](../assets/MCT_01-crossings.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_01-crossings.txt](../assets/MCT_01-crossings.txt) | Zone/radius setup and exact result rows/metrics. |

## Screenshot Evidence

Unavailable under ACC_04. Exact result table DOM provides direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Center/place zones/radius | About 5 s |
| Analyze | About 1 s |

## Handoff Notes

- Completed: Crossing list and metrics.
- Remaining unfinished coverage: None for MCT_01.
- Blocked or not applicable: None.
- State left for the next packet: Segment results open; six rows; five selected.
