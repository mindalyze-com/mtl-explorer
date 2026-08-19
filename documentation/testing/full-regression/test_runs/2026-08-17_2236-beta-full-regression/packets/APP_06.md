# Packet: APP_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_06
- In scope: Map-style independence from the Light and Dark application themes.
- Out of scope: Map-style persistence and opacity controls, covered by APP_07 and APP_08.

## Prerequisites

- Required previous coverage IDs or run packets: APP_05.
- Required app/data state: Authenticated root map with 15 tracks.
- Required browser context: Desktop 1280 x 720.

## Allowed Mutations

- Allowed: Select application themes and map styles through visible controls.
- Not allowed: Change map-source mode or inject styles.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_06 | Selected all seven map styles under Dark, repeated under Light, then captured both opposing combinations. | Every map style is selectable with either application theme. | All 14 combinations selected exactly, each radio was checked, and the application theme remained independent. Light UI rendered OSM Dark; Dark UI rendered OSM Light. | PASS | [assets/APP_06-theme-independence.txt](../assets/APP_06-theme-independence.txt); [assets/APP_06-light-ui-dark-map.jpg](../assets/APP_06-light-ui-dark-map.jpg); [assets/APP_06-dark-ui-light-map.jpg](../assets/APP_06-dark-ui-light-map.jpg) |

## Issues

- None new. FR-002 remains limited to the Remote raster OSM Dark mode already recorded by MAP_13.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_06-theme-independence.txt](../assets/APP_06-theme-independence.txt) | Exact themes/styles exercised and per-selection assertions. |
| [assets/APP_06-light-ui-dark-map.jpg](../assets/APP_06-light-ui-dark-map.jpg) | Light application chrome with a selected and rendered OSM Dark map. |
| [assets/APP_06-dark-ui-light-map.jpg](../assets/APP_06-dark-ui-light-map.jpg) | Dark application chrome with a selected and rendered OSM Light map. |

## Screenshot Evidence

- The screenshots show both opposing combinations: Light application chrome with OSM Dark, and Dark application chrome with OSM Light.

## Timings

| Step | Timing |
|---|---:|
| Fourteen visible style selections | Immediate per selection |

## Handoff Notes

- Completed: All listed map styles are selectable under both application themes.
- Remaining unfinished coverage: None for APP_06.
- Blocked or not applicable: None.
- State left for the next packet: Dark application theme with OSM Light selected; Map Style detail open.
