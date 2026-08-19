# Packet: MCT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_03
- In scope: Stop measure/segment tool and clean temporary markers/listeners.
- Out of scope: Reopening the analyzer.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_02.
- Required app/data state: Segment results open with temporary zones.
- Required browser context: Track Details above Segment Analyzer.

## Allowed Mutations

- Allowed: Close details and analyzer; click map once afterward.
- Not allowed: Start another tool.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| MCT_03 | Closed Track Details and Segment Analyzer, audited active/visible UI, then clicked the map. | Temporary markers/listeners are cleaned up. | Segments became inactive, guidance/overlay disappeared, retained sheet moved below viewport, and a later map click created no zone or guidance. | PASS | [assets/MCT_03-stop-cleanup.txt](../assets/MCT_03-stop-cleanup.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_03-stop-cleanup.txt](../assets/MCT_03-stop-cleanup.txt) | Active/visible state and post-stop map-click behavior. |

## Screenshot Evidence

Unavailable under ACC_04. DOM visibility and post-stop interaction provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Close and audit | About 1 s |
| Post-stop map click | Under 1 s |

## Handoff Notes

- Completed: Segment-tool stop and cleanup.
- Remaining unfinished coverage: None for MCT_03.
- Blocked or not applicable: None.
- State left for the next packet: Map-only view centered on Bern; Segment Analyzer inactive.
