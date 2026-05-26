# Packet: TRD_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_02
- In scope: track detail overview/charts/related/events/mini-map/quality surfaces.
- Out of scope: graph controls and hover sync.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_03, TRD_01.
- Required app/data state: FIT-backed track imported and openable.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open detail tabs.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_02 | Opened FIT-backed track details and observed overview, graphs, quality, related, events, and mini-map. | Opening a track loads overview, charts, related-tracks list, event list, mini-map, and quality info. | FIT track #100005 rendered overview, graph, quality, related, and events views with mini-map context; GPX track #100001 also rendered overview/mini-map. | PASS | `assets/FIT_03-fit-detail-overview.webp`, `assets/FIT_03-fit-graphs.webp`, `assets/FIT_03-fit-quality.webp`, `assets/FIT_03-fit-related.webp`, `assets/FIT_03-fit-events.webp`, `assets/MAP_11-selected-track-detail.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_03-fit-detail-overview.webp | FIT overview and mini-map. |
| assets/FIT_03-fit-graphs.webp | FIT graphs. |
| assets/FIT_03-fit-quality.webp | FIT quality tab. |
| assets/FIT_03-fit-related.webp | FIT related tab. |
| assets/FIT_03-fit-events.webp | FIT events tab. |
| assets/MAP_11-selected-track-detail.webp | GPX overview/mini-map. |

## Timings

| Step | Timing |
|---|---:|
| Detail surface check | Reused from FIT_03/MAP_11 |

## Handoff Notes

- Completed: TRD_02 terminal PASS.
- Remaining unfinished coverage: continue with TRD_03.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
