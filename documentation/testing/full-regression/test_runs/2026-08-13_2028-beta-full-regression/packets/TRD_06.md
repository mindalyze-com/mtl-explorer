# Packet: TRD_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_06.
- In scope: bidirectional chart/mini-map hover linking and cursor cleanup.
- Out of scope: point-click popups, covered elsewhere.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_05.
- Required app/data state: #100000 Graphs with populated chart series.
- Required browser context: mini-map and first chart simultaneously visible.

## Allowed Mutations

- Allowed: pointer hover over chart, route, and neutral surface.
- Not allowed: click or persist data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_06 | Hovered the speed chart, left it, hovered an actual mini-map route point, then moved away. | Chart hover highlights the mini-map point; mini-map hover highlights the chart; leaving clears both. | Chart hover produced an orange on-route map marker. Mini-map hover produced a red route marker, chart crosshair, and populated speed tooltip. Moving away hid the crosshair/tooltip and removed map markers. | PASS | [chart hover](../assets/TRD_06-chart-hover.webp), [map hover](../assets/TRD_06-map-hover.webp), [cleared](../assets/TRD_06-hover-cleared.webp), [state log](../assets/TRD_06-cross-highlight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_06-chart-hover.webp](../assets/TRD_06-chart-hover.webp) | Orange map marker caused by chart hover. |
| [assets/TRD_06-map-hover.webp](../assets/TRD_06-map-hover.webp) | Red map point and visible chart crosshair caused by mini-map hover. |
| [assets/TRD_06-hover-cleared.webp](../assets/TRD_06-hover-cleared.webp) | Neutral no-cursor state. |
| [assets/TRD_06-cross-highlight.txt](../assets/TRD_06-cross-highlight.txt) | Exact tooltip, crosshair, and cleared states. |

## Screenshot Evidence

All three working-state screenshots are compact WebP files below 85 KB.

## Timings

| Step | Timing |
|---|---:|
| Each hover transition | < 0.5 s |
| Full bidirectional check | < 1 min |

## Handoff Notes

- Completed: TRD_06.
- Remaining unfinished coverage: TRD_07 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: #100000 Graphs at restored defaults with no hover cursor.

