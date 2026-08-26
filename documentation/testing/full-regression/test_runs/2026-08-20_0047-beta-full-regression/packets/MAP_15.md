# Packet: MAP_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_15
- In scope: Manual Remote map source override, remote-only themes, persistence, and Reset.
- Out of scope: Changing server tile mode.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_14.
- Required app/data state: Healthy restored local-mode Compose app.
- Required browser context: Signed-in local-vector map with automatic source.

## Allowed Mutations

- Allowed: Change client Map Source and use Reset map settings.
- Not allowed: Change server config or image.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_15 | Select Remote source; inspect themes/attribution; pan/zoom; reload; reopen settings; Reset. | Remote provider loads without local proxy, OSM themes only, setting persists, Reset restores Auto. | CARTO dark raster loaded with four OSM themes/no Swiss themes and no PMTiles/proxy diagnostics; Remote persisted after reload; Reset restored Automatic, preferred OSM Topo Contrast, and seven themes including Swiss. | PASS | [assets/MAP_15-manual-remote.txt](../assets/MAP_15-manual-remote.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_15-manual-remote.txt](../assets/MAP_15-manual-remote.txt) | Baseline, manual override, themes, attribution, interaction, persistence, diagnostics, and Reset. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible settings, attribution, and interaction states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Source switch to settled CARTO map | 1.424 s |
| Reload to persisted remote view | 3.297 s |

## Handoff Notes

- Completed: Manual Remote override, interaction, persistence, source-specific themes, and Reset.
- Remaining unfinished coverage: None for MAP_15.
- Blocked or not applicable: None.
- State left for the next packet: Map settings reset to Automatic, preferred OSM Topo Contrast, route overlays none, nine-track filter; original app healthy.
