# Packet: MAP_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_15
- In scope: Manual Remote map-source override in a local-mode deployment, provider/no-proxy behavior, theme filtering, persistence, and Reset.
- Out of scope: Server-wide remote mode covered by MAP_13.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_14.
- Required app/data state: Healthy restored `tileMode: local` deployment and 15 visible tracks.
- Required browser context: Map style panel and observed-page asset inventory.

## Allowed Mutations

- Allowed: Change the per-browser Map Source, normal reload, and Reset map settings.
- Not allowed: Change server deployment mode during this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_15 | Switched Map Source from Automatic to Remote, inventoried new assets/themes/attribution, reloaded to verify persistence, then Reset and rechecked local source/themes/assets. | Remote uses configured raster provider without map-proxy, only OSM themes remain, setting persists, and Reset restores Auto. | Remote added 24 OpenTopoMap tiles and zero new proxy assets, showed four OSM themes and no Swiss themes, persisted with 68 provider tiles and zero proxy assets after reload, then Reset restored Automatic, seven themes, and local PMTiles proxy. | PASS | [assets/MAP_15-manual-remote.txt](../assets/MAP_15-manual-remote.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_15-manual-remote.txt](../assets/MAP_15-manual-remote.txt) | Baseline, source switch, isolated assets, themes, persistence, and Reset evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Semantic controls and the browser's observed-page asset inventory supplied complete functional evidence.

## Timings

| Step | Timing |
|---|---:|
| Select Remote and settle provider tiles | About 2 s |
| Reload and verify persistence | About 5 s |
| Reset and verify Automatic/local restoration | About 3 s |

## Handoff Notes

- Completed: Manual Remote provider/no-proxy behavior, theme filtering, persistence, and Reset.
- Remaining unfinished coverage: None for MAP_15.
- Blocked or not applicable: None.
- State left for the next packet: Original local deployment and Automatic map settings restored; Map overview panel open.
