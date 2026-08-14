# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-08-13_2028-beta-full-regression |
| Target server | 91.99.12.14 |
| SSH user | root |
| Source | GitHub main quick install, with app image override `wauwau0977/mytraillog:beta` |
| App image | `wauwau0977/mytraillog:beta` |
| Coverage plan snapshot | `coverage-plan.md` |
| Coverage plan source | `documentation/testing/frontend-regression-test-plan.md` |
| Coverage plan Git revision | `26acee1ba22ff355d09acbc30d0638d5a5947d77` |
| Coverage plan SHA-256 | `673d46d9e05e1d66b106b8fd34e0a3900544f40ec0a3d3fde6e8f4d2e759702d` |
| App URL | http://91.99.12.14:18080/mtl/ |
| Started | 2026-08-13T20:28:34+02:00 |
| Coordinator | Codex |

## Shared Facts

- README facts: Docker Engine plus the Docker Compose plugin; download GitHub `main` `docker-compose.yml`; run `docker compose up -d`; open `http://localhost:18080/mtl/`; copy imports into `./data/gpx/`.
- Login credentials source: GitHub `main` README quick-start credentials `mtl` / `change-me`.
- Import folder: `/root/mtl-full-regression-2026-08-13_2028-beta-full-regression/data/gpx` (documented relative folder `./data/gpx/`).
- Browser contexts: Codex in-app browser at `http://91.99.12.14:18080/mtl/`; desktop initially, narrow mobile/touch to be added for responsive coverage.
- Known constraints: remote origin is plain HTTP, so secure-origin-only geolocation may be not applicable; installed-PWA-only offline coverage depends on an installed context. SSH first login required password rotation; the operator public key added for resumability must be removed during cleanup after the supplied temporary credential is restored. During SGN_07, Browser Use URL policy blocked the intentional server-down reload; do not bypass it. The stale tab was later replaced through the browser skill's supported recovery path, and normal target operations resumed.

## Queue

- Source queue: `coverage-plan.md`
- Current coverage ID: COMPLETE
- Next coverage ID: NONE
- Frozen coverage ID count: 193

