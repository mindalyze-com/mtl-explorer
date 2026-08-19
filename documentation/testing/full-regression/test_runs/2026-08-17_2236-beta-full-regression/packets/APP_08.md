# Packet: APP_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_08
- In scope: Layer opacity sliders, base-map dimming, persistence, and Reset map settings.
- Out of scope: Individual map-style selection matrix, covered by APP_06.

## Prerequisites

- Required previous coverage IDs or run packets: APP_07.
- Required app/data state: Authenticated 15-track map.
- Required browser context: Desktop 1280 x 720, Dark theme.

## Allowed Mutations

- Allowed: Change visible opacity sliders, show Heatmap, reload, and use Reset map settings.
- Not allowed: Modify preference storage directly.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_08 | Reset to baseline; set base/GPS/media/points/heatmap opacities to 61/73/64/55/42%; reloaded; then reset and reloaded again. | Opacity and base-map dimming update live, persist, and Reset restores persistent defaults. | Every custom value rendered in the visible sheet and survived reload. Reset restored OSM Topo Contrast, four 100% controls, hidden Heatmap at default 40%, and the 3-of-4 summary after reload. | PASS | [assets/APP_08-opacity-persistence.txt](../assets/APP_08-opacity-persistence.txt); [assets/APP_08-opacity-controls.jpg](../assets/APP_08-opacity-controls.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_08-opacity-persistence.txt](../assets/APP_08-opacity-persistence.txt) | Exact baseline, custom, persisted, and reset values. |
| [assets/APP_08-opacity-controls.jpg](../assets/APP_08-opacity-controls.jpg) | Visible custom layer opacities and dimmed base map. |

## Screenshot Evidence

- The screenshot shows the Dark Your Data sheet with media 64%, points 55%, and Heatmap 42%, while the map behind the sheet is visibly dimmed by the 61% base-map setting.

## Timings

| Step | Timing |
|---|---:|
| Each live slider update | Under 250 ms |
| Each persistence reload settle | About 2.2 seconds |

## Handoff Notes

- Completed: Live opacity updates, base-map dimming, persistence, and reset defaults.
- Remaining unfinished coverage: None for APP_08.
- Blocked or not applicable: None.
- State left for the next packet: Reset defaults, Dark UI, Your Data detail open, 15 tracks.
