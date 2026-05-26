# Packet: TRD_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_06
- In scope: chart hover to mini-map highlight, mini-map hover to chart highlight, and cursor clearing.
- Out of scope: point-click popup, covered by MAP_11.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_05.
- Required app/data state: track #100001 detail open on Graphs tab.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: hover chart and mini-map.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_06 | Hovered the speed chart, then hovered the mini-map/track line, then moved away. | Chart hover highlights matching mini-map point; mini-map hover highlights chart; no stale cursors remain. | Chart hover showed a tooltip/crosshair and red mini-map point. Mini-map hover did not visibly highlight the chart, and the red map cursor remained after moving away. | FAIL | `assets/TRD_06-chart-hover-retry.webp`, `assets/TRD_06-map-hover-line-retry.webp`, `assets/TRD_06-hover-cleared-retry.webp`, `assets/TRD_06-hover-sync.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TRD-01 | P3 | Hover sync is one-way and leaves a stale mini-map cursor. | Open #100001 Graphs tab, hover the speed chart, hover the mini-map line, then move away. | Hover sync works chart-to-map and map-to-chart, and clears after leaving. | Chart-to-map works, but map-to-chart was not visible and the red mini-map marker persisted. | `assets/TRD_06-chart-hover-retry.webp`, `assets/TRD_06-hover-cleared-retry.webp` | Detail inspection can leave confusing stale hover state. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_06-chart-hover-retry.webp | Chart hover showing tooltip/crosshair and mini-map marker. |
| assets/TRD_06-map-hover-line-retry.webp | Mini-map hover attempt. |
| assets/TRD_06-hover-cleared-retry.webp | Cursor state after moving away. |
| assets/TRD_06-hover-sync.txt | Hover state summary. |

## Timings

| Step | Timing |
|---|---:|
| Hover sync check | <2m |

## Handoff Notes

- Completed: TRD_06 terminal FAIL.
- Remaining unfinished coverage: continue with TRD_07.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
