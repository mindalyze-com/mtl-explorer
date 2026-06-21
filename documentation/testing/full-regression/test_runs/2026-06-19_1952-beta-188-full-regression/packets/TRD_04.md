# Packet: TRD_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_04
- In scope: Verify elevation, speed, distance, and gain charts render with readable values.
- Out of scope: Graph control update behavior, covered by TRD_05.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_03 and FIT_03.
- Required app/data state: FIT-backed detail Graphs tab renderable.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Reuse completed FIT_03 graph evidence from this run.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_04 | Reused FIT_03 Graphs tab evidence for Track #100005. | Elevation, speed, distance, and gain charts render with readable values. | Graphs tab rendered Speed, Elevation, Elevation Gain Rate, Distance over Time, Cumulative Mechanical Energy, and Estimated Power charts with visible chart controls. | PASS | [assets/FIT_03-detail-summary.txt](../assets/FIT_03-detail-summary.txt); [assets/FIT_03-graphs.webp](../assets/FIT_03-graphs.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_03-detail-summary.txt](../assets/FIT_03-detail-summary.txt) | Chart names and graph-tab summary. |
| [assets/FIT_03-graphs.webp](../assets/FIT_03-graphs.webp) | Rendered Graphs tab screenshot. |

## Screenshot Evidence

![FIT detail graphs](../assets/FIT_03-graphs.webp)

## Timings

| Step | Timing |
|---|---:|
| Chart rendering check | Covered in FIT_03 |

## Handoff Notes

- Completed: TRD_04.
- Remaining unfinished coverage: TRD_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
