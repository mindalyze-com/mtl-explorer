# Packet: MAP_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_13
- In scope: Intentional remote-raster mode contract and three configured OSM themes.
- Out of scope: Local-vector runtime fallback, covered by MAP_14.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_12 and RUN_SETUP.
- Required app/data state: Disposable services/data intact; verified image available locally.
- Required browser context: Signed-in in-app browser with nine tracks.

## Allowed Mutations

- Allowed: Temporarily replace only the app container with the same image and `MTL_MAP_SERVER_TILE_MODE=remote`; restore Compose app afterward.
- Not allowed: Change database, source files, image, or persistent Compose configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_13 | Start same image in remote mode; inspect config; select OSM Light, OSM Topo Light, and OSM Dark; pan/zoom; restore original app. | Three remote styles exposed without legacy key; configured providers/attribution load; map stays interactive; no map-proxy tiles. | Contract exposed `light`, `light-topo`, `dark`, no legacy key; all three showed matching attribution with live canvases/nine tracks; temporary-process proxy and browser PMTiles diagnostics were zero; original local app restored on the same image ID. | PASS | [assets/MAP_13-remote-raster.txt](../assets/MAP_13-remote-raster.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_13-remote-raster.txt](../assets/MAP_13-remote-raster.txt) | Mode/image identity, config contract, provider URLs, visible theme results, proxy checks, and restoration. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact config and accessible attribution/interaction states are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Temporary remote-mode startup to HTTP 200 | <30 s |
| Three-theme UI validation | <5 min |
| Original Compose app restoration to HTTP 200 | <30 s |

## Handoff Notes

- Completed: Intentional remote-raster config, all three required styles, interaction, proxy diagnostics, and full restoration.
- Remaining unfinished coverage: None for MAP_13.
- Blocked or not applicable: None.
- State left for the next packet: Original Compose app healthy in local mode on the original verified image; nine-track browser map loaded.
