# Packet: APP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_08
- In scope: Layer opacity sliders, basemap dimming persistence, and reset-to-defaults.
- Out of scope: Pixel-level rendering comparison.

## Prerequisites

- Required previous coverage IDs or run packets: APP_07
- Required app/data state: Signed in with Map panel available.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Change map style and layer opacity preferences, reload, and reset map defaults.
- Not allowed: Change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_08 | Changed Base Map opacity to 45%, reloaded, reset defaults, changed GPS Tracks opacity to 50%, reloaded again, then reset defaults. | Layer opacity sliders, basemap dimming, and reset-to-defaults all behave and persist. | Base Map opacity changed the tile container to `opacity: 0.45` and persisted after reload. GPS Tracks opacity persisted at 50% after reload. Reset restored OSM Topo, Base Map 100%, GPS Tracks 100%, and Heatmap disabled. | PASS | `assets/APP_08-opacity-reset.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/APP_08-opacity-reset.txt | Layer opacity, persistence, and reset observations. |

## Timings

| Step | Timing |
|---|---:|
| Opacity, reload, and reset checks | <4 min |

## Handoff Notes

- Completed: APP_08 passed.
- Remaining unfinished coverage: LOC_01 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in, dark UI theme, OSM Topo selected, Base Map and GPS Tracks at 100%, Photos & Media and Heatmap disabled.
