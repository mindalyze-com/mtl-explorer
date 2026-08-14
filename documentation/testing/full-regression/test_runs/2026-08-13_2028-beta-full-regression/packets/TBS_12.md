# Packet: TBS_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_12.
- In scope: Statistics resolved-set agreement with a geo-drawn filter before and after reload.
- Out of scope: Statistics summary activation, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_11.
- Required app/data state: clean twelve-track Smart Base Filter.
- Required browser context: circle drawing, map, Statistics Overview, Trends, and Tracks.

## Allowed Mutations

- Allowed: draw one circle, reload, clear the circle.
- Not allowed: leave the shape active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_12 | Drew a circle reducing the map to two tracks; checked all Statistics tabs; reloaded and repeated; cleared the shape. | Map, Overview, Trends, and Tracks use the same two-track set before and after fallback ID restoration. | All surfaces reported two before and after reload. Tracks retained 791 km and 14h 37m. Cleanup restored twelve. | PASS | [set agreement](../assets/TBS_12-geo-resolved-set.txt), [reloaded Overview](../assets/TBS_12-geo-stats-reload.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_12-geo-resolved-set.txt](../assets/TBS_12-geo-resolved-set.txt) | Exact cross-view counts before/after reload and cleanup. |
| [assets/TBS_12-geo-stats-reload.webp](../assets/TBS_12-geo-stats-reload.webp) | Reloaded two-track Statistics Overview with matching map count. |

## Screenshot Evidence

The compact WebP shows the reloaded filter-aware Statistics result.

## Timings

| Step | Timing |
|---|---:|
| Geo result update | < 1 s |
| Reload restoration | < 2 s |
| Cleanup | < 1 s |

## Handoff Notes

- Completed: TBS_12 is terminal `PASS`.
- Remaining unfinished coverage: TBS_13 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop Filter open; circle cleared; twelve tracks.
