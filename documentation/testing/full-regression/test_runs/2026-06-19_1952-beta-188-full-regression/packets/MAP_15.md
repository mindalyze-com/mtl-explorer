# Packet: MAP_15

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_15
- In scope: Verify manual Map Source Remote override in local-vector deployment, persistence after reload, OSM-only remote themes, no proxy tile requests, and Reset restoring Auto.
- Out of scope: Intentional deployment-wide remote mode, covered by MAP_13.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_14.
- Required app/data state: Temporary MAP_13 remote-mode override removed; normal local-mode config restored.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Restore compose config, restart app service, change in-app Map Source setting, reload, and Reset.
- Not allowed: Change imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_15 | Removed remote-mode compose override, restarted app, verified local config, selected Map Source Remote in the UI, reloaded, ran an isolated no-proxy log check, then clicked Reset. | Remote override uses remote raster tiles without `/api/map-proxy`, OSM raster themes remain selectable, Swiss vector themes are hidden in Remote mode, setting persists after reload, and Reset restores Auto. | Local config was restored; Remote override pressed persisted after reload; theme list narrowed to OSM Topo Light/Light/Gray/Dark with Swiss themes absent; isolated app logs since Remote selection contained no `/api/map-proxy`; Reset restored Auto and Swiss themes. | PASS | [assets/MAP_15-manual-remote-override.txt](../assets/MAP_15-manual-remote-override.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_15-manual-remote-override.txt](../assets/MAP_15-manual-remote-override.txt) | Restore, config, UI source override, persistence, no-proxy, and reset evidence. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct config/DOM/log evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Restore local mode/app readiness | ~1 min |
| Remote override, reload, no-proxy, reset checks | ~4 min |

## Handoff Notes

- Completed: MAP_15.
- Remaining unfinished coverage: TRD_01 onward.
- Blocked or not applicable: none.
- State left for the next packet: Original local/Auto map source restored; dataset has 14 API tracks and 13 visible simplified/map tracks after MAP_09 synthetic imports.
