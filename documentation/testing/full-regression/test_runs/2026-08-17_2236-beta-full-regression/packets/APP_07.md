# Packet: APP_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_07
- In scope: Selected map-style persistence across reload.
- Out of scope: Application-theme persistence and layer-opacity persistence.

## Prerequisites

- Required previous coverage IDs or run packets: APP_06.
- Required app/data state: Authenticated 15-track map in Automatic source mode.
- Required browser context: Desktop 1280 x 720, Dark application theme.

## Allowed Mutations

- Allowed: Select OSM Gray and reload the page.
- Not allowed: Change source mode or edit stored preferences directly.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_07 | Selected OSM Gray, reloaded `/mtl/map-settings`, reopened Map Style, and inspected the selected radio and map summary. | Selected map style persists across reload. | Current Map remained OSM Gray; its radio exposed `aria-checked=true`; 15 tracks and OpenStreetMap attribution remained present. | PASS | [assets/APP_07-map-style-persistence.txt](../assets/APP_07-map-style-persistence.txt); [assets/APP_07-style-persisted.jpg](../assets/APP_07-style-persisted.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_07-map-style-persistence.txt](../assets/APP_07-map-style-persistence.txt) | Exact pre/post-reload style, route, checked state, count, and attribution. |
| [assets/APP_07-style-persisted.jpg](../assets/APP_07-style-persisted.jpg) | Reloaded Map Style sheet with OSM Gray selected. |

## Screenshot Evidence

- The screenshot shows OSM Gray selected after the full reload, with the 15-track map still rendered behind the sheet.

## Timings

| Step | Timing |
|---|---:|
| Reload settle before inspection | 2.5 seconds |

## Handoff Notes

- Completed: OSM Gray persisted across reload.
- Remaining unfinished coverage: None for APP_07.
- Blocked or not applicable: None.
- State left for the next packet: Dark theme, OSM Gray, Map Style detail open.
