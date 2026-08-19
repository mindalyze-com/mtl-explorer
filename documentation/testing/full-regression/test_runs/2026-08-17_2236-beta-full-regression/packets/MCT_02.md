# Packet: MCT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_02
- In scope: Open a crossing result in track details/segment view.
- Out of scope: Compare multiple rows.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_01.
- Required app/data state: Six crossing results.
- Required browser context: Segment Analyzer result table.

## Allowed Mutations

- Allowed: Activate one result row.
- Not allowed: Change selection or zones.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| MCT_02 | Activated mtl-segment-b.gpx in the result table. | Result opens the matching track details/segment view. | `/mtl/track/100017` opened Track Details for MTL Synthetic Segment B with matching start/duration and populated Overview. | PASS | [assets/MCT_02-result-open.txt](../assets/MCT_02-result-open.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_02-result-open.txt](../assets/MCT_02-result-open.txt) | Result identity, route, and details match. |

## Screenshot Evidence

Unavailable under ACC_04. Route/ID/name/metrics provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Open and verify result | About 1 s |

## Handoff Notes

- Completed: Result-to-details navigation.
- Remaining unfinished coverage: None for MCT_02.
- Blocked or not applicable: None.
- State left for the next packet: Track 100017 Details open above Segment Analyzer results.
