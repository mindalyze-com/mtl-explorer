> **RESULT: FAIL - Quick install and cleanup passed, but 23 coverage IDs failed.**

# MTL Explorer Full Regression Report

## Summary

- Target: `178.105.173.254`, remote URL `http://178.105.173.254:18080/mtl/`.
- Run folder: `documentation/testing/full-regression/test_runs/2026-05-26_0606-remote-178-full-regression/`.
- Source: GitHub `main` quick-install compose file from the README.
- Finalization gate: PASS, `168` coverage IDs terminal.
- Cleanup: PASS; compose stack stopped, no `mtl-explorer-*` containers running, port `18080` closed, disposable directory removed.
- Coverage result: 128 PASS, 23 FAIL, 5 BLOCKED, 12 NOT APPLICABLE.
- Issue count: 26 total (14 P2, 11 P3, 1 coverage constraint).

The run is a regression **FAIL** because required coverage ran to completion and cleanup succeeded, but several user-facing failures remain open, including format imports, visible map/list count mismatch, missing controls, freshness dismiss, locale decimal formatting, and broken media recovery.

## Environment And README Facts

- Docker prerequisites on target: Docker Engine `29.5.2`, Docker Compose `v5.1.4`.
- README quick-start app URL: `http://localhost:18080/mtl/`; browser-accessible target URL: `http://178.105.173.254:18080/mtl/`.
- README login used: `mtl` / `change-me`.
- Import folder used: `/root/mtl-full-regression-2026-05-26_0606/mtl-explorer/data/gpx/`.
- Remote quick-install ran over plain HTTP on a non-localhost host, so secure-context-only live geolocation rows were not applicable.

## Setup And Cleanup

| Step | Result | Evidence |
|---|---|---|
| Quick install | PASS: compose stack started in 13s; readiness in 5s; empty map baseline showed 0 tracks. | `assets/RUN_SETUP-quick-install.txt`, `assets/RUN_SETUP-readiness.txt` |
| Finalization gate | PASS: 168 coverage IDs terminal. | `assets/RUN_CLEANUP-cleanup.txt` |
| Cleanup | PASS: compose down removed app, brouter, db, location-search containers and network; directory removed. | `assets/RUN_CLEANUP-cleanup.txt` |

![Login baseline](assets/RUN_SETUP-login.webp)

![Empty map baseline](assets/RUN_SETUP-empty-map.webp)

## Timing Highlights

| Area | Timing |
|---|---:|
| Quick install / compose start | 13s |
| App readiness after compose start | 5s |
| Five-GPX watcher detection | 16s |
| Five-GPX import completion | <1m |
| Delete-two source sync | <1m |
| FIT import/conversion | <1m |
| Cleanup shutdown/removal | ~12s |

## Evidence Highlights

![Imported map after freshness refresh](assets/IMP_05-map-after-freshness-refresh.webp)

![Stats after import](assets/IMP_05-stats-after-import.webp)

![Admin upload UI format limitation](assets/ADM_02-upload-panel.webp)

![Broken media preview failure](assets/MED_05-broken-preview.webp)

![Back/forward navigation failure](assets/SGN_09-back-forward.webp)

## High-Priority Findings

- `FMT-01` (`FMT_01/FMT_02`): KMZ is listed as supported but GPSBabel conversion fails with `Input type 'kmz' not recognized`.
- `FMT-02` (`FMT_01/FMT_02`): GeoJSON import completes without usable trackpoints and GPX export has 0 `<trkpt>`.
- `FMT-04` (`FMT_02`): KML import creates API track data but is not visible/searchable in the track browser.
- `MAP-01` (`MAP_02`): Valid indexed tracks are omitted from the map/list visible count after format imports.
- `TRD-02` (`TRD_10`): Track details do not expose a visible activity type change control.
- `TRD-04` (`TRD_12`): Track details do not expose a visible statistics exclusion toggle.
- `FLT-02` (`FLT_04`): Geo rectangle filter parameter is lost after reload while text/date parameters persist.
- `PLN-01` (`PLN_03`): Dragging an existing planner route leg did not insert a waypoint; route stayed at one leg.
- `MCT-01` (`MCT_04`): Segment comparison mini-map collapses while charts render, preventing visual segment alignment verification.
- `AVR-01` (`AVR_01`): Animate tool sees zero tracks and disables playback while tracks are visible on the map.
- `MED-01` (`MED_05`): Broken media renders as a blank/broken image instead of a recoverable error.
- `ADM-01` (`ADM_02`): Admin upload UI only allows `.gpx` while backend and regression scope include multiple track formats.
- `SYN-01` (`SYN_05`): Data freshness banner Dismiss does not hide the banner.
- `LOC-01` (`LOC_02`): Locale switch does not update decimal-unit formatting on some Stats values.

## All Issues

| ID | Severity | Coverage | Summary | Status |
|---|---|---|---|---|
| `FMT-01` | P2 | FMT_01/FMT_02 | KMZ is listed as supported but GPSBabel conversion fails with `Input type 'kmz' not recognized`. | Open |
| `FMT-02` | P2 | FMT_01/FMT_02 | GeoJSON import completes without usable trackpoints and GPX export has 0 `<trkpt>`. | Open |
| `FMT-03` | P3 | FMT_01 | SBP positive coverage could not run because no sample was found and installed GPSBabel cannot generate SBP. | Coverage constraint |
| `FMT-04` | P2 | FMT_02 | KML import creates API track data but is not visible/searchable in the track browser. | Open |
| `SGN-01` | P3 | SGN_09 | Browser Back/Forward does not navigate between primary views. | Open |
| `MAP-01` | P2 | MAP_02 | Valid indexed tracks are omitted from the map/list visible count after format imports. | Open |
| `MAP-02` | P3 | MAP_11 | Track point clicks did not expose a point metrics popup in the tested detail map. | Open |
| `TRD-01` | P3 | TRD_06 | Hover sync is one-way and leaves a stale mini-map cursor. | Open |
| `TRD-02` | P2 | TRD_10 | Track details do not expose a visible activity type change control. | Open |
| `TRD-03` | P3 | TRD_11 | Energy what-if recalculation control is not exposed in tested track details. | Open |
| `TRD-04` | P2 | TRD_12 | Track details do not expose a visible statistics exclusion toggle. | Open |
| `FLT-01` | P3 | FLT_03 | Filter parameter workflow lacks explicit Apply/Cancel controls; changes live-apply and reset only by clearing values. | Open |
| `FLT-02` | P2 | FLT_04 | Geo rectangle filter parameter is lost after reload while text/date parameters persist. | Open |
| `FLT-03` | P3 | FLT_05 | Polygon draw toolbar reports 3 points while explicit Undo/Finish controls remain disabled. | Open |
| `TBS-01` | P3 | TBS_04 | Track browser does not expose quick-view/preset subset controls beyond search/sort/pagination. | Open |
| `TBS-02` | P3 | TBS_11 | Excluded-highlight count opens an empty track-browser search instead of a useful excluded-track review/manage view. | Open |
| `PLN-01` | P2 | PLN_03 | Dragging an existing planner route leg did not insert a waypoint; route stayed at one leg. | Open |
| `MCT-01` | P2 | MCT_04 | Segment comparison mini-map collapses while charts render, preventing visual segment alignment verification. | Open |
| `AVR-01` | P2 | AVR_01 | Animate tool sees zero tracks and disables playback while tracks are visible on the map. | Open |
| `AVR-02` | P3 | AVR_02 | Virtual Race moves map markers, but racer cards/ranking do not visibly update during playback. | Open |
| `MED-01` | P2 | MED_05 | Broken media renders as a blank/broken image instead of a recoverable error. | Open |
| `ADM-01` | P2 | ADM_02 | Admin upload UI only allows `.gpx` while backend and regression scope include multiple track formats. | Open |
| `SYN-01` | P2 | SYN_05 | Data freshness banner Dismiss does not hide the banner. | Open |
| `APP-01` | P3 | APP_06 | Grayscale base-map style is missing from Map panel despite regression scope listing it. | Open |
| `LOC-01` | P2 | LOC_02 | Locale switch does not update decimal-unit formatting on some Stats values. | Open |
| `MOB-01` | Coverage constraint | MOB_01/MOB_02/MOB_04/MOB_05 | Touch input and gesture automation are unavailable in the current browser surface. | Coverage constraint |

