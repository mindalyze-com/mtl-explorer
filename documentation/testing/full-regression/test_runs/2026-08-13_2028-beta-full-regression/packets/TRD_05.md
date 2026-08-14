# Packet: TRD_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_05.
- In scope: x-axis, range band, point-count, and graph-height controls.
- Out of scope: cross-highlighting between charts and the mini-map.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_04.
- Required app/data state: #100000 graph series loaded.
- Required browser context: Track Details Graphs tab.

## Allowed Mutations

- Allowed: change transient graph view controls and restore them.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_05 | Toggled Time/Distance and Range, moved the point-count slider from 350 to 35, moved graph height from 240 to 480, checked charts, then restored defaults. | Each control updates charts without layout breakage. | Axis units/ranges changed, range series toggled, data-point count changed exactly, height changed, and all expected chart panels/navigation remained usable. Restoring defaults returned six time-axis charts. | PASS | [graph controls](../assets/TRD_05-graph-controls.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_05-graph-controls.txt](../assets/TRD_05-graph-controls.txt) | Before, changed, and restored control/series states. |

## Screenshot Evidence

No screenshot is needed for the control-state transitions; exact accessible values and series counts are recorded.

## Timings

| Step | Timing |
|---|---:|
| Each toggle or slider update | < 1 s |
| Full mutation and restore | < 1 min |

## Handoff Notes

- Completed: TRD_05.
- Remaining unfinished coverage: TRD_06 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Graphs open with original Time, Range, 350-point, 240-height settings.