Track active, blocked, failed, and recently completed IDs here. Completed packet
files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | packets/RUN_SETUP.md | Quick install is healthy with requested beta image/digest verified; image build 1.331; remote login screen reachable. |
| ACC_01 | PASS | Codex | packets/ACC_01.md | Frozen plan and run state contain the same 193 unique coverage IDs. |
| ACC_02 | PASS | Codex | packets/ACC_02.md | Queue and packet model require one result per exact coverage ID; no parent shortcut rows exist. |
| ACC_03 | PASS | Codex | packets/ACC_03.md | Completed packets have per-ID action, expected, actual, status, and evidence fields; report stays gated. |
| ACC_04 | PASS | Codex | packets/ACC_04.md | Compact working login and signed-in empty-map WebP evidence saved below 85 KB. |
| ACC_05 | PASS | Codex | packets/ACC_05.md | Constraints are explicit and cannot silently become PASS or satisfy finalization while resumable. |
| DAT_01 | PASS | Codex | packets/DAT_01.md | Five public GPX files staged outside watched folder; all contain real track sequences. |
| DAT_02 | PASS | Codex | packets/DAT_02.md | All 7,735 positive GPX trackpoints contain timestamps. |
| DAT_03 | PASS | Codex | packets/DAT_03.md | All six sources have complete metadata and imported mappings: GPX 100000-100004; FIT 100005 Activity.fit. |
| DAT_04 | PASS | Codex | packets/DAT_04.md | Exact suggested five-file public GPX set is staged and validated. |
| DAT_05 | PASS | Codex | packets/DAT_05.md | Public Garmin Activity.fit validated: integrity true; 3,601 timestamped GPS records. |
| DAT_06 | PASS | Codex | packets/DAT_06.md | Positive evidence includes only real-track GPX and GPS-bearing FIT data. |
| DAT_07 | PASS | Codex | packets/DAT_07.md | Two fully synthetic timestamped tracks cross repeatable shared start/end zones; no private GPX used. |
| IMP_01 | PASS | Codex | packets/IMP_01.md | Empty baseline: 0 tracks, zero totals, matched freshness tokens with index/tracks r0, GPS/jobs done 0/0. |
| IMP_02 | PASS | Codex | packets/IMP_02.md | Watched folder changed 0→5 files; all five checksums match staged public sources. |
| IMP_03 | PASS | Codex | packets/IMP_03.md | Live watcher indexed all five GPX files successfully in 18.171 s; no Rescan GPS needed. |
| IMP_04 | PASS | Codex | packets/IMP_04.md | GPS and processing jobs settled 5/5 with no failure state; freshness token changed and stale banner appeared. |
| IMP_05 | FAIL | Codex | packets/IMP_05.md | IMP-05-P1: helper Reload updated map/browser but left Filter/Stats at zero until normal browser reload. |
| IMP_06 | PASS | Codex | packets/IMP_06.md | All five files verified by exact filename search, direct map geometry, Stats, and Filter Review tracks. |
| IMP_07 | PASS | Codex | packets/IMP_07.md | All five main-map selections opened matching details; every detail line produced a complete point popup; no stale or duplicate line observed. |
| IMP_08 | PASS | Codex | packets/IMP_08.md | Statistics count changed 0→5; all five GPX sources mapped one-to-one to records. |
| IMP_09 | PASS | Codex | packets/IMP_09.md | Totals, ascent/descent direction, activity, quarter charts/table, rankings, heatmap density, and Track Browser summary all populated consistently. |
| DEL_01 | PASS | Codex | packets/DEL_01.md | Deleted exactly Vitry and VoieVerte from the disposable watched folder; 3 sources remain. |
| DEL_02 | PASS | Codex | packets/DEL_02.md | Watcher deleted IDs 100001 and 100003 automatically in 8.309 s; no Rescan GPS used. |
| DEL_03 | FAIL | Codex | packets/DEL_03.md | DEL-03-P1: freshness Reload updated map/Stats to 3 but Filter retained both deleted rows until browser reload. |
| DEL_04 | PASS | Codex | packets/DEL_04.md | Jura #100000, Mosel #100002, and Lannion #100004 remain visible and open matching healthy details. |
| DEL_05 | PASS | Codex | packets/DEL_05.md | Final browser-visible state excludes both deleted tracks everywhere; API/stale-URL behavior was not used as a criterion. |
| FIT_01 | PASS | Codex | packets/FIT_01.md | Verified official Garmin Activity.fit copied unchanged into watched folder; count 3→4. |
| FIT_02 | PASS | Codex | packets/FIT_02.md | GPSBabel conversion/ingest succeeded as #100005; map 4, exact source search 1, Statistics totals updated. |
| FIT_03 | PASS | Codex | packets/FIT_03.md | FIT Overview/mini-map/popup, six graph groups, Quality, Related, and valid empty Events all render. |
| FIT_04 | PASS | Codex | packets/FIT_04.md | Original download is 94,096-byte FIT and checksum-identical to uploaded Activity.fit. |
| FIT_05 | PASS | Codex | packets/FIT_05.md | Downloaded GPX validates and contains 3,601 timestamped trkpt elements and zero wpt elements. |
| FIT_06 | NOT APPLICABLE | Codex | packets/FIT_06.md | Conditional unavailable-converter path did not apply: GPSBabel and FIT conversion worked end to end. |
| FMT_01 | PASS | Codex | packets/FMT_01.md | GPX, FIT, TCX, KML, KMZ, IGC, NMEA, GeoJSON, and GDB all accepted; 12 visible records because IGC split at a real temporal gap. |
| FMT_02 | PASS | Codex | packets/FMT_02.md | All eight non-GPX formats passed exact search, map/details/charts, statistics, original-download integrity, and valid GPX download. |
| SGN_01 | PASS | Codex | packets/SGN_01.md | Signed-out app root redirected to `/mtl/login` with the login form visible. |
| SGN_02 | PASS | Codex | packets/SGN_02.md | Valid documented credentials reached the 12-track map in 1.67 s. |
| SGN_03 | PASS | Codex | packets/SGN_03.md | Invalid password produced a clear alert and retained the ready login form. |
| SGN_04 | NOT APPLICABLE | Codex | packets/SGN_04.md | Demo mode is false, so the conditional demo-credentials banner requirement does not apply. |
| SGN_05 | PASS | Codex | packets/SGN_05.md | Credentials-only sign-out returned to login; valid sign-in restored the 12-track map. |
| SGN_06 | PASS | Codex | packets/SGN_06.md | Reload showed the branded loading splash at 234 ms and the 12-track map after 1.8 s. |
| SGN_07 | BLOCKED | Codex | packets/SGN_07.md | Browser URL safety policy prohibited observing the intentional server-down startup state; app service was restored and health-checked. |
| SGN_08 | PASS | Codex | packets/SGN_08.md | Public About launcher, heading, and installation copy use MTL Explorer branding. |
| SGN_09 | PASS | Codex | packets/SGN_09.md | Browser Back restored Stats and Forward restored Filter without an error surface. |
| MAP_01 | PASS | Codex | packets/MAP_01.md | Fresh open rendered the OSM base map, labels, attribution, controls, 12 track overlays, and no browser error. |
| MAP_02 | PASS | Codex | packets/MAP_02.md | Map badge, Track Browser summary, and table row count all equal the expected 12 visible records. |
| MAP_03 | PASS | Codex | packets/MAP_03.md | Existing browser map changed 0→5 within 4 s of the freshness Reload; no browser restart preceded map evidence. |
| MAP_04 | PASS | Codex | packets/MAP_04.md | Final recovered state excludes both deleted tracks from map, selection sources, popups, Related, heatmap, and statistics. |
| MAP_05 | PASS | Codex | packets/MAP_05.md | Zooming 200 km→30 km improved route precision with continuous single lines and no browser error. |
| MAP_06 | PASS | Codex | packets/MAP_06.md | Rapid alternating pan/zoom settled to complete tiles and continuous overlays with no spinner or browser error. |
| MAP_07 | PASS | Codex | packets/MAP_07.md | Enabled Track points/direction layer rendered many arrowhead markers at dense visible Lannion vertices at 30 m scale. |
| MAP_08 | PASS | Codex | packets/MAP_08.md | Clicking isolated Activity.fit opened #100005 directly with highlighted detail geometry and no chooser. |
| MAP_09 | PASS | Codex | packets/MAP_09.md | Overlap click showed a two-track Jura/Mosel chooser; picking Mosel opened matching #100002 details. |
| MAP_10 | PASS | Codex | packets/MAP_10.md | Closing Track Details returned to `/mtl/` with no chooser/detail and a normal undimmed 12-track map. |
| MAP_11 | PASS | Codex | packets/MAP_11.md | Clicking an actual direction marker opened #100004 point 15 with time, coordinate, elevation, distance, elapsed, ascent/descent, and energy metrics. |
| MAP_12 | PASS | Codex | packets/MAP_12.md | SchweizMobil cycling overlay identified Goldküste–Limmat #66 in Nearby Routes; popup closed cleanly and overlay was restored off. |
| MAP_13 | PASS | Codex | packets/MAP_13.md | Intentional remote mode passed all three provider themes, matching attribution/interactivity, frozen-image identity, and zero map-proxy requests. |
| MAP_14 | BLOCKED | Codex | packets/MAP_14.md | Quick install has no local map-server sidecar or safe PMTiles-only failure control; normal local mode already uses hosted/public proxy fallback. |
| MAP_15 | PASS | Codex | packets/MAP_15.md | Manual Remote mode offered four OSM themes, hid Swiss themes, persisted reload, avoided map-proxy, and Reset restored Automatic. |
| TRD_01 | PASS | Codex | packets/TRD_01.md | Statistics filename search opened GPX #100000 and FIT #100005; exact source identities recorded. |
| TRD_02 | PASS | Codex | packets/TRD_02.md | GPX #100000 loaded populated overview, graphs, quality, related, events, and mini-map surfaces. |
| TRD_03 | PASS | Codex | packets/TRD_03.md | Two full tab cycles stayed populated at one URL and caused zero post-load detail/chart/related refetches. |
| TRD_04 | PASS | Codex | packets/TRD_04.md | Speed, elevation, gain-rate, and distance charts rendered populated readable axes with correct units. |
| TRD_05 | PASS | Codex | packets/TRD_05.md | Axis, range, point-count, and height controls updated populated charts without layout breakage; defaults restored. |
| TRD_06 | PASS | Codex | packets/TRD_06.md | Bidirectional chart/mini-map hover linking worked and both cursor states cleared on leave. |
| TRD_07 | PASS | Codex | packets/TRD_07.md | Shape previews rendered in Track Browser, Filter review, Stats overview, Related cards, and overlap chooser rows. |
| TRD_08 | PASS | Codex | packets/TRD_08.md | FIT original download was 94,096 bytes and checksum-identical to uploaded Activity.fit. |
| TRD_09 | PASS | Codex | packets/TRD_09.md | FIT-backed #100005 exported valid GPX 1.1 with 3,601 timestamped trackpoints. |
| TRD_10 | PASS | Codex | packets/TRD_10.md | #100005 activity saved Walking→Bicycle, energy changed 346.7→395.1 Wh, then Walking/346.7 Wh was restored. |
| TRD_11 | PASS | Codex | packets/TRD_11.md | 100 kg what-if changed energy/power transiently; reload restored unsaved 75 kg and baseline values. |
| TRD_12 | PASS | Codex | packets/TRD_12.md | Excluding #100005 changed 12→11 stats with exact totals removed; reincluding restored the baseline. |
| TRD_13 | PASS | Codex | packets/TRD_13.md | Related showed previous/next and three duplicates; previous and duplicate cards navigated to matching track IDs. |
| TRD_14 | PASS | Codex | packets/TRD_14.md | #100000 break selected an on-track mini-map target and cleared cleanly on second selection. |
| TRD_15 | FAIL | Codex | packets/TRD_15.md | TRD-15-P2: Statistics returns to Tracks but loses the originating one-result search on Close and Back; Filter Review preserves it. |
| FLT_01 | PASS | Codex | packets/FLT_01.md | Reload restored the 12 Tracks map chip and Smart Base Filter as the active current result. |
| FLT_02 | PASS | Codex | packets/FLT_02.md | Catalog showed 18 views in five groups; `year` search returned the two relevant date/time views. |
| FLT_03 | FAIL | Codex | packets/FLT_03.md | FLT-03-P2: parameter edits/reset require explicit Apply; dependent views stay stale until then. |
| FLT_04 | PASS | Codex | packets/FLT_04.md | Exact date range, keyword, rectangle, criteria count, and 0/12 result all rehydrated after reload. |
| FLT_05 | PASS | Codex | packets/FLT_05.md | Circle, rectangle, polygon, undo, cancel, finish, reload persistence, and clear all passed; no shapes left active. |
| FLT_06 | PASS | Codex | packets/FLT_06.md | Applied Jura filter synchronized 1/12 map, CYCLING 1 legend, and exact one-track Stats without page reload; restored. |
| FLT_07 | PASS | Codex | packets/FLT_07.md | Legend collapse/expand passed; CYCLING hide changed 12/12→1/12 immediately and restore returned 12/12. |
| FLT_08 | PASS | Codex | packets/FLT_08.md | Reset filter restored Smart Base Filter, no criteria, no keyword legend, and all 12 tracks. |
| FLT_09 | PASS | Codex | packets/FLT_09.md | Exact 2010/2013 selection yielded eight tracks consistently across map, legend, browser, heatmap, Overview, Trends, and Stats Tracks. |
| FLT_10 | FAIL | Codex | packets/FLT_10.md | FLT-10-P2: main-group selection works, but no filter view exposes exact Walking/Hiking result categories. |
| FLT_11 | PASS | Codex | packets/FLT_11.md | Expanding year range preserved checked 2010 and left newly discovered 2013 unchecked; exact result stayed two tracks. |
| FLT_12 | FAIL | Codex | packets/FLT_12.md | FLT-12-P2: select-none is stable, but selecting every available category remains exact 2/2 instead of All categories. |
| FLT_13 | PASS | Codex | packets/FLT_13.md | Unavailable selected 2013 stayed visible as missing and could be removed without changing valid 2010. |
| FLT_14 | PASS | Codex | packets/FLT_14.md | Same-view reselect retained exact 2010; changing to quarter cleared selection to All categories. |
| FLT_15 | PASS | Codex | packets/FLT_15.md | Exact Q1 restored on reload before a settled result; map, Filter, Statistics, and Review tracks all showed 8/12. |
| FLT_16 | FAIL | Codex | packets/FLT_16.md | FLT-16-P2: global selection changed to 12, but temporary Q1 map hiding persisted at 4/12. |
| FLT_17 | PASS | Codex | packets/FLT_17.md | Clean-context guidance auto-opened with required content and controls; Got it prevented repeat. |
| FLT_18 | PASS | Codex | packets/FLT_18.md | Returning guidance fit desktop and 390×844 without overflow; Read more, Back, and Close passed. |
| FLT_19 | PASS | Codex | packets/FLT_19.md | Pause/resume synchronized one↔twelve tracks across map, Statistics, reload persistence, desktop, and mobile. |
| FLT_20 | PASS | Codex | packets/FLT_20.md | Filter Review and Statistics Tracks shared search, summary, sort, paging, responsive table/cards, selection, and details. |
| FLT_21 | PASS | Codex | packets/FLT_21.md | Desktop Filter/review bounds matched; mobile review passed both 88vh and 95vh detents. |
| TBS_01 | PASS | Codex | packets/TBS_01.md | Filtered table/card showed required track identity, date, activity, distance, duration, and supporting fields. |
| TBS_02 | PASS | Codex | packets/TBS_02.md | Search matched name, description, date, distance, duration, activity, and file name/path with correct summaries. |
| TBS_03 | PASS | Codex | packets/TBS_03.md | All six exposed sorts changed order; full and searched summaries matched visible rows. |
| TBS_04 | PASS | Codex | packets/TBS_04.md | All four presets selected expected subsets and preserved Walking search plus Exploration sort. |
| TBS_05 | PASS | Codex | packets/TBS_05.md | Track 100005 row opened the matching populated Track Details surface. |
| TBS_06 | PASS | Codex | packets/TBS_06.md | Overview rendered totals, activity breakdown/chart, rankings, milestones, and active periods. |
| TBS_07 | PASS | Codex | packets/TBS_07.md | Statistics correctly represented 12, 1, and 0 resolved tracks without stale totals. |
| TBS_08 | FAIL | Codex | packets/TBS_08.md | IMP-05-P1: import freshness left Overview at 0/5 until browser reload; later deletion Statistics correctly reached 3. |
| TBS_09 | PASS | Codex | packets/TBS_09.md | Daily, weekly, and monthly Trends rendered expected periods and all chart panels; view toggle passed. |
| TBS_10 | PASS | Codex | packets/TBS_10.md | Recent Activity entry navigated to matching Track Details #100016. |
| TBS_11 | FAIL | Codex | packets/TBS_11.md | TBS-11-P2: excluded count showed 1 but opened a 0-track cached view until browser reload. |
| TBS_12 | PASS | Codex | packets/TBS_12.md | Circle reduced all filter-aware Statistics and map surfaces to 2/12 before and after reload; cleanup restored 12. |
| TBS_13 | BLOCKED | Codex | packets/TBS_13.md | Pointer opened Filter on desktop/mobile; keyboard harness also failed a native Trends control check, so app attribution was impossible. |
| PLN_01 | PASS | Codex | packets/PLN_01.md | Planner opened and Road Bike was selected from four profiles. |
| PLN_02 | PASS | Codex | packets/PLN_02.md | Four clicks computed and drew a three-leg 4.86 km Road Bike route with chart and stats. |
| PLN_03 | PASS | Codex | packets/PLN_03.md | Dragging a route leg inserted a waypoint: legs 4→5 and route/chart recalculated. |
| PLN_04 | PASS | Codex | packets/PLN_04.md | Move/delete/clear and undo/redo all changed route state and metrics correctly. |
| PLN_05 | PASS | Codex | packets/PLN_05.md | Stats bar updated across create/insert/move/delete/clear/undo/redo with exact prior values restored. |
| PLN_06 | PASS | Codex | packets/PLN_06.md | Elevation chart rendered 426 points; hover showed tooltip and matching visible map marker. |
| PLN_07 | PASS | Codex | packets/PLN_07.md | Saved, listed, loaded exact route, and deleted named plan; empty Load state verified. |
| PLN_08 | PASS | Codex | packets/PLN_08.md | Valid GPX 1.1 contained 426 elevation points and 7.66 km geometry matching the 7.69 km plan. |
| PLN_09 | PASS | Codex | packets/PLN_09.md | Missing W25_N60 produced clear downloading/auto-retry then unavailable messages; sidecar restored the segment. |
| PLN_10 | PASS | Codex | packets/PLN_10.md | Existing 7.69 km/4-leg/426-point plan stayed visible through download and unavailable states; undo recovered. |
| PLN_11 | BLOCKED | Codex | packets/PLN_11.md | Mobile pointer drag rerouted correctly, but browser exposes no touch-event capability for touch-specific attribution. |
| MCT_01 | PASS | Codex | packets/MCT_01.md | Two Lannion zones found seven shared tracks; populated result table exposed speed, time, and distance metrics. |
| MCT_02 | PASS | Codex | packets/MCT_02.md | Measured source row opened matching populated Track Details #100004 while analyzer results stayed available. |
| MCT_03 | PASS | Codex | packets/MCT_03.md | Stopping the measure tool removed placement guidance; a later map click created no zone or count. |
| MCT_04 | PASS | Codex | packets/MCT_04.md | Compare skipped three missing segments explicitly and aligned two valid tracks in the local map and two-line charts. |
| MCT_05 | FAIL | Codex | packets/MCT_05.md | MCT-05-P1: A-B returned 9m09s but 0.00 m, negative chart distance, zero metrics, and an endpoint-only line. |
| MCT_06 | PASS | Codex | packets/MCT_06.md | Selected A-B line and markers stayed within a 50 m-scale Lannion view with no global/off-continent jump. |
| AVR_01 | PASS | Codex | packets/AVR_01.md | Desktop/mobile range, speed, play, collapse, pause, resume, stop, expand, reset, finish, and replay all worked. |
| AVR_02 | PASS | Codex | packets/AVR_02.md | Two distinct racers moved concurrently; ranked cards advanced through sampled progress and live distances. |
| AVR_03 | PASS | Codex | packets/AVR_03.md | After both racers reached 100%, normal map zoom and Filter controls worked with no stuck race state. |
| AVR_04 | PASS | Codex | packets/AVR_04.md | Both racer markers/trails stayed within a 30 m-scale Lannion segment with no global or off-continent jump. |
| MED_01 | PASS | Codex | packets/MED_01.md | Four synthetic geotagged files indexed; enabling media rendered a three-photo cluster and one single pin. |
| MED_02 | PASS | Codex | packets/MED_02.md | Media was absent outside indexed bounds and loaded as a four-item cluster after pan/zoom into the current viewport. |
| MED_03 | PASS | Codex | packets/MED_03.md | Clicking the single pin opened a rendered 1/4 preview; Next and Previous navigated 1↔2 correctly. |
| MED_04 | PASS | Codex | packets/MED_04.md | Indexed HEIC rendered as item 4/4; server returned a valid 640×480 JPEG with image/jpeg. |
| MED_05 | PASS | Codex | packets/MED_05.md | Missing source produced an actionable Preview unavailable sheet; exact restore plus Retry recovered the image. |
| HMO_01 | PASS | Codex | packets/HMO_01.md | Heatmap rendered below readable tracks; its live opacity changed 100→40 while track opacity stayed 100. |
| HMO_02 | PASS | Codex | packets/HMO_02.md | Seven worldwide/Swiss overlays toggled independently with sliders; Cycling 100→40 stayed below readable tracks. |
| HMO_03 | PASS | Codex | packets/HMO_03.md | Exact Q1 changed 12→8 and the 40% heatmap recalculated to the remaining track geometry. |
| GPS_01 | NOT APPLICABLE | Codex | packets/GPS_01.md | Frozen-plan expected limitation: remote plain HTTP is not a secure geolocation origin; app required HTTPS/localhost. |
| GPS_02 | NOT APPLICABLE | Codex | packets/GPS_02.md | Enable attempt stopped before permission/marker because remote HTTP requires HTTPS or localhost. |
| GPS_03 | NOT APPLICABLE | Codex | packets/GPS_03.md | Follow/drift states cannot exist without a live secure-origin geolocation session. |
| GPS_04 | PASS | Codex | packets/GPS_04.md | GPS unavailable showed clear HTTPS/localhost guidance and a dismiss control over the usable map. |
| GPS_05 | NOT APPLICABLE | Codex | packets/GPS_05.md | No live marker/watch can exist on remote HTTP, so disable and update-stop behavior cannot execute. |
| SRC_01 | PASS | Codex | packets/SRC_01.md | Zurich query returned populated typed place results with district/neighborhood entries and zoom ranges. |
| SRC_02 | PASS | Codex | packets/SRC_02.md | Selecting Zürich closed search, flew to a 100 m view, and placed a centered removable marker. |
| SRC_03 | PASS | Codex | packets/SRC_03.md | Marker X removed the selected place cleanly; same Zürich map remained usable without an orphan surface. |
| SRC_04 | PASS | Codex | packets/SRC_04.md | Guaranteed-miss and empty queries both showed No matches without stale results or blank state. |
| GLB_01 | PASS | Codex | packets/GLB_01.md | Zooming out from Zürich automatically entered a fitted 2,000 km globe with the globe control visible. |
| GLB_02 | PASS | Codex | packets/GLB_02.md | Four zoom-in steps returned the 2,000 km globe to a normal flat 100 km Europe map. |
| GLB_03 | PASS | Codex | packets/GLB_03.md | Manual disable stayed flat through a low-zoom cycle; explicit toggle re-enabled the fitted globe. |
| GLB_04 | PASS | Codex | packets/GLB_04.md | Globe rotated at the outer limit; Zoom In escaped and Zoom Out returned through all available scales without trapping the map. |
| ADM_01 | PASS | Codex | packets/ADM_01.md | Desktop/mobile overview and all grouped sections were reachable; mobile section navigation and Back returned synchronized routes. |
| ADM_02 | PASS | Codex | packets/ADM_02.md | Synthetic GPX uploaded with clear success/indexing state; accepted formats, unsupported-file error, and empty-file error/disabled action all passed. |
| ADM_03 | FAIL | Codex | packets/ADM_03.md | ADM-03-P1: database FAILED synthetic GPX increased Admin's completed count; no failed state was exposed, though refresh and removed counts worked. |
| ADM_04 | PASS | Codex | packets/ADM_04.md | GPS/media queued feedback, GPS already-running response, healthy conditional not-ready handling, app recovery, and post-action map zoom passed. |
| ADM_05 | PASS | Codex | packets/ADM_05.md | Exploration Score visibly ran at 93% then settled; Duplicate Finder and Exploration Score both reached DONE 100% with post-import totals. |
| ADM_06 | PASS | Codex | packets/ADM_06.md | Hosted vector-map, GeoNames and BRouter ready details, location-search unavailable/recovery, and routing downloading/unavailable states passed; disabled did not apply. |
| ADM_07 | PASS | Codex | packets/ADM_07.md | Latest Change advanced after a disposable server change; Out of sync, Reload, Fresh data loaded, and restored In sync all passed. |
| ADM_08 | PASS | Codex | packets/ADM_08.md | Server log loaded 200 structured lines; Refresh advanced the newest timestamp and kept the full line count. |
| ADM_09 | PASS | Codex | packets/ADM_09.md | All 12 map/library/data sources appeared before and after login; Back/Close returned to Admin/log-in fallbacks and sign-in restored the map. |
| ADM_10 | PASS | Codex | packets/ADM_10.md | Both helpers were READY; gcexport and fit-export Install actions ended Done with explicit current-environment and active-version/profile results. |
| ADM_11 | PASS | Codex | packets/ADM_11.md | A selected synthetic upload remained pending with filename, size, and Upload action after closing/reopening Admin and returning to Import & sync. |
| ADM_12 | PASS | Codex | packets/ADM_12.md | Direct section URL, browser Back/Forward, mobile Back to overview, and Close all kept the visible Admin section and route synchronized. |
| SYN_01 | PASS | Codex | packets/SYN_01.md | Watcher deletion followed by the next freshness poll produced the banner over the unchanged stale 13-track map. |
| SYN_02 | PASS | Codex | packets/SYN_02.md | Banner Reload changed the map 8/13→8/12 and Statistics immediately showed populated 8-of-12 totals without hard refresh. |
| SYN_03 | FAIL | Codex | packets/SYN_03.md | Aggregate flow failed: IMP-05-P1 and DEL-03-P1 left cached Filter/Stats inconsistent after freshness Reload until normal browser reload. |
| SYN_04 | PASS | Codex | packets/SYN_04.md | Official FIT conversion produced the same freshness/cache flow as GPX and updated map, Browser, Statistics, search, and details 3→4 tracks. |
| SYN_05 | PASS | Codex | packets/SYN_05.md | Dismiss hid the banner through a second token change and poll; it reappeared after the five-minute boundary while the client remained stale. |
| SYN_06 | PASS | Codex | packets/SYN_06.md | Login made only its initial track fetches; the next freshness poll triggered no repeat and UI stayed stable at 8/12. |
| SYN_07 | PASS | Codex | packets/SYN_07.md | Processing Live and GPS SCANNING at 70% were visible; closing Admin and zooming 500→300 km worked while indexing was live. |
| APP_01 | PASS | Codex | packets/APP_01.md | Light/dark changed immediately across Admin, Filter, navigation, populated Statistics charts, text, panels, and controls; light restored without reload. |
| APP_02 | PASS | Codex | packets/APP_02.md | Visual inspection found no unreadable text; 31 visible Preferences text elements had minimum 4.47:1 light and 4.15:1 dark, none below 3:1. |
| APP_03 | PASS | Codex | packets/APP_03.md | Eight charts rendered after each no-reload switch; dark grids changed black 6%→white 6%, and light restored black 6%. |
| APP_04 | PASS | Codex | packets/APP_04.md | Dark theme and rgb(10,10,15) body persisted across reload, signed-out login, and signed-in map without re-selection. |
| APP_05 | PASS | Codex | packets/APP_05.md | In-flight 5 ms and settled 161 ms hard-refresh frames both showed the dark shell; final data-theme/background remained dark. |
| APP_06 | PASS | Codex | packets/APP_06.md | All seven map themes became CURRENT MAP under both dark and light UI themes; 14 combinations passed. |
| APP_07 | PASS | Codex | packets/APP_07.md | After browser reload, Map settings still showed OSM Dark as CURRENT MAP under Automatic source without re-selection. |
| APP_08 | PASS | Codex | packets/APP_08.md | Base 40%, tracks 65%, heatmap 40% persisted through reload; reset restored Topo Contrast and 100% default layers. |
| LOC_01 | PASS | Codex | packets/LOC_01.md | en-GB/Europe-Zurich previews and populated Statistics consistently used day/month dates, comma thousands, decimal dot, metric units, and concise durations. |
| LOC_02 | PASS | Codex | packets/LOC_02.md | en-GB→de-CH immediately updated previews and populated Statistics dates/thousands without reload or mixed-locale artifacts. |
| LOC_03 | PASS | Codex | packets/LOC_03.md | de-CH Statistics and selector persisted through reload without re-selection; en-GB restored afterward. |
| LOC_04 | PASS | Codex | packets/LOC_04.md | Zero/missing metrics, 518 km values, negative altitude/slope all rendered with units/signs; no NaN, undefined, null, or blank metric. |
| MOB_01 | BLOCKED | Codex | packets/MOB_01.md | 390×844 rendering passed, but the browser advertises no touch-event/emulation capability; real touch input cannot be enabled or attributed. |
| MOB_02 | BLOCKED | Codex | packets/MOB_02.md | Filter handle moved/snapped and sheets closed; navigation stayed fixed under pointer drags, and no touch capability exists to execute/attribute its touch path. |
| MOB_03 | PASS | Codex | packets/MOB_03.md | Charts/cards fit the 390 px document, long text used deliberate ellipsis, no visible element crossed bounds, and all map controls stayed usable. |
| MOB_04 | BLOCKED | Codex | packets/MOB_04.md | PLN_11 proved mobile pointer reroute, but the browser exposes no native touch events for tap/drag/insert attribution. |
| MOB_05 | BLOCKED | Codex | packets/MOB_05.md | Pointer gestures worked after Filter/Map/Segments and zoomed 500→50 km, but native pinch/double-tap/touch-drag cannot execute without touch capability. |
| MOB_06 | PASS | Codex | packets/MOB_06.md | Openings returned to Filter overview; catalog Apply returned to settings, and Apply filter toggled true→false→true before another selection; Q1 restored. |
| NET_01 | NOT APPLICABLE | Codex | packets/NET_01.md | Active context is display-mode browser, not standalone/minimal-ui/fullscreen; the installed-PWA-only offline criterion does not apply. |
| NET_02 | PASS | Codex | packets/NET_02.md | During an app outage Admin showed explicit unavailable/network-error cards without blanking; same-page Refresh recovered all status cards after restart. |
| NET_03 | PASS | Codex | packets/NET_03.md | Protected APIs returned 401; direct protected Admin navigation redirected to ready login, and valid sign-in restored the populated Q1 map. |
| NET_04 | NOT APPLICABLE | Codex | packets/NET_04.md | Remote target is non-loopback HTTP and Admin reports Browser mode; a service worker cannot register/activate, so no update prompt flow can exist. |
| ERR_01 | FAIL | Codex | packets/ERR_01.md | ERR-01-P2: track/media/planner/session paths were actionable, but map-config 503 silently fell back without a visible message or recovery action. |
| ERR_02 | PASS | Codex | packets/ERR_02.md | Primed segment state cleared across Planner→Filter→Animate→Segments→Map; no residual marker/listener/cursor/tool surface, and map zoom still worked. |
| UXP_01 | PASS | Codex | packets/UXP_01.md | Three journeys passed 60 input checks (150 ms worst feedback/gap); 171 first-party requests all returned 200 within 478 ms; no console error or lost interaction. |
| RUN_CLEANUP | PASS | Codex | packets/RUN_CLEANUP.md | Gate/audit passed; project containers/network/volume/directory and local artifacts removed; supplied password verified; temporary key removed. |

