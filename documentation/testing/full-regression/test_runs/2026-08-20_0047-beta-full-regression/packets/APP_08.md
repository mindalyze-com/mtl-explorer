# Packet: APP_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_08
- In scope: Layer opacity, basemap dimming, persistence, and reset-to-defaults.

## Prerequisites

- Required previous coverage IDs or run packets: APP_07, HMO_01, and HMO_02.
- Required app/data state: OSM Dark selected at start; default three data layers on.
- Required browser context: Map settings.

## Allowed Mutations

- Allowed: Change basemap/GPS opacity, reload, and reset all map settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_08 | Exercised basemap endpoints/dimming, set GPS opacity, reloaded to verify persistence, then reset and reloaded again. | Opacity, dimming, persistence, and reset all behave. | 35% basemap and 42% GPS persisted; reset restored preferred topo, 100% basemap/data, heatmap off, overlays none, and persisted across reload. | PASS | [assets/APP_08-opacity-reset.txt](../assets/APP_08-opacity-reset.txt); [assets/HMO_01-heatmap-controls.txt](../assets/HMO_01-heatmap-controls.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_08-opacity-reset.txt](../assets/APP_08-opacity-reset.txt) | Basemap/layer sequences, persistence, and reset state. |
| [assets/HMO_02-route-overlays.txt](../assets/HMO_02-route-overlays.txt) | All route-overlay opacity/toggle coverage. |

## Screenshot Evidence

Direct slider/status/summary evidence is durable; rendered opacity pixels remain uncaptured under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Each persistence reload | About 0.8 s |
| Reset and verification | About 3 s |

## Handoff Notes

- Completed: Basemap/data opacity, reload persistence, reset, and reset persistence.
- Remaining unfinished coverage: None for APP_08.
- Blocked or not applicable: Durable rendered-pixel screenshots only.
- State left for the next packet: Light UI; default map settings (OSM Topo Contrast, Automatic, 2D, three layers 100%, heatmap off, overlays none).
