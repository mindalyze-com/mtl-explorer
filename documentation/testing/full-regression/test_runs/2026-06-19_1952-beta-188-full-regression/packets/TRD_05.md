# Packet: TRD_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_05
- In scope: Verify graph controls work: time/distance x-axis, range band, point-count slider/buttons, and graph-height slider.
- Out of scope: Chart/mini-map hover sync, covered by TRD_06.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_04.
- Required app/data state: FIT-backed Track #100005 openable.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Change transient graph view controls.
- Not allowed: Change track metadata, statistics inclusion, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_05 | Opened Track #100005 Graphs, clicked Distance, toggled Range off, used Load fewer/more chart points, and pressed ArrowRight on graph-height slider. | Controls update charts without layout breakage or blank panels. | Distance became active; Range changed charts from combination/range series to line-only; point slider changed 174→169→174 with data-point counts changing 345/350→320/325→345/350; height slider changed 240→250; charts remained rendered. | PASS | [assets/TRD_05-graph-controls.txt](../assets/TRD_05-graph-controls.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_05-graph-controls.txt](../assets/TRD_05-graph-controls.txt) | Control actions and before/after graph DOM state. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct DOM/control evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Graph control interactions | ~3 min |

## Handoff Notes

- Completed: TRD_05.
- Remaining unfinished coverage: TRD_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: Browser tab remains on Track #100005 Graphs with Range off, Distance active, point slider 174, graph height 250.
