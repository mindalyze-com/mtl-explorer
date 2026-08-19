# Packet: TRD_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_05
- In scope: X-axis, range-band, point-count, and graph-height controls.
- Out of scope: Cross-surface hover covered by TRD_06.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_04.
- Required app/data state: Six populated graphs for track 100004.
- Required browser context: Graphs tab at defaults.

## Allowed Mutations

- Allowed: Change graph-only view controls and restore them.
- Not allowed: Persist track mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_05 | Toggled Time/Distance, Range, fewer/more points, and smaller/larger graph height while measuring axes, series, data counts, and boxes; restored defaults. | Controls update charts without layout breakage. | Distance changed axes to km; Range changed series 2↔1; points changed 350↔325 and data count; height changed every chart 240↔230. Six time charts returned with no new errors. | PASS | [assets/TRD_05-graph-controls.txt](../assets/TRD_05-graph-controls.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_05-graph-controls.txt](../assets/TRD_05-graph-controls.txt) | Before/change/restore measurements for all graph controls. |

## Screenshot Evidence

Unavailable under ACC_04; accessible axes/series plus rendered element measurements supplied direct control evidence.

## Timings

| Step | Timing |
|---|---:|
| X-axis and range checks | About 2 s |
| Point and height checks/restoration | About 3 s |

## Handoff Notes

- Completed: All graph controls and default restoration.
- Remaining unfinished coverage: None for TRD_05.
- Blocked or not applicable: None.
- State left for the next packet: Graphs tab restored to Time, Range, 350 points, height 240.