## Issues

Coverage statuses above preserve the result from the tested beta. Issue statuses below record the current follow-up resolution.

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| IMP-05-P1 | P1 | IMP_05 | Freshness Reload leaves Filter and Statistics at zero after first five-track import. | **FIXED — VERIFIED 2026-08-14** |
| DEL-03-P1 | P1 | DEL_03 | Freshness Reload leaves both deleted records and old totals in Filter after the 5→3 delete. | **FIXED — VERIFIED 2026-08-14** |
| TRD-15-P2 | P2 | TRD_15 | Statistics Track Details loses the originating search/result state on Close and Back. | **FIXED — VERIFIED 2026-08-14** |
| FLT-03-P2 | P2 | FLT_03 | Filter parameter edits and resets require explicit Apply instead of auto-applying. | **FIXED — VERIFIED 2026-08-14** |
| FLT-10-P2 | P2 | FLT_10 | Exact activity types are not available as filter result categories; only main groups are exposed. | **FIXED — VERIFIED 2026-08-14** |
| FLT-12-P2 | P2 | FLT_12 | Selecting every available category does not normalize to All categories. | **FIXED — VERIFIED 2026-08-14** |
| FLT-16-P2 | P2 | FLT_16 | A global Result categories change does not reset temporary map-legend hiding. | **FIXED — VERIFIED 2026-08-14** |
| TBS-11-P2 | P2 | TBS_11 | The excluded-highlight count opens an empty cached Excluded view until browser reload. | **FIXED — VERIFIED 2026-08-14** |
| MCT-05-P1 | P1 | MCT_05 | A-B comparison extracts a zero-distance endpoint line from a timed segment. | **FIXED — VERIFIED 2026-08-14** |
| ADM-03-P1 | P1 | ADM_03 | Admin Processing counts a database FAILED GPS import as completed and exposes no failed state. | **FIXED — VERIFIED 2026-08-14** |
| ERR-01-P2 | P2 | ERR_01 | Map-config failure silently falls back without an actionable end-user message. | **FIXED — VERIFIED 2026-08-14** |

## Final Assembly Notes

- Missing coverage IDs: none; all 193 frozen coverage IDs have terminal packet results.
- Cleanup state: PASS; scoped remote stack/data/access and registered local artifacts removed and verified.
- Final report path: `report.md`.
- Finalization gate: PASS (193 coverage IDs terminal), checked before RUN_CLEANUP and report assembly.
- Early closure approval: not requested or used.
