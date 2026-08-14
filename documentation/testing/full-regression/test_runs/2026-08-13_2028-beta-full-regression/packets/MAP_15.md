# Packet: MAP_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MAP_15.
- In scope: manual Remote map-source selection, theme filtering, persistence, direct provider behavior, and Reset.
- Out of scope: server-wide remote tile mode, covered by MAP_13.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_14.
- Required app/data state: frozen image 1.331 in restored local tile mode with 12 visible tracks.
- Required browser context: signed in at the main map.

## Allowed Mutations

- Allowed: change and reset the current browser's map-source preference.
- Not allowed: change server tile mode or compose state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_15 | Opened Map style, changed Automatic to Remote tiles, kept OSM Dark selected, reloaded, then used Reset map settings and reloaded again. | Remote provider renders without map-proxy requests; OSM themes remain; Swiss themes disappear; Remote persists; Reset restores Auto. | Remote mode rendered 12 tracks over the OSM/CARTO raster base. It offered four OSM themes and no Swiss themes, persisted through reload, and produced no map-proxy request during the Remote interval. Reset restored persisted Automatic mode, OSM Topo Contrast, seven themes, and both Swiss themes. | PASS | [manual Remote](../assets/MAP_15-manual-remote.webp), [reset Auto](../assets/MAP_15-reset-auto.webp), [summary](../assets/MAP_15-manual-remote-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_15-manual-remote.webp](../assets/MAP_15-manual-remote.webp) | Compact Remote-source UI and rendered map evidence. |
| [assets/MAP_15-reset-auto.webp](../assets/MAP_15-reset-auto.webp) | Compact post-reload Automatic selection and restored Swiss themes. |
| [assets/MAP_15-manual-remote-summary.txt](../assets/MAP_15-manual-remote-summary.txt) | Exact selections, theme inventory, persistence, attribution, and server-log interval. |

## Screenshot Evidence

Both screenshots are compact WebP files below 85 KB and show working states.

## Timings

| Step | Timing |
|---|---:|
| Manual source switch and render | < 2 s |
| Reload persistence | < 2 s |
| Reset and second reload | < 2 s |

## Handoff Notes

- Completed: MAP_15.
- Remaining unfinished coverage: TRD_01 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: local server mode; browser map source Automatic; OSM Topo Contrast; 12-track main map; map sheet closed.

