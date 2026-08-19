# Packet: MAP_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_13
- In scope: Intentional server remote-raster mode, config schema, three OSM raster themes, attributions, interactivity, and absence of app map-proxy tiles.
- Out of scope: Local-vector fallback covered by MAP_14.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_12 and RUN_SETUP.
- Required app/data state: Disposable Compose installation and healthy beta image.
- Required browser context: Signed-in desktop map with observed-page asset inventory capability.

## Allowed Mutations

- Allowed: Temporarily add a Compose environment override and recreate only the disposable app service.
- Not allowed: Change image override, database, or unrelated services.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_13 | Repeated OSM Dark selection and request capture in remote mode on the matching beta build at desktop and mobile sizes. | OSM Dark loads CARTO raster tiles with matching attribution. | The beta source switched to the CARTO source and loaded 20 desktop and 35 mobile CARTO tile requests. The earlier asset inventory result did not reproduce. | REJECTED | [retest](../assets/MAP_13-retest.txt); [desktop](../assets/MAP_13-rejected-desktop.webp); [mobile](../assets/MAP_13-rejected-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-002 | P1 | OSM Dark in intentional remote-raster mode does not request its configured Carto tiles. | Start with `mtl.map-server.tile-mode=remote`; open Map style; select OSM Dark; zoom once; inspect observed page assets. | `basemaps.cartocdn.com/dark_all/...png` tiles load and CARTO attribution appears. | Radio and attribution update, but no Carto asset is observed and no new tile asset appears; provider URL independently returns HTTP 200. | [assets/MAP_13-remote-raster.txt](../assets/MAP_13-remote-raster.txt) | Remote-raster users can select a dark theme that does not load its declared background provider and may retain a stale or blank base map. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_13-remote-raster.txt](../assets/MAP_13-remote-raster.txt) | Effective mode, config schema, per-theme observed assets/attribution, interactivity, and failure evidence. |
| [assets/MAP_13-compose.remote.yml](../assets/MAP_13-compose.remote.yml) | Exact temporary Compose override. |

## Screenshot Evidence

Unavailable under ACC_04. The selected browser's observed-page asset inventory supplied provider-resource evidence.

## Timings

| Step | Timing |
|---|---:|
| App recreate and HTTP readiness | About 27 s |
| Each theme selection and asset settle | About 1-2 s |
| Dark provider availability probe | Under 1 s |

## Handoff Notes

- Completed: Remote config/schema, three UI theme actions, provider inventories, attribution, interactivity, and no-proxy checks.
- Remaining unfinished coverage: None for MAP_13; FR-002 remains open.
- Blocked or not applicable: Screenshot capture only; asset inventory was sufficient for the functional failure.
- State left for the next packet: App still intentionally configured for remote mode, UI reset to OSM Topo Contrast; override must be removed to test MAP_14.

## Remediation Verification

- Finding FR-002 is `REJECTED`: the matching beta build requested the configured CARTO tiles at both viewports.
- No product change was made. Evidence is linked in the action row.
