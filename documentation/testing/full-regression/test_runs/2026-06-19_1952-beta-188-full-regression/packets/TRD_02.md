# Packet: TRD_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_02
- In scope: Verify opening a track loads overview, charts, related-tracks list, event list, mini-map, and quality info.
- Out of scope: Detailed graph-control behavior, covered by TRD_05.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01 and FIT_03.
- Required app/data state: FIT-backed track #100005 indexed successfully.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Reuse completed FIT_03 detail evidence from this run.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_02 | Reused FIT_03 action: opened Activity.fit #100005 and checked Overview, Graphs, Quality, Related, Events, mini-map, and popup surfaces. | Opening a track loads overview, charts, related list, event list, mini-map, and quality info. | Overview, Graphs/charts, Quality `SUCCESS/UNIQUE`, Related prior/current context, Events empty state, mini-map, and point popup all rendered. | PASS | [assets/FIT_03-detail-summary.txt](../assets/FIT_03-detail-summary.txt); [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp); [assets/FIT_03-graphs.webp](../assets/FIT_03-graphs.webp); [assets/FIT_03-quality.webp](../assets/FIT_03-quality.webp); [assets/FIT_03-related.webp](../assets/FIT_03-related.webp); [assets/FIT_03-events.webp](../assets/FIT_03-events.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_03-detail-summary.txt](../assets/FIT_03-detail-summary.txt) | Summary of loaded detail surfaces. |
| [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) | Overview with mini-map. |
| [assets/FIT_03-graphs.webp](../assets/FIT_03-graphs.webp) | Graphs/charts surface. |
| [assets/FIT_03-quality.webp](../assets/FIT_03-quality.webp) | Quality info surface. |
| [assets/FIT_03-related.webp](../assets/FIT_03-related.webp) | Related tracks surface. |
| [assets/FIT_03-events.webp](../assets/FIT_03-events.webp) | Events tab surface. |

## Screenshot Evidence

![FIT detail overview](../assets/FIT_03-overview.webp)

![FIT detail graphs](../assets/FIT_03-graphs.webp)

![FIT detail quality](../assets/FIT_03-quality.webp)

![FIT detail related](../assets/FIT_03-related.webp)

![FIT detail events](../assets/FIT_03-events.webp)

## Timings

| Step | Timing |
|---|---:|
| Detail surface verification | Covered in FIT_03 (~4 min) |

## Handoff Notes

- Completed: TRD_02.
- Remaining unfinished coverage: TRD_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
