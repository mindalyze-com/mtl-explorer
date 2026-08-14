# Packet: FLT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_06.
- In scope: applied filter synchronization across visible count, map, legend, and statistics without page reload.
- Out of scope: the parameter auto-apply defect, already recorded in FLT_03.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_05.
- Required app/data state: clean 12-track filter baseline.
- Required browser context: Filter, map legend, and Statistics.

## Allowed Mutations

- Allowed: apply Jura keyword and restore no criteria.
- Not allowed: use a full page reload for synchronization.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_06 | Applied Jura keyword, read Filter/map/legend/Stats, then restored all tracks. | Applied filter updates all required surfaces without a page reload. | Current result/map/legend/Stats all changed coherently to one Jura/Bicycle track and exact Jura totals, then restored to 12. | PASS | [live synchronization](../assets/FLT_06-live-synchronization.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_06-live-synchronization.txt](../assets/FLT_06-live-synchronization.txt) | Exact before/filtered/restored values across all surfaces. |

## Screenshot Evidence

Exact counts and aggregate values provide direct synchronization evidence.

## Timings

| Step | Timing |
|---|---:|
| Applied result update | < 1 s |
| Statistics open | < 1 s |

## Handoff Notes

- Completed: FLT_06.
- Remaining unfinished coverage: FLT_07 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Activities by keyword, no criteria, 12 matching tracks.

