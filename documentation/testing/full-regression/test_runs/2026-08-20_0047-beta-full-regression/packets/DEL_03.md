# Packet: DEL_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DEL_03
- In scope: Verify deleted tracks disappear across map, browser, filters, selections, heatmap, related lists, and statistics.
- Out of scope: Remaining-track opening, covered by DEL_04.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01 and DEL_02 executed.
- Required app/data state: Two deletion events processed and client freshness synchronized.
- Required browser context: Signed-in desktop context across required views.

## Allowed Mutations

- Allowed: Freshness reload and read-only cross-view checks.
- Not allowed: Claim rendered heatmap/polyline absence without visual access.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_03 | Inspect map count, Review, Stats Tracks/Overview, and Related after reload. | Deleted tracks disappear from every named surface. | Accessible lists/counts/totals pass; rendered heatmap density and polylines cannot be visually inspected under ACC_04. | BLOCKED | [assets/DEL_03-cross-view-removal.txt](../assets/DEL_03-cross-view-removal.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_03-cross-view-removal.txt](../assets/DEL_03-cross-view-removal.txt) | Accessible cross-view removal and exact visual blocker. |

## Screenshot Evidence

Screenshot/canvas evidence is BLOCKED in ACC_04; accessible removal states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Cross-view accessible checks | 4 min |
| Visual heatmap/polyline capability audit | 1 min |

## Handoff Notes

- Completed: Map count, Review, Stats, and Related removal checks.
- Remaining unfinished coverage: None; visual heatmap/polyline subchecks are terminal BLOCKED.
- Blocked or not applicable: Rendered heatmap and polyline absence (ACC_04).
- State left for the next packet: Seven-track synchronized frontend; tracks 100002/100003 absent.
