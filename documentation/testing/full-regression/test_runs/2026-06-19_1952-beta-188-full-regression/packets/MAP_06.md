# Packet: MAP_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_06
- In scope: Verify fast pan/zoom does not leave stale lines, missing tiles, or runaway loading spinners.
- Out of scope: Visual tile provider-specific checks, covered by MAP_13-MAP_15.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_05.
- Required app/data state: Current 11-track dataset.
- Required browser context: desktop root map tab.

## Allowed Mutations

- Allowed: Pan and zoom the map.
- Not allowed: Change data, preferences, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_06 | Rapidly clicked zoom controls in both directions, dragged/panned the map, waited for settle, then checked DOM and console logs. | Fast pan/zoom leaves the map usable with no stale loading, missing track count, or runaway spinners. | Map settled at 100 km scale with map regions, zoom controls, `11 Tracks`, no loading/retry/spinner text, and no console warnings/errors. | PASS | [assets/MAP_06-pan-zoom-stress.txt](../assets/MAP_06-pan-zoom-stress.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_06-pan-zoom-stress.txt](../assets/MAP_06-pan-zoom-stress.txt) | Pan/zoom actions, settled DOM state, and console status. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct DOM/action evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Pan/zoom stress and settle check | <1 min |

## Handoff Notes

- Completed: MAP_06.
- Remaining unfinished coverage: MAP_07 onward.
- Blocked or not applicable: none.
- State left for the next packet: Root map tab remains at `/mtl/` after pan/zoom stress, 11 tracks visible.