## Blocked And Not Applicable Areas

- BLOCKED: mobile/touch rows requiring a touch-capable browser context (`MOB_01`, `MOB_02`, `MOB_04`, `MOB_05`) and planner mobile/touch drag (`PLN_11`).
- NOT APPLICABLE: demo mode disabled, FIT converter unavailable state not applicable, no Swiss Mobility layer, plain-HTTP live GPS secure-origin rows, installed-PWA offline row, service-worker update row, and BRouter trouble rows that did not occur.
- No coverage IDs remained `NOT STARTED`, `IN PROGRESS`, `PARTIAL`, or `NOT COVERED` at finalization.

## Coverage Matrix

<details open>
<summary>All coverage IDs</summary>

| Coverage ID | Status | Packet | Packet actual result | Evidence |
|---|---|---|---|---|
| `ACC_01` | PASS | `packets/ACC_01.md` | Initialized run-state with every coverage ID from the plan as a queue row and packet target. | run-state.md |
| `ACC_02` | PASS | `packets/ACC_02.md` | Using one packet per child coverage ID; parent/section summaries will not substitute for child statuses. | run-state.md |
| `ACC_03` | PASS | `packets/ACC_03.md` | Final report will be assembled from packet files only and include one row per coverage ID. | packets/*.md; report.md pending final gate |
| `ACC_04` | PASS | `packets/ACC_04.md` | Run evidence policy records WebP screenshots for working user-facing surfaces as well as failures; initial login and empty map screenshots already captured. | assets/RUN_SETUP-login.webp; assets/RUN_SETUP-empty-map.webp |
| `ACC_05` | PASS | `packets/ACC_05.md` | Known constraints are recorded in run-state and constrained packets will use BLOCKED or NOT APPLICABLE rather than being collapsed into parent rows. | run-state.md |
| `DAT_01` | PASS | `packets/DAT_01.md` | All five GPX files have real `trkpt` counts: 1414, 2954, 1688, 1298, and 381. | `assets/DAT_03-source-manifest.txt` |
| `DAT_02` | PASS | `packets/DAT_02.md` | All five GPX files include timestamp counts matching trackpoint counts plus metadata time tags. | `assets/DAT_03-source-manifest.txt` |
| `DAT_03` | PASS | `packets/DAT_03.md` | Source metadata is recorded and imported track IDs/names were added from `IMP_06`. | `assets/DAT_03-source-manifest.txt`, `assets/IMP_06-imported-track-mapping.txt` |
| `DAT_04` | PASS | `packets/DAT_04.md` | All five staged GPX files are from the suggested `gps-touring/sample-gpx` repository. | `assets/DAT_03-source-manifest.txt` |
| `DAT_05` | PASS | `packets/DAT_05.md` | `Activity.fit` from Garmin `fit-javascript-sdk` was staged with SHA-256 recorded. | `assets/DAT_03-source-manifest.txt` |
| `DAT_06` | PASS | `packets/DAT_06.md` | No waypoint-only GPX was counted; FIT will be counted as positive only after `FIT_02`/`FIT_05` conversion and GPX export evidence. | `assets/DAT_03-source-manifest.txt` |
| `IMP_01` | PASS | `packets/IMP_01.md` | Map showed `0 Tracks`; stats showed no matching tracks; Admin jobs were done with 0 totals and routing/location-search ready; freshness token was in sync with `index:0`, `media:0`, `track_geometry:0`, `tracks:0`. | `assets/RUN_SETUP-empty-map.webp`, `assets/IMP_01-stats-baseline.webp`, `assets/IMP_01-admin-baseline.webp`, `assets/IMP_01-jobs-baseline.webp`, `assets/IMP_01-freshness-baseline.webp`, `assets/IMP_01-baseline-summary.t... |
| `IMP_02` | PASS | `packets/IMP_02.md` | All five files were copied to `data/gpx`; sizes and SHA-256 values matched the source manifest. | `assets/IMP_02-copy-gpx.txt` |
| `IMP_03` | PASS | `packets/IMP_03.md` | Live watcher detected all five CREATE events and all five files completed `status=SUCCESS`; manual Rescan GPS was not required. | `assets/IMP_03-index-monitor.txt` |
| `IMP_04` | PASS | `packets/IMP_04.md` | All five source files reached SUCCESS; Admin Jobs showed quiet/idle processing, no visible GPS failures, and data freshness moved out of sync after import. | `assets/IMP_03-index-monitor.txt`, `assets/IMP_04-jobs-after-import.webp`, `assets/IMP_04-freshness-out-of-sync.webp` |
| `IMP_05` | PASS | `packets/IMP_05.md` | Freshness refresh returned the app to the map with `5 Tracks`; Stats overview/table showed 5 tracks and totals; Filter panel opened against the 5-track state. | `assets/IMP_05-freshness-before-refresh.webp`, `assets/IMP_05-map-after-freshness-refresh.webp`, `assets/IMP_05-stats-after-import.webp`, `assets/IMP_05-filter-after-import.webp` |
| `IMP_06` | PASS | `packets/IMP_06.md` | Each of the five files mapped to a visible track ID/name; track-browser searches for Jura, Mosel, Vitry, Voie, and Lannion returned one matching row; map showed 5 tracks; stats highlights/recent activity contained the imported names; filter panel opened with the imported 5-track state. | `assets/IMP_06-imported-track-mapping.txt`, `assets/IMP_06-track-browser-search-results.txt`, `assets/IMP_06-track-list-after-import.webp`, `assets/IMP_05-map-after-freshness-refresh.webp`, `assets/IMP_05-filter-after-i... |
| `IMP_07` | PASS | `packets/IMP_07.md` | CUA viewport clicks opened Lannion #100003, Vitry #100000, Jura #100001 directly; overlapping Mosel/VoieVerte click produced a two-track selection list and each row opened #100002/#100004. No stale or duplicate lines were visible in captured map evidence. | `assets/IMP_07-map-click-results.txt`, `assets/IMP_07-cua-click-lannion.webp`, `assets/IMP_07-map-click-vitry.webp`, `assets/IMP_07-map-click-jura.webp`, `assets/IMP_07-map-selection-mosel.webp`, `assets/IMP_07-map-sele... |
| `IMP_08` | PASS | `packets/IMP_08.md` | Track count increased from 0 to 5; each GPX source produced exactly one imported track ID (100000-100004). | `assets/IMP_01-baseline-summary.txt`, `assets/IMP_06-imported-track-mapping.txt`, `assets/IMP_05-stats-after-import.webp` |
| `IMP_09` | PASS | `packets/IMP_09.md` | Stats increased to 5 tracks, 1,043 km, 23h31m, 4,527 Wh with Bicycle breakdown, rankings/highlights, active periods, and track-browser summary; heatmap rendered density over imported tracks. | `assets/IMP_05-stats-after-import.webp`, `assets/IMP_06-track-list-after-import.webp`, `assets/IMP_09-heatmap-map-visible.webp` |
| `DEL_01` | PASS | `packets/DEL_01.md` | Both files were removed from `data/gpx`; Jura, Lannion, and Mosel remained. | `assets/DEL_01-delete-files.txt` |
| `DEL_02` | PASS | `packets/DEL_02.md` | Watcher/indexer removed track IDs 100000 and 100004 automatically; no manual rescan was needed. | `assets/DEL_02-delete-monitor.txt` |
| `DEL_03` | PASS | `packets/DEL_03.md` | Map and heatmap dropped from 5 to 3 tracks; track list contained only Jura/Mosel/Lannion; searches for Vitry and Voie returned no matching tracks; filter panel opened against the 3-track state. Related-list deletion will also be rechecked in later TRD related coverage. | `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-track-list-after-delete.webp`, `assets/DEL_03-track-list-after-delete.txt`, `assets/DEL_03-search-after-delete.txt`, `assets/DEL_03-filter-after-delete.webp` |
| `DEL_04` | PASS | `packets/DEL_04.md` | Jura #100001 opened from the map and rendered its overview/details after deletion. | `assets/DEL_04-remaining-jura-opens.webp` |
| `DEL_05` | PASS | `packets/DEL_05.md` | Deletion status is based on user-visible map, heatmap, track browser/search, filter context, and remaining detail evidence. | `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-search-after-delete.txt` |
| `FIT_01` | PASS | `packets/FIT_01.md` | `Activity.fit` copied to `data/gpx` with expected SHA-256. | `assets/FIT_01-copy-fit.txt` |
| `FIT_02` | PASS | `packets/FIT_02.md` | Live watcher detected `Activity.fit`; GPSBabel converted it; import completed as #100005; map showed 4 tracks and track-browser search found `Track 100005`. | `assets/FIT_02-index-monitor.txt`, `assets/FIT_02-map-after-fit.webp`, `assets/FIT_02-track-list-after-fit.webp`, `assets/FIT_02-track-list-search-fit.txt` |
| `FIT_03` | PASS | `packets/FIT_03.md` | FIT #100005 opened; overview, graphs, quality, related, and events tabs rendered; mini-map was visible; events tab correctly showed no track events. | `assets/FIT_03-fit-detail-overview.webp`, `assets/FIT_03-fit-graphs.webp`, `assets/FIT_03-fit-quality.webp`, `assets/FIT_03-fit-related.webp`, `assets/FIT_03-fit-events.webp` |
| `FIT_04` | PASS | `packets/FIT_04.md` | Downloaded `Activity.fit` was 94,096 bytes with SHA-256 `949a238e...d591387`, matching the uploaded file. | `assets/FIT_04_05-download-controls.webp`, `assets/FIT_04_05-download-verification.txt`, `assets/FIT_04-download-original.fit` |
| `FIT_05` | PASS | `packets/FIT_05.md` | Downloaded `Activity.gpx` was 479,844 bytes and contained 3,601 `<trkpt>` points. | `assets/FIT_04_05-download-verification.txt`, `assets/FIT_05-download-as-gpx.gpx` |
| `FIT_06` | N/A | `packets/FIT_06.md` | GPSBabel/FIT conversion was available and succeeded, so unavailable-converter error handling was not applicable in this run. | `assets/FIT_02-index-monitor.txt` |
| `FMT_01` | FAIL | `packets/FMT_01.md` | GPX and FIT already passed earlier. Distinct TCX, KML, IGC, GDB, and NMEA samples imported successfully. KMZ failed conversion with `Input type 'kmz' not recognized`. GeoJSON converted but produced `EMPTY_FILE` / 0 trackpoints. SBP remained blocked because no sample was found and installed GPSBabel lists SBP as read-only. | `assets/FMT_01-unique-format-status.txt`, `assets/FMT_01-unique-track-api-summary.txt`, `assets/FMT_01-sbp-source-search.txt`, `assets/FMT_01-track-browser-unique.webp` |
| `FMT_02` | FAIL | `packets/FMT_02.md` | TCX, IGC, GDB, and NMEA were visible in track browser/stat totals and download endpoints returned matching originals plus GPX exports with 180 `trkpt`. KML imported and download endpoints worked, but it was not visible/searchable in the track browser. GeoJSON exported GPX with 0 `trkpt`. KMZ had no imported track because conversion failed. Per-format detail... | `assets/FMT_02-unique-download-verification.txt`, `assets/FMT_01-all-track-browser-after-unique.txt`, `assets/FMT_01-kml-search-probes.txt`, `assets/FMT_02-tcx-detail-overview.txt` |
| `SGN_01` | PASS | `packets/SGN_01.md` | Browser reached `http://178.105.173.254:18080/mtl/login` and showed the sign-in screen. | `assets/SGN_01-login-redirect.webp`, `assets/SGN_01-login-redirect.txt` |
| `SGN_02` | PASS | `packets/SGN_02.md` | Login succeeded, URL returned to `/mtl/`, and the map shell showed 10 tracks plus primary navigation. | `assets/SGN_02-valid-login-map.webp`, `assets/SGN_02-valid-login-map.txt` |
| `SGN_03` | PASS | `packets/SGN_03.md` | The page stayed at `/mtl/login` and displayed `Invalid username or password.` | `assets/SGN_03-wrong-credentials.webp`, `assets/SGN_03-wrong-credentials.txt` |
| `SGN_04` | N/A | `packets/SGN_04.md` | Demo mode is not active (`"demoMode": false`), so a demo credentials banner is not applicable to this quick-install run. | `assets/SGN_04-demo-status.txt`, `assets/SGN_04-login-no-demo-banner.webp` |
| `SGN_05` | PASS | `packets/SGN_05.md` | `Logout` returned to `/mtl/login`; signing in again reached `/mtl/` with 10 tracks visible. | `assets/SGN_05-admin-session-scan.webp`, `assets/SGN_05-logout-login.webp`, `assets/SGN_05-relogin-map.webp`, `assets/SGN_05-logout-relogin.txt` |
| `SGN_06` | PASS | `packets/SGN_06.md` | `LOADING YOUR TRAILS` appeared during startup, then disappeared after 2.6s and the ready map showed 10 tracks. | `assets/SGN_06-splash-loading.webp`, `assets/SGN_06-ready-map.webp`, `assets/SGN_06-splash-summary.txt` |
| `SGN_07` | PASS | `packets/SGN_07.md` | The app showed `Unable to load tracks — no server connection and no cached data available. Retry`; no frozen splash remained. | `assets/SGN_07-storage-startup-api-failure.webp`, `assets/SGN_07-storage-startup-api-failure.txt` |
| `SGN_08` | PASS | `packets/SGN_08.md` | About dialog heading and copy both used `MTL Explorer`. | `assets/SGN_08-about-branding.webp`, `assets/SGN_08-about-branding.txt` |
| `SGN_09` | FAIL | `packets/SGN_09.md` | Stats/Admin did not update browser history; Back and Forward left the user on Admin. Console captured two 401 resource errors during the pass. | `assets/SGN_09-back-forward.webp`, `assets/SGN_09-back-forward.txt` |
| `MAP_01` | PASS | `packets/MAP_01.md` | The first ready map rendered map controls, scale, primary navigation, and 10 visible tracks without a blank-map state. | `assets/SGN_02-valid-login-map.webp`, `assets/SGN_02-valid-login-map.txt` |
| `MAP_02` | FAIL | `packets/MAP_02.md` | UI showed 10 tracks, but the installed-app API listed 14 tracks with point data after imports, including valid TCX/KML/GDB entries not represented in the visible count/list. | `assets/SGN_02-valid-login-map.webp`, `assets/MAP_02-api-valid-track-count.txt`, `assets/FMT_01-all-track-browser-after-unique.txt` |
| `MAP_03` | PASS | `packets/MAP_03.md` | Freshness reload after GPX import updated the map from baseline to imported data; later hard reload after format imports showed the expanded 10-track visible dataset. | `assets/IMP_05-map-after-freshness-refresh.webp`, `assets/FMT_01-all-track-browser-after-unique.webp`, `assets/FMT_01-unique-format-status.txt` |
| `MAP_04` | PASS | `packets/MAP_04.md` | Map/heatmap dropped to 3 GPX tracks at that stage; searches for Vitry and Voie returned no matches; remaining tracks still opened. | `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-search-after-delete.txt`, `assets/DEL_04-remaining-jura-opens.webp` |
| `MAP_05` | PASS | `packets/MAP_05.md` | Zoomed/clicked imported tracks rendered continuous line geometry and opened selections/details without stale duplicate lines. | `assets/IMP_07-zoom-lannion.webp`, `assets/IMP_07-map-click-jura.webp`, `assets/IMP_07-map-click-results.txt` |
| `MAP_06` | PASS | `packets/MAP_06.md` | Map remained usable with 10-track overlay and no visible loading/spinner text. Request log showed tile/API aborts from rapid movement but no frozen UI. | `assets/MAP_06-fast-pan-zoom.webp`, `assets/MAP_06-fast-pan-zoom.txt` |
| `MAP_07` | N/A | `packets/MAP_07.md` | No direction-arrow setting was exposed in config or Settings; the conditional check is not applicable in this run. | `assets/MAP_07-direction-arrow-config.txt`, `assets/MAP_07-settings-scan.txt`, `assets/MAP_07-settings-scan.webp` |
| `MAP_08` | PASS | `packets/MAP_08.md` | Lannion, Vitry, and Jura single-track clicks opened their detail/selection state without stale geometry. | `assets/IMP_07-cua-click-lannion.webp`, `assets/IMP_07-map-click-vitry.webp`, `assets/IMP_07-map-click-jura.webp`, `assets/IMP_07-map-click-results.txt` |
| `MAP_09` | PASS | `packets/MAP_09.md` | A two-track selection list appeared; selecting Mosel opened #100002 and selecting VoieVerte opened #100004. | `assets/IMP_07-map-selection-mosel.webp`, `assets/IMP_07-map-selection-voieverte.webp`, `assets/IMP_07-map-click-results.txt` |
| `MAP_10` | PASS | `packets/MAP_10.md` | An 8-track selection list opened; after Close, the selection text disappeared and the normal 10-track map state remained. | `assets/MAP_10-current-selection-open.webp`, `assets/MAP_10-current-selection-closed.webp`, `assets/MAP_10-current-selection-close.txt` |
| `MAP_11` | FAIL | `packets/MAP_11.md` | Track details and aggregate metrics were visible, but no point popup appeared after multiple line/point clicks. | `assets/MAP_11-selected-track-detail.webp`, `assets/MAP_11-point-popup.webp`, `assets/MAP_11-point-popup-retry.webp`, `assets/MAP_11-point-popup-retry.txt` |
| `MAP_12` | N/A | `packets/MAP_12.md` | No Swiss Mobility route layer/API was exposed in this quick-install configuration; the conditional popup check is not applicable. | `assets/MAP_12-swiss-config-probe.txt` |
| `TRD_01` | PASS | `packets/TRD_01.md` | GPX-backed track #100001 (`JuraRoute72011.gpx`) opened from the map; FIT-backed track #100005 (`Activity.fit`) opened from track browser/detail flow. | `assets/MAP_11-selected-track-detail.webp`, `assets/FIT_03-fit-detail-overview.webp`, `assets/IMP_06-imported-track-mapping.txt`, `assets/FIT_02-track-list-search-fit.txt` |
| `TRD_02` | PASS | `packets/TRD_02.md` | FIT track #100005 rendered overview, graph, quality, related, and events views with mini-map context; GPX track #100001 also rendered overview/mini-map. | `assets/FIT_03-fit-detail-overview.webp`, `assets/FIT_03-fit-graphs.webp`, `assets/FIT_03-fit-quality.webp`, `assets/FIT_03-fit-related.webp`, `assets/FIT_03-fit-events.webp`, `assets/MAP_11-selected-track-detail.webp` |
| `TRD_03` | PASS | `packets/TRD_03.md` | Each tab rendered content and remained usable; no blank tab state was captured. | `assets/FIT_03-fit-detail-overview.webp`, `assets/FIT_03-fit-graphs.webp`, `assets/FIT_03-fit-quality.webp`, `assets/FIT_03-fit-related.webp`, `assets/FIT_03-fit-events.webp` |
| `TRD_04` | PASS | `packets/TRD_04.md` | Graphs tab rendered multiple chart panels with track metric axes/values instead of blank space. | `assets/FIT_03-fit-graphs.webp` |
| `TRD_05` | PASS | `packets/TRD_05.md` | Distance became active, Range became inactive, point count increased from 350 to 375, and graph height control moved while charts remained rendered. | `assets/TRD_05-graphs-controls-top-visible.webp`, `assets/TRD_05-graphs-controls-after-visible.webp`, `assets/TRD_05-graphs-controls-visible.txt` |
| `TRD_06` | FAIL | `packets/TRD_06.md` | Chart hover showed a tooltip/crosshair and red mini-map point. Mini-map hover did not visibly highlight the chart, and the red map cursor remained after moving away. | `assets/TRD_06-chart-hover-retry.webp`, `assets/TRD_06-map-hover-line-retry.webp`, `assets/TRD_06-hover-cleared-retry.webp`, `assets/TRD_06-hover-sync.txt` |
| `TRD_07` | PASS | `packets/TRD_07.md` | Track browser/stat rows displayed line previews; related-track rows and overlap selection list displayed miniature track shapes; filter panel was captured with track geometry visible behind it. | `assets/FIT_02-track-list-after-fit.webp`, `assets/DEL_03-filter-after-delete.webp`, `assets/FIT_03-fit-related.webp`, `assets/MAP_10-current-selection-open.webp` |
| `TRD_08` | PASS | `packets/TRD_08.md` | GPX original returned HTTP 200, 199,962 bytes, 1,414 trackpoints, and matching SHA-256; FIT original previously matched uploaded checksum. | `assets/TRD_08-download-original-verification.txt`, `assets/FIT_04_05-download-verification.txt`, `assets/TRD_08-gpx-original-download.gpx` |
| `TRD_09` | PASS | `packets/TRD_09.md` | GPX #100001 export returned HTTP 200 and 1,414 `trkpt`; FIT export returned 3,601 `trkpt`; successful non-GPX exports returned 180 `trkpt`. GeoJSON remains a known FMT failure with 0 `trkpt`. | `assets/TRD_09-download-as-gpx-verification.txt`, `assets/FIT_04_05-download-verification.txt`, `assets/FMT_02-unique-download-verification.txt` |
| `TRD_10` | FAIL | `packets/TRD_10.md` | No visible activity type edit control was exposed in the tested detail view; clicking the `Bicycle` badge did not open an editor. | `assets/TRD_10-activity-control-scan.webp`, `assets/TRD_10-activity-badge-click.webp` |
| `TRD_11` | FAIL | `packets/TRD_11.md` | The Overview showed energy values and mass used, but the About dialog only contained explanatory text; no rider-weight/what-if input was exposed. | `assets/TRD_11-energy-about-open.webp` |
| `TRD_12` | FAIL | `packets/TRD_12.md` | No visible exclusion toggle was exposed in the tested detail UI, so the exclude/re-include workflow could not be performed from the frontend. | `assets/TRD_12-exclusion-control-scan.webp`, `assets/TRD_12-quality-exclusion-scan.webp` |
| `TRD_13` | PASS | `packets/TRD_13.md` | The Related tab showed Next Tracks and Duplicates for #100001. Clicking `Track #100005` navigated the detail panel to #100005 and refreshed related context. | `assets/TRD_13-related-fullscreen.webp`, `assets/TRD_13-related-navigated.webp` |
| `TRD_14` | PASS | `packets/TRD_14.md` | Events showed `1 break`. Selecting Break 1 set the event button pressed and highlighted the matching location on the mini-map; clicking again removed the map highlight and cleared the pressed state. | `assets/TRD_14-events-before-select.webp`, `assets/TRD_14-event-selected.webp`, `assets/TRD_14-event-deselected.webp` |
| `FLT_01` | PASS | `packets/FLT_01.md` | Reopening Filter kept filtering On, showed the selected Smart Base Filter, and displayed live preview status with 10 matching tracks and 2 categories. | `assets/FLT_01-filter-open-initial.webp`, `assets/FLT_01-filter-reopened-active.webp` |
| `FLT_02` | PASS | `packets/FLT_02.md` | Catalog showed 18 filters grouped as Core, Activity, Date & Time, Performance, and Quality. Searching `year` narrowed results to Date & Time matches; clearing search and selecting Activity showed its four filters. | `assets/FLT_02-filter-catalog.webp`, `assets/FLT_02-filter-search-year.webp`, `assets/FLT_02-filter-group-activity-cleared.webp` |
| `FLT_03` | FAIL | `packets/FLT_03.md` | Selecting the filter revealed a `Keyword` parameter. Entering `Jura` live-applied the filter and reduced the map/live preview to 1/10 tracks; clearing the field reset the preview to 10/10. No explicit Apply or Cancel controls were exposed in the tested UI. | `assets/FLT_03-keyword-params.webp`, `assets/FLT_03-keyword-applied.webp`, `assets/FLT_03-keyword-reset-attempt.webp` |
| `FLT_04` | FAIL | `packets/FLT_04.md` | The text keyword and date parameter persisted and re-applied after reload. The rectangle geo parameter was visible before reload but disappeared afterward; Base scope showed only 1 active parameter and the Area section returned to empty Draw buttons. | `assets/FLT_04-date-text-geo-set.webp`, `assets/FLT_04-after-geo-reload-map.webp`, `assets/FLT_04-after-geo-reload-expanded.webp` |
| `FLT_05` | FAIL | `packets/FLT_05.md` | Cancel, circle draw, rectangle draw, polygon double-click finish, and clear worked. The explicit Polygon Undo and Finish buttons were disabled while the tool reported `3 points`, and the rectangle geo parameter did not reappear after reload. | `assets/FLT_05-circle-start.webp`, `assets/FLT_05-circle-drawn.webp`, `assets/FLT_05-polygon-before-undo.webp`, `assets/FLT_05-polygon-finish-attempt.webp`, `assets/FLT_05-shapes-cleared.webp`, `assets/FLT_04-after-geo-... |
| `FLT_06` | PASS | `packets/FLT_06.md` | The map count and legend changed immediately to 1/10 with one Bicycle category; Stats showed `Showing 1 of 10 tracks`. Clearing the keyword changed the map/legend to 10/10 with Bicycle and Walking categories, and Stats updated to 10 tracks and all-track totals without reload. | `assets/FLT_06-date-cleared-map.webp`, `assets/FLT_06-stats-filtered.webp`, `assets/FLT_06-keyword-cleared-map.webp`, `assets/FLT_06-stats-all-after-clear-retry.webp` |
| `FLT_07` | PASS | `packets/FLT_07.md` | Legend showed Bicycle=9 and Walking=1. Hiding Bicycle immediately changed the map count to 1/10 and changed the Bicycle visibility icon. Collapsing hid the group list; expanding/restoring Bicycle returned count to 10/10. | `assets/FLT_07-legend-before.webp`, `assets/FLT_07-bicycle-hidden.webp`, `assets/FLT_07-legend-collapsed.webp`, `assets/FLT_07-legend-restored.webp` |
| `FLT_08` | PASS | `packets/FLT_08.md` | The map returned to `10 Tracks`, showed a `Showing all tracks` alert, removed the active legend, disabled Colors/SQL tabs, and displayed the `Filtering is off` panel. | `assets/FLT_08-filter-before-disable.webp`, `assets/FLT_08-filter-disabled-map.webp` |
| `TBS_01` | PASS | `packets/TBS_01.md` | Tracks tab listed 10 tracks with Start, Track, Activity, Distance, Duration, Avg km/h, Energy, Exploration, Imported, summary totals, sort buttons, and pagination. | `assets/TBS_01-track-browser-list.webp` |
| `TBS_02` | PASS | `packets/TBS_02.md` | Each representative term returned matching visible rows, including FIT source-file/path search returning Track #100005. Search was cleared afterward. | `assets/TBS_02-search-results.txt`, `assets/TBS_02-file-search.webp` |
| `TBS_03` | PASS | `packets/TBS_03.md` | Each sort control produced a distinct expected first row for the selected sort. Summary stayed at 10 tracks for all-track sorts and changed to `1 of 10 tracks` for the Walking subset. | `assets/TBS_03-sort-results.txt`, `assets/TBS_03-walking-summary.webp` |
| `TBS_04` | FAIL | `packets/TBS_04.md` | The tested Tracks tab exposed search, sort controls, table columns, summary, and pagination, but no quick-view/preset subset buttons were visible. | `assets/TBS_01-track-browser-list.webp`, `assets/TBS_03-walking-summary.webp` |
| `TBS_05` | PASS | `packets/TBS_05.md` | The detail sheet opened for `#100005`, showing the FIT-backed overview with Activity.fit, distance, duration, ascent, and download buttons. | `assets/TBS_05-row-opens-detail.webp` |
| `TBS_06` | PASS | `packets/TBS_06.md` | Overview showed 10 tracks, 1,262 km, 1d 03h duration, 7,052 Wh, Bicycle/Walking breakdown, highlight rankings, recent activity, most active day/week/month/weekday, milestones, and overall date range. | `assets/TBS_06-overview-top.webp`, `assets/TBS_06-overview-lower.webp` |
| `TBS_07` | PASS | `packets/TBS_07.md` | Empty baseline showed no imported tracks; filtered stats showed `Showing 1 of 10 tracks` with Jura-only totals; all-track overview showed 10 tracks, 1,262 km, 1d 03h, activity breakdown, highlights, and milestones. | `assets/IMP_01-stats-baseline.webp`, `assets/FLT_06-stats-filtered.webp`, `assets/TBS_06-overview-top.webp` |
| `TBS_08` | PASS | `packets/TBS_08.md` | Five-GPX import increased stats to 5 tracks, 1,043 km, 23h31m, with rankings, period summaries, browser summary, and heatmap density. Deleting Vitry and VoieVerte dropped visible map/heatmap/list state to 3 tracks and searches for the deleted names returned no matching tracks. | `assets/IMP_05-stats-after-import.webp`, `assets/IMP_06-track-list-after-import.webp`, `assets/DEL_03-map-after-delete.webp`, `assets/DEL_03-track-list-after-delete.webp`, `assets/DEL_03-search-after-delete.txt` |
| `TBS_09` | PASS | `packets/TBS_09.md` | Each selected mode updated the active combobox label, kept the 10-track/1,262 km/1d 03h totals, and rendered duration/distance bar charts without blank panels or layout errors. | `assets/TBS_09-monthly.webp`, `assets/TBS_09-weekly.webp`, `assets/TBS_09-daily.webp` |
| `TBS_10` | PASS | `packets/TBS_10.md` | The clicked highlight became active and opened a ranked `Longest track` drilldown list headed by Moselradweg, with matching ranked tracks and per-track action rows. | `assets/TBS_10-highlight-before.webp`, `assets/TBS_10-highlight-drilldown.webp` |
| `TBS_11` | PASS | `packets/TBS_11.md` | The drilldown listed ranked tracks with Moselradweg first; selecting Moselradweg opened Track Details `#100002`; excluding it showed `1 track excluded`; restoring the API state and reloading returned Moselradweg to the highlight with no excluded-count badge. | `assets/TBS_11-drilldown-list.webp`, `assets/TBS_11-selected-track-opened.webp`, `assets/TBS_11-exclusion-dialog.webp`, `assets/TBS_11-excluded-count.webp`, `assets/TBS_11-highlight-restore.txt` |
| `PLN_01` | PASS | `packets/PLN_01.md` | Planner opened in Drawing mode, showed `BRouter status: ready`, exposed Hiking/Road Bike/Mountain Hiking/Car profile options, and selected Road Bike. | `assets/PLN_01-planner-open.webp`, `assets/PLN_01-road-bike-selected.webp` |
| `PLN_02` | PASS | `packets/PLN_02.md` | After zooming to 10 km scale, two map clicks enabled Undo/Clear/Save, produced 1 leg, 1.93 km distance, 5 m descent, and 4m duration. | `assets/PLN_02-zoomed-under-span.webp`, `assets/PLN_02-route-computed.webp` |
| `PLN_03` | FAIL | `packets/PLN_03.md` | Dragging the leg panned/shifted the map or left the route unchanged; the route stayed at 1 leg with unchanged stats after retry. | `assets/PLN_03-route-drag-insert.webp`, `assets/PLN_03-route-rebuilt.webp`, `assets/PLN_03-route-drag-retry.webp` |
| `PLN_04` | PASS | `packets/PLN_04.md` | Dragging the selected waypoint changed distance from 0.33 km to 5.79 km; `Delete selected waypoint` reduced the route; Undo restored it, Redo deleted it again; Clear set 0.00 km/0 legs; Undo restored route, Redo cleared it. | `assets/PLN_04-route-before-move.webp`, `assets/PLN_04-waypoint-moved.webp`, `assets/PLN_04-waypoint-deleted.webp`, `assets/PLN_04-undo-restored.webp`, `assets/PLN_04-redo-deleted.webp`, `assets/PLN_04-cleared.webp`, `a... |
| `PLN_05` | PASS | `packets/PLN_05.md` | Stats changed from 0.33 km/0m/0m/0m/1 leg to 5.79 km/1m/6m/15m/1 leg after moving a waypoint, then returned to 0.00 km/0 legs after delete/clear states. | `assets/PLN_04-route-before-move.webp`, `assets/PLN_04-waypoint-moved.webp`, `assets/PLN_04-waypoint-deleted.webp`, `assets/PLN_04-cleared.webp` |
| `PLN_06` | PASS | `packets/PLN_06.md` | Profile rendered with route elevation values; hovering showed a chart tooltip (`5.79 km`, `412 m`, `-0.1%`) and an orange hover marker on the visible route point. | `assets/PLN_06-profile-before-hover.webp`, `assets/PLN_06-profile-hover-retry.webp` |
| `PLN_07` | PASS | `packets/PLN_07.md` | Save dialog accepted name/description; Load showed the saved route with 5.8 km metadata; selecting it restored the Drawing view with the route; delete confirmation removed it and the list returned to `No saved routes yet`. | `assets/PLN_07-save-dialog.webp`, `assets/PLN_07-load-list.webp`, `assets/PLN_07-loaded-plan.webp`, `assets/PLN_07-delete-dialog.webp`, `assets/PLN_07-after-delete.webp` |
| `PLN_08` | PASS | `packets/PLN_08.md` | Download returned HTTP 200 `application/gpx+xml`; GPX root/name were valid; 5 `<trkpt>` entries matched the saved-plan detail coordinate count and first/last coordinates; disposable plan delete returned 204. | `assets/PLN_08-after-save.webp`, `assets/PLN_08-download-headers.txt`, `assets/PLN_08-download-verification.txt`, `assets/PLN_08-export.gpx` |
| `PLN_09` | N/A | `packets/PLN_09.md` | Not applicable in this configured run: tested planner routes computed successfully and BRouter status showed ready, running `yes`, 3 segments on disk, queued `0`. No missing-data state occurred. | `assets/PLN_09-brouter-ready.webp` |
| `PLN_10` | N/A | `packets/PLN_10.md` | Not applicable in this configured run: no route-fetch trouble occurred; BRouter status was ready and PLN_07 already verified saved routes list/load/delete under normal routing state. | `assets/PLN_07-load-list.webp`, `assets/PLN_09-brouter-ready.webp` |
| `PLN_11` | BLOCKED | `packets/PLN_11.md` | BLOCKED: workspace has no Playwright/touch harness installed, and the in-app browser surface for this run does not expose mobile viewport or touch emulation controls. | `assets/PLN_11-touch-blocked.txt` |
| `MCT_01` | PASS | `packets/MCT_01.md` | Analyzer showed A/B zones each crossing 1 track; Analyze produced a 1/1 track table with Lannion result, duration `3h 42m`, and A-B speed metric `-1.03`, with metric controls for speed/time/distance. | `assets/MCT_01-lannion-map.webp`, `assets/MCT_01-zones-placed.webp`, `assets/MCT_01-results.webp` |
| `MCT_02` | PASS | `packets/MCT_02.md` | Track Details opened for `#100003` with Lannion overview metrics while Segment Analyzer remained available behind it. | `assets/MCT_02-result-click.webp` |
| `MCT_03` | PASS | `packets/MCT_03.md` | After closing Segment Analyzer, the side button was no longer active, the analyzer sheet was gone, and the map no longer showed A/B zone markers or analyzer result UI. | `assets/MCT_03-after-detail-close.webp`, `assets/MCT_03-stopped-clean.webp` |
| `MCT_04` | FAIL | `packets/MCT_04.md` | Analyzer found 3/3 tracks (`JuraRoute72011.gpx`, `.igc`, `.nmea`) and Compare opened with racer cards plus speed/altitude charts. The required comparison mini-map did not render as a usable map: the MapLibre canvas existed but its `.sc-minimap`/wrapper height was `0`/collapsed, leaving no visible segment map to verify alignment. | `assets/MCT_04-compare-top.webp`, `assets/MCT_04-compare-charts.webp`, `assets/MCT_04-canvas-bounds.txt` |
| `MCT_05` | PASS | `packets/MCT_05.md` | Returned 89 ordered points on one `gpsTrackDataId` from index 8 to 96. First/last coordinates matched the requested segment, distance delta was 18,101.42 m, duration delta was 4,017 s, and missing moving-window speed values did not break extraction. | `assets/MCT_05-subtrack-response.txt` |
| `AVR_01` | FAIL | `packets/AVR_01.md` | Animate opened, but it showed `Tracks 0 / 0`; Play and Stop were disabled while the map header still showed `10 Tracks`. Playback, pause, reset, and speed behavior could not be exercised. | `assets/AVR_01-animate-disabled.webp`, `assets/AVR_01-animate-disabled.txt` |
| `AVR_02` | FAIL | `packets/AVR_02.md` | Race opened with `3 racers`, a mini-map, rank cards, and A/B segment. Starting playback moved the racer marker/trail on the map and toggled the control to Pause. The ranking/card area stayed static across ready, running, and later states, with no visible live progress or ranking update. | `assets/AVR_02-race-ready.webp`, `assets/AVR_02-race-running.webp`, `assets/AVR_02-race-later.webp`, `assets/AVR_02-race-running.txt`, `assets/AVR_02-race-later.txt` |
| `AVR_03` | PASS | `packets/AVR_03.md` | Race overlays closed; map returned to normal state with `10 Tracks`, zoom changed from 30 km to 20 km scale, pan/drag worked, and location search opened/closed normally. | `assets/AVR_03-after-race-close.webp`, `assets/AVR_03-map-usable.webp`, `assets/AVR_03-map-usable.txt` |
| `MED_01` | PASS | `packets/MED_01.md` | With the layer disabled no red media marker was visible; after enabling Photos & Media, the red cluster marker `2` appeared over the indexed Arezzo media coordinate. | assets/MED_01-media-layer-on.webp; assets/MED_01-media-api.txt |
| `MED_02` | PASS | `packets/MED_02.md` | Arezzo showed the red cluster `2`; after pan/zoom away no media marker was visible; returning to Arezzo showed cluster `2` again. API bounds returned the two Arezzo media IDs for Arezzo bounds and `[]` for west-Arezzo bounds, while all indexed regression media globally contained three items. | assets/MED_02-pan-zoom-away.webp; assets/MED_02-return-arezzo.webp; assets/MED_02-viewport-api.txt |
| `MED_03` | PASS | `packets/MED_03.md` | Photo sheet opened with image `MED_JPEG_01_DSCN0010.jpg` at `2 / 2`; Previous moved to `MED_JPEG_02_DSCN0010_COPY.jpg` at `1 / 2`; Next returned to `MED_JPEG_01_DSCN0010.jpg` at `2 / 2`. | assets/MED_03-preview-2of2.webp; assets/MED_03-preview-1of2.webp; assets/MED_03-navigation.txt |
| `MED_04` | PASS | `packets/MED_04.md` | Content endpoint returned `HTTP 200` with `Content-Type: image/jpeg`; ImageMagick identified the response as a 793x1024 JPEG. The UI preview displayed the converted flower image with filename `MED_HEIC_01_IMG_5195.HEIC` and Apple iPhone metadata. | assets/MED_04-heic-preview.webp; assets/MED_04-heic-api.txt |
| `MED_05` | FAIL | `packets/MED_05.md` | Preview opened, but the main media area was blank except for a broken-image icon/alt text. No retry/actionable error message appeared. The content endpoint returned `HTTP 200` with `Content-Type: image/jpeg` and a 0-byte body. | assets/MED_05-broken-preview.webp; assets/MED_05-broken-media.txt |
| `HMO_01` | PASS | `packets/HMO_01.md` | Heatmap density rendered around the track while the track line stayed visible above it. Turning Heatmap off removed the density overlay. Re-enabling with lower opacity restored a weaker overlay without hiding the track. | assets/HMO_01-heatmap-full.webp; assets/HMO_01-heatmap-off.webp; assets/HMO_01-heatmap-low-opacity.webp; assets/HMO_01-actions.txt |
| `HMO_02` | PASS | `packets/HMO_02.md` | Worldwide and Swiss overlay controls switched independently. Opacity handles moved for worldwide and Swiss overlays. Waymarked Trails and Swiss overlays rendered without making the visible GPS track layer disappear. | assets/HMO_02-worldwide-overlays.webp; assets/HMO_02-swiss-overlays.webp; assets/HMO_02-overlay-states.txt |
| `HMO_03` | PASS | `packets/HMO_03.md` | With empty keyword, Lannion showed `10 / 10 Tracks` and the local heatmap. Entering `Jura` reduced the map to `1 / 10 Tracks` and removed the Lannion track/heatmap. Clearing the keyword restored `10 / 10 Tracks` and the Lannion heatmap. | assets/HMO_03-all-tracks-heatmap.webp; assets/HMO_03-jura-filter-heatmap.webp; assets/HMO_03-restored-heatmap.webp; assets/HMO_03-filter-heatmap.txt |
| `GPS_01` | PASS | `packets/GPS_01.md` | The app was loaded from `http://178.105.173.254:18080/mtl/`, not localhost or HTTPS. Browser geolocation was not exposed in the page context. Opening GPS produced only a transient app info toast and no usable browser permission/marker validation path. | `assets/GPS_01-secure-context.txt`, `assets/GPS_01-gps-panel-http.webp` |
| `GPS_02` | N/A | `packets/GPS_02.md` | This run is on a remote plain-HTTP origin, confirmed by GPS_01. The plan explicitly marks live GPS permission/marker checks not applicable for this target type. | `assets/GPS_01-secure-context.txt`, `assets/GPS_01-gps-panel-http.webp` |
| `GPS_03` | N/A | `packets/GPS_03.md` | This run has no secure-origin geolocation path because it is remote plain HTTP. Without a live locate marker/position stream, follow-me recentering cannot be meaningfully exercised here. | `assets/GPS_01-secure-context.txt` |
| `GPS_04` | N/A | `packets/GPS_04.md` | The configured target is remote plain HTTP, so the browser permission-denied flow for live geolocation is outside this run's applicable scope per GPS_01 and the test plan note. | `assets/GPS_01-secure-context.txt`, `assets/GPS_01-gps-panel-http.webp` |
| `GPS_05` | N/A | `packets/GPS_05.md` | This remote plain-HTTP run cannot establish a live browser geolocation marker/stream. There is no applicable live GPS state to disable and verify. | `assets/GPS_01-secure-context.txt` |
| `SRC_01` | PASS | `packets/SRC_01.md` | Results appeared in the search panel, headed by `Bern, Switzerland`, followed by additional matching places. | `assets/SRC_01-search-results.webp`, `assets/SRC_01-search-results.txt` |
| `SRC_02` | PASS | `packets/SRC_02.md` | Search panel closed back to the map and a visible `mtl-location-search-marker` appeared with an associated clear-marker button. | `assets/SRC_02-bern-selected.webp`, `assets/SRC_02-bern-selected.txt` |
| `SRC_03` | PASS | `packets/SRC_03.md` | Marker and clear-button DOM nodes were removed, and the map remained usable with `10 / 10 Tracks`. | `assets/SRC_03-marker-cleared.webp`, `assets/SRC_03-marker-cleared.txt` |
| `SRC_04` | PASS | `packets/SRC_04.md` | The search panel displayed `No matches`; no marker was placed and the map remained usable. | `assets/SRC_04-no-results.webp`, `assets/SRC_04-no-results.txt` |
| `GLB_01` | PASS | `packets/GLB_01.md` | At world-scale zoom, the globe toggle became visible and active (`mtl-globe-active`), with the map still rendered and usable. | `assets/GLB_01-globe-active-retry.webp`, `assets/GLB_01-globe-state.txt` |
| `GLB_02` | PASS | `packets/GLB_02.md` | At 5 km scale, the globe control was hidden and no longer had `mtl-globe-active`; the map rendered normally with tracks. | `assets/GLB_02-flat-after-zoom-in.webp`, `assets/GLB_02-flat-state.txt` |
| `GLB_03` | PASS | `packets/GLB_03.md` | After manual disable, the globe control stayed visible but inactive through low-zoom nudges; it became active again only after clicking the toggle a second time. | `assets/GLB_03-manual-disabled.webp`, `assets/GLB_03-reenabled.webp`, `assets/GLB_03-manual-disable.txt` |
| `GLB_04` | PASS | `packets/GLB_04.md` | Low-zoom panning kept the map rendered, and subsequent Zoom in controls still changed from 2000 km globe to 5 km flat view. | `assets/GLB_04-low-zoom-pan.webp`, `assets/GLB_04-zoomed-back-in.webp`, `assets/GLB_04-zoom-limits.txt` |
| `ADM_01` | PASS | `packets/ADM_01.md` | Admin workspace showed grouped entries for Data, System, and Session. Clicking `Open Upload` opened the upload panel in-place. | `assets/ADM_01-admin-upload-tab.webp`, `assets/ADM_01-admin-tabs.txt` |
| `ADM_02` | FAIL | `packets/ADM_02.md` | Upload availability was clear and API success/error messages were clear. However, the UI picker/drop-zone only advertises and accepts `.gpx`, while the endpoint reports broader accepted formats. Native UI progress could not be driven because this browser runtime exposes no file-picker/set-input-files method. | `assets/ADM_02-upload-panel.webp`, `assets/ADM_02-upload-results.txt`, `assets/ADM_02-upload-valid.gpx`, `assets/ADM_02-upload-unsupported.xyz` |
| `ADM_03` | PASS | `packets/ADM_03.md` | Jobs panel showed MEDIA and GPS file-indexer progress plus job states. API status exposed pending/completed/failed/removed counts. Refresh updated the timestamp from 10:10:09 to 10:11:06. | `assets/ADM_03-jobs-panel.webp`, `assets/ADM_03-jobs-refreshed.webp`, `assets/ADM_03-indexer-status.txt` |
| `ADM_04` | PASS | `packets/ADM_04.md` | GPS and MEDIA rescans showed clear queued messages; MEDIA briefly showed `SCANNING`. The ready indexers completed too quickly to produce `ALREADY_RUNNING`, and not-ready was not applicable. Map zoom still changed scale from 5 km to 10 km. | `assets/ADM_04-rescan-controls.webp`, `assets/ADM_04-rescan-results.txt` |
| `ADM_05` | PASS | `packets/ADM_05.md` | Duplicate Finder and Exploration Score were visible as `DONE 100%`; API reported pending=0 and done=total for all background jobs. | `assets/ADM_05-background-jobs.webp`, `assets/ADM_05-background-jobs.txt` |
| `ADM_06` | PASS | `packets/ADM_06.md` | Healthy-run ready states were shown with detail: hosted map service, GeoNames ready, and BRouter routing segments ready with 3 segments on disk. Disabled/downloading/unavailable states did not occur in this run. | `assets/ADM_06-operational-tasks.webp`, `assets/ADM_06-operational-tasks.txt` |
| `ADM_07` | PASS | `packets/ADM_07.md` | Banner offered `Reload` and `Dismiss`; Freshness panel showed `Out of sync`, checked time, latest change timestamp, server/client tokens, outdated domains, and healthy polling. | `assets/ADM_07-freshness-panel.webp`, `assets/ADM_07-freshness.txt` |
| `ADM_08` | PASS | `packets/ADM_08.md` | Timestamped server log lines loaded; Refresh added/advanced visible API request entries. | `assets/ADM_08-server-log.webp`, `assets/ADM_08-server-log.txt` |
| `ADM_09` | PASS | `packets/ADM_09.md` | Attribution listed expected rendering, basemap, trail overlay, chart, location search, conversion, and routing sources including OpenStreetMap, Protomaps, swisstopo, SchweizMobil, GeoNames, GPSBabel, and BRouter. | `assets/ADM_09-attribution.webp`, `assets/ADM_09-attribution.txt` |
| `ADM_10` | PASS | `packets/ADM_10.md` | Helpers showed `2/2 READY`; API reported both exporter environments present. The `gcexport` install action returned clear output saying the existing venv was already present and active version was updated in DB. | `assets/ADM_10-helpers.webp`, `assets/ADM_10-helper-install-output.webp`, `assets/ADM_10-garmin-tools.txt` |
| `ADM_11` | PASS | `packets/ADM_11.md` | Reopening restored the Admin workspace with the Helpers panel still open and the recent gcexport command output intact. | `assets/ADM_11-admin-reopened.webp`, `assets/ADM_11-close-reopen.txt` |
| `SYN_01` | PASS | `packets/SYN_01.md` | Banner appeared with `New data available`, explanatory text, and `Reload`/`Dismiss` actions. | `assets/SYN_01-freshness-banner.webp`, `assets/SYN_01-freshness-banner.txt` |
| `SYN_02` | PASS | `packets/SYN_02.md` | Banner cleared; map changed from `10 / 10 Tracks` to `11 / 11 Tracks`; Stats Overview showed 11 tracks and included `ADM_02 Upload Validation` as latest/recent activity. | `assets/SYN_02-reload-results.txt` |
| `SYN_03` | PASS | `packets/SYN_03.md` | Earlier packets directly verified import success, freshness reload, map/browser/stats/filter/heatmap/detail updates, automatic delete processing, and removal of deleted tracks from user-visible surfaces. | `assets/SYN_03-five-gpx-delete-flow.txt`, `packets/IMP_02.md` through `packets/IMP_09.md`, `packets/DEL_01.md` through `packets/DEL_05.md` |
| `SYN_04` | PASS | `packets/SYN_04.md` | FIT conversion succeeded, became visible on map/list/stats/detail surfaces, and produced original/GPX downloads. Native freshness/cache reload behavior was also directly verified in SYN_01/SYN_02. | `assets/SYN_04-fit-freshness-flow.txt`, `packets/FIT_01.md` through `packets/FIT_06.md`, `packets/SYN_01.md`, `packets/SYN_02.md` |
| `SYN_05` | FAIL | `packets/SYN_05.md` | Banner appeared, but Dismiss did not hide it; the banner remained visible after all click paths and waits. | `assets/SYN_05-dismiss-results.txt`, `assets/SYN_05-dismiss-upload.gpx` |
| `SYN_06` | PASS | `packets/SYN_06.md` | App returned to map with `12 / 12 Tracks`; no freshness banner appeared immediately after login or after a 10 second wait. | `assets/SYN_06-logout-login.txt` |
| `SYN_07` | PASS | `packets/SYN_07.md` | Jobs tile showed `PROCESSING`; the media rescan queued/settled; map Zoom in remained responsive and changed scale from 1000 km to 500 km. | `assets/SYN_07-indexer-running.txt` |
| `APP_01` | PASS | `packets/APP_01.md` | `data-theme` and pressed Settings controls changed immediately for light and dark; visible Admin/Settings colors changed with the selected scheme. | `assets/APP_01_02-theme-switch.txt` |
| `APP_02` | PASS | `packets/APP_02.md` | No white-on-white or black-on-black text was observed; sampled active theme buttons had contrast ratios of 7.07 in light and 6.96 in dark. | `assets/APP_01_02-theme-switch.txt` |
| `APP_03` | PASS | `packets/APP_03.md` | Highcharts text fill changed from `rgb(100, 116, 139)` in light to `rgb(148, 163, 184)` in dark; eight chart roots rendered in both states. | `assets/APP_03-chart-theme.txt` |
| `APP_04` | PASS | `packets/APP_04.md` | `data-theme` remained `dark` after reload, on the login page, and after signing back in. | `assets/APP_04_05-theme-persistence.txt` |
| `APP_05` | PASS | `packets/APP_05.md` | The first captured post-DOM-load state already had `data-theme=dark`; the app applies the stored scheme at `useTheme.ts` module import before Vue mounts. No light mounted state was observed. | `assets/APP_04_05-theme-persistence.txt`, `mtl-client/src/composables/useTheme.ts` |
| `APP_06` | FAIL | `packets/APP_06.md` | The five exposed styles could be selected independently in dark UI, but no grayscale style was exposed despite the coverage scope listing it. | `assets/APP_06-map-styles.txt` |
| `APP_07` | PASS | `packets/APP_07.md` | After reload, the dark UI remained active and OSM Dark still had the active check. | `assets/APP_07-map-style-persistence.txt` |
| `APP_08` | PASS | `packets/APP_08.md` | Base Map opacity changed the tile container to `opacity: 0.45` and persisted after reload. GPS Tracks opacity persisted at 50% after reload. Reset restored OSM Topo, Base Map 100%, GPS Tracks 100%, and Heatmap disabled. | `assets/APP_08-opacity-reset.txt` |
| `LOC_01` | PASS | `packets/LOC_01.md` | en-GB preview used `26/05/2026` and `12,345.67`; Stats showed grouped values such as `1,262 km`, `7,054 Wh`, dates like `26/05/2026, 11:20`, and compact durations such as `2m 00s`. | `assets/LOC_01-locale-baseline.txt` |
| `LOC_02` | FAIL | `packets/LOC_02.md` | Settings preview, dates, and grouped values updated immediately to de-DE style, but decimal unit values such as `94.26 m`, `3.60 km`, and `72.5 km/h` kept period decimal separators instead of de-DE comma separators. | `assets/LOC_02-locale-switch.txt` |
| `LOC_03` | PASS | `packets/LOC_03.md` | After reload, Settings still showed de-DE selected and the preview stayed in de-DE format: `26.05.2026 ... 12.345,67`. | `assets/LOC_03-locale-persistence.txt` |
| `LOC_04` | PASS | `packets/LOC_04.md` | Zero ascent/descent and no-elevation imports rendered as normal recent activity rows with dates, distances, and durations. Visible body text did not contain `NaN`, `undefined`, `null`, or `Infinity`. | `assets/LOC_04-boundary-values.txt`, `assets/LOC_04-boundary.gpx`, `assets/LOC_04-null-elevation.gpx` |
| `MOB_01` | BLOCKED | `packets/MOB_01.md` | Narrow mobile layout loaded, but the browser runtime exposes only viewport resizing, not touch input or gesture emulation. | `assets/MOB_01-mobile-context.txt` |
| `MOB_02` | BLOCKED | `packets/MOB_02.md` | Filter sheet opened and closed at mobile width. Pointer drag attempts did not change sheet/nav positions, and touch drag/snap cannot be verified because the runtime lacks touch input. | `assets/MOB_02-mobile-sheets.txt` |
| `MOB_03` | PASS | `packets/MOB_03.md` | Stats tabs were clickable, Tracks showed mobile list rows and sort controls, document width stayed at 390 px, and Zoom in changed scale from 1000 km to 500 km. | `assets/MOB_03-mobile-usability.txt` |
| `MOB_04` | BLOCKED | `packets/MOB_04.md` | No touch input or mobile gesture capability is available in this browser runtime; planner touch behavior cannot be executed. | `assets/MOB_04-touch-planner-blocked.txt` |
| `MOB_05` | BLOCKED | `packets/MOB_05.md` | Pointer map controls worked at mobile width, but pinch and touch double-tap require a touch/gesture-capable context that this browser runtime does not expose. | `assets/MOB_05-touch-gestures-blocked.txt` |
| `NET_01` | N/A | `packets/NET_01.md` | This run used a normal browser tab; the test plan explicitly says that context is not expected to pass and should be marked not applicable. | `assets/NET_01-pwa-mode.txt` |
| `NET_02` | PASS | `packets/NET_02.md` | The app showed `Unable to load tracks - no server connection and no cached data available.` with a visible Retry button; splash was not frozen and the map shell/nav remained visible. | `assets/NET_02-network-recovery.txt`, `assets/NET_02-network-recovery.spec.ts` |
| `NET_03` | PASS | `packets/NET_03.md` | The app redirected to `/mtl/login?reason=expired` and showed the login form. | `assets/NET_03-auth-redirect.txt`, `assets/NET_03-auth-redirect.spec.ts` |
| `NET_04` | N/A | `packets/NET_04.md` | No new client build or service-worker version was deployed during this fixed-target regression run, so no update prompt event was applicable. | `assets/NET_04-service-worker-update.txt` |
| `ERR_01` | FAIL | `packets/ERR_01.md` | API/map failure showed Retry and expired session redirected to login, but broken media still rendered a blank/broken preview with no actionable recovery message. Planner route-fetch trouble did not occur in this configured run. | `assets/ERR_01-error-recovery.txt`, `assets/NET_02-network-recovery.txt`, `assets/NET_03-auth-redirect.txt`, `assets/MED_05-broken-media.txt` |
| `ERR_02` | PASS | `packets/ERR_02.md` | Final state had one active Map sheet, zero stale track-pick/planner/segment/location/media overlays, cursor `auto`, no bad text, and map Zoom out changed scale from 300 km to 500 km. | `assets/ERR_02-rapid-tool-switching.txt` |

</details>

## Cleanup

Cleanup completed after finalization gate PASS. The target server no longer has the disposable install directory, no `mtl-explorer-*` containers are running, and port `18080` has no listener. No global Docker prune or unrelated resource removal was performed.

## Conclusion

MTL Explorer quick install worked and the regression run reached terminal status for every required coverage ID. The release is not clean from an end-user regression standpoint because 23 coverage IDs failed, including several P2 issues in import formats, map/list consistency, controls exposure, freshness dismissal, locale formatting, and error recovery. Cleanup passed.
