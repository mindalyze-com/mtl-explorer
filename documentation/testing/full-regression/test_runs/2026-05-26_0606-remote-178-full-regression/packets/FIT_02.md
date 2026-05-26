# Packet: FIT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_02
- In scope: FIT conversion/import/details/download flow.
- Out of scope: non-FIT format coverage.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05, DEL flow complete, app running.
- Required app/data state: Garmin `Activity.fit` staged before FIT_01.
- Required browser context: desktop signed-in browser; API token used only because in-app browser downloads are unsupported.

## Allowed Mutations

- Allowed: copy FIT to watched folder, use visible UI controls, download files from app endpoints.
- Not allowed: product source edits or workarounds that alter conversion behavior.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_02 | Monitored FIT conversion/import and verified map/list/stat inclusion. | FIT is accepted, indexed successfully, displayed on map, searchable in browser, and included in statistics. | Live watcher detected `Activity.fit`; GPSBabel converted it; import completed as #100005; map showed 4 tracks and track-browser search found `Track 100005`. | PASS | `assets/FIT_02-index-monitor.txt`, `assets/FIT_02-map-after-fit.webp`, `assets/FIT_02-track-list-after-fit.webp`, `assets/FIT_02-track-list-search-fit.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_02-index-monitor.txt | FIT watcher/GPSBabel/SUCCESS lines. |
| assets/FIT_02-map-after-fit.webp | Map after FIT import. |
| assets/FIT_02-track-list-after-fit.webp | Track browser search for FIT track. |
| assets/FIT_02-track-list-search-fit.txt | FIT search row text. |

## Timings

| Step | Timing |
|---|---:|
| FIT step | <1m |

## Handoff Notes

- Completed: FIT_02 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none unless stated.
- State left for the next packet: dataset contains three remaining GPX tracks plus FIT track #100005.
