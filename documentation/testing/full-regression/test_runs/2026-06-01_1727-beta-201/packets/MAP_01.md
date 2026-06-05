# Packet: MAP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_01
- In scope: Verify base map and overlays load on first open.
- Out of scope: Track-count correctness; covered by MAP_02.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_09.
- Required app/data state: Eleven imported visible tracks.
- Required browser context: Fresh authenticated desktop browser context.

## Allowed Mutations

- Allowed: Open map and observe network/UI state.
- Not allowed: Change map source or app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_01 | Signed in from a fresh context and waited for the map to load. | Base map and overlays load on first open. | Map loaded with OSM/Mapterhorn attribution, navigation controls, app tool overlays, `11 Tracks`, remote raster responses, track API responses, and local PMTiles map-proxy range responses. | PASS | [assets/MAP_01-base-map-overlays.txt](../assets/MAP_01-base-map-overlays.txt), [assets/MAP_01-base-map-overlays.webp](../assets/MAP_01-base-map-overlays.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_01-base-map-overlays.txt](../assets/MAP_01-base-map-overlays.txt) | Map/attribution/control assertions and relevant map/track network responses. |
| [assets/MAP_01-base-map-overlays.webp](../assets/MAP_01-base-map-overlays.webp) | First-open map screenshot. |

## Screenshot Evidence

**First-open map screenshot.**

![First-open map screenshot.](../assets/MAP_01-base-map-overlays.webp)

## Timings

| Step | Timing |
|---|---:|
| First map load | ~6.5 seconds |

## Handoff Notes

- Completed: MAP_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with MAP_02.
- Blocked or not applicable: None.
- State left for the next packet: App state unchanged.
