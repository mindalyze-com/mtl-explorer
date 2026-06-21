# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-06-20_2114-beta-178-full-regression |
| Target server | 178.104.209.132 |
| SSH user | root |
| Source | GitHub main quick install, with app image override `wauwau0977/mytraillog:beta` |
| App URL | http://178.104.209.132:18080/mtl/ |
| Started | 2026-06-20T21:14:31+0200 CEST |
| Coordinator | Codex |

## Shared Facts

- README facts: prerequisite Docker Engine plus Docker Compose plugin; quick install downloads `docker-compose.yml` from GitHub main, runs `docker compose up -d`, serves `http://localhost:18080/mtl/`, and imports tracks from `./data/gpx/`.
- Login credentials source: README quick-start credentials `mtl` / `change-me`.
- Import folder: documented watched import folder `./data/gpx/`, expected remote path under the disposable install directory after setup.
- Browser contexts: desktop and narrow mobile/touch browser contexts against `http://178.104.209.132:18080/mtl/`; installed-PWA-only offline coverage to be handled per `NET_01`.
- Synthetic MCT/AVR tracks: MCT_04 uploaded fully synthetic shared-zone tracks `100017` (`synthetic-mct04-shared-a.gpx`) and `100018` (`synthetic-mct04-shared-b.gpx`); stable trigger points are A `(7.4200, 46.9700)`, B `(7.4900, 47.0400)`, radius `700 m`.
- Synthetic Admin uploads: ADM_02 uploaded fully synthetic tracks `100019` (`ADM_02-adm-upload-20260621020618.gpx`, 5 points) and `100020` (`ADM_02-adm-upload-success-20260621021405.gpx`, 12 points); GPS indexer settled at total 18, completed 16, removed 2, pending 0, failed 0.
- Synthetic freshness uploads: SYN_01 uploaded fully synthetic track `100021` (`SYN_01-freshness-import-20260621023820.gpx`, 10 points) to trigger the data-freshness banner; SYN_02 uploaded fully synthetic track `100022` (`SYN_02-reload-refresh-20260621024035.gpx`, 11 points) and verified banner Reload updated map/stat caches to `14 Tracks`; SYN_04 uploaded duplicate public FIT track `100023` and final synthetic non-duplicate FIT track `100024` (`SYN_04-synthetic-fit-20260621024711.fit`, 20 points), verifying banner Reload updated map/stat caches to `15 Tracks`.
- Synthetic indexer-running upload: SYN_07 uploaded fully synthetic GPX track `100025` (`SYN_07-indexer-running-20260621050700.gpx`, 20,000 points); GPS indexer and background jobs settled pending `0`, and the synced map shows `16 Tracks`.
- Locale state: LOC_01 set the desktop browser context format locale to `de-CH`; LOC_02 changed it through Admin Settings to `en-US`; LOC_03 verified `en-US` persisted across reload.
- Boundary rendering note: LOC_04 used a page-local API response override for Track 100024 null-elevation fields only; no server data was changed.
- Mobile context: MOB_01 saved `assets/browser-state-mobile.json` from a 390x844 `isMobile`/`hasTouch` context.
- Known constraints: target is remote plain HTTP, so live geolocation permission rows may be `NOT APPLICABLE` per `GPS_01`; SSH credential is intentionally not recorded in this file.

## Queue

- Source queue: `documentation/testing/frontend-regression-test-plan.md`
- Current coverage ID: RUN_CLEANUP
- Next coverage ID: COMPLETE

Track active, blocked, failed, and recently completed IDs here. Completed packet
files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | packets/RUN_SETUP.md | Installed quick-start stack with beta app image override; app reachable remotely. |
| ACC_01 | PASS | Codex | packets/ACC_01.md | Test plan has 175 coverage IDs; run-state has 175 matching coverage rows. |
| ACC_02 | PASS | Codex | packets/ACC_02.md | Queue has no parent-section shortcut rows. |
| ACC_03 | PASS | Codex | packets/ACC_03.md | Completed packets contain detailed action/expected/actual/status/evidence tables; final report remains deferred. |
| ACC_04 | PASS | Codex | packets/ACC_04.md | Working login and signed-in map screenshots captured as compact WebP assets. |
| ACC_05 | PASS | Codex | packets/ACC_05.md | Known constraints recorded explicitly for later packet decisions. |
| DAT_01 | PASS | Codex | packets/DAT_01.md | Five public GPX files staged; all contain real trkpt sequences. |
| DAT_02 | PASS | Codex | packets/DAT_02.md | All five GPX files are fully timestamped. |
| DAT_03 | PASS | Codex | packets/DAT_03.md | Source metadata complete; GPX and FIT imported IDs/names recorded. |
| DAT_04 | PASS | Codex | packets/DAT_04.md | Used the suggested `gps-touring/sample-gpx` raw GPX files. |
| DAT_05 | PASS | Codex | packets/DAT_05.md | Garmin public `Activity.fit` has 3601 GPS-bearing record messages. |
| DAT_06 | PASS | Codex | packets/DAT_06.md | Positive evidence set excludes waypoint-only GPX and non-GPS FIT files. |
| DAT_07 | PASS | Codex | packets/DAT_07.md | Synthetic shared-segment GPX pair staged outside import folder for later MCT/AVR checks. |
| IMP_01 | PASS | Codex | packets/IMP_01.md | Baseline recorded: 0 tracks, zero stats, freshness tracks/index 0, GPS indexer settled. |
| IMP_02 | PASS | Codex | packets/IMP_02.md | Five public GPX files copied into watched import folder; checksums match staged sources. |
| IMP_03 | PASS | Codex | packets/IMP_03.md | Watched-folder import indexed five GPX files; no manual rescan needed. |
| IMP_04 | PASS | Codex | packets/IMP_04.md | Five GPS imports completed with zero failures; freshness changed; jobs settled 5/5. |
| IMP_05 | PASS | Codex | packets/IMP_05.md | Helper reload verified map/filter/stats/track-browser surfaces with five imported tracks. |
| IMP_06 | PASS | Codex | packets/IMP_06.md | Each imported GPX name verified in track-browser search, stats, map/filter counts; ID mapping recorded. |
| IMP_07 | PASS | Codex | packets/IMP_07.md | Rendered map clicks opened details/selection for all five GPX tracks; no stale/duplicated geometry observed. |
| IMP_08 | PASS | Codex | packets/IMP_08.md | Baseline 0 to current 5 tracks; source-to-track mapping recorded. |
| IMP_09 | PASS | Codex | packets/IMP_09.md | Totals, activity breakdown, rankings, periods, browser summary, and heatmap density changed from baseline. |
| DEL_01 | PASS | Codex | packets/DEL_01.md | Deleted VoieVerteHauteVosges.gpx and Lannion_Plestin_parcours24.4RE.gpx from watched folder. |
| DEL_02 | PASS | Codex | packets/DEL_02.md | Delete processed automatically; visible track count 3, GPS removed 2, pending/failed 0. |
| DEL_03 | PASS | Codex | packets/DEL_03.md | Deleted tracks absent from map/browser/filter/heatmap/stats/related surfaces; count is 3. |
| DEL_04 | PASS | Codex | packets/DEL_04.md | Remaining three GPX tracks display and open from track-browser search rows. |
| DEL_05 | PASS | Codex | packets/DEL_05.md | Deletion flow judged on user-visible frontend surfaces, not stale URL/API probes. |
| FIT_01 | PASS | Codex | packets/FIT_01.md | Public Garmin FIT file copied into watched import folder with matching checksum. |
| FIT_02 | PASS | Codex | packets/FIT_02.md | Activity.fit imported as Track 100005; map/browser/stats show four tracks. |
| FIT_03 | PASS | Codex | packets/FIT_03.md | Detail tabs render; retest rejected FIT-03-P2 because the recorded failed click was off-route, and clicking the FIT mini-map route line opened a Track point popup. |
| FIT_04 | PASS | Codex | packets/FIT_04.md | Download original returned `Activity.fit` with matching public-source checksum and `.FIT` marker. |
| FIT_05 | PASS | Codex | packets/FIT_05.md | Download GPX returned `Activity.gpx` with 3,601 `trkpt` elements and no waypoints. |
| FIT_06 | NOT APPLICABLE | Codex | packets/FIT_06.md | FIT conversion is available; unavailable-conversion condition did not occur. |
| FMT_01 | PASS | Codex | packets/FMT_01.md | Synthetic `.gpx`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, `.gdb` uploaded and indexed; `.fit` covered by FIT_02. |
| FMT_02 | PASS | Codex | packets/FMT_02.md | Non-GPX format matrix passed for conversion, map/detail/graphs, stats, original downloads, and GPX downloads. |
| SGN_01 | PASS | Codex | packets/SGN_01.md | Clean browser context redirects to `/mtl/login` with login controls. |
| SGN_02 | PASS | Codex | packets/SGN_02.md | Valid README credentials reach `/mtl/` with map canvases and nav controls. |
| SGN_03 | PASS | Codex | packets/SGN_03.md | Invalid password stays on `/mtl/login` with `Invalid username or password.` |
| SGN_04 | NOT APPLICABLE | Codex | packets/SGN_04.md | Demo mode inactive according to `/api/auth/demo-status`; no demo banner expected. |
| SGN_05 | PASS | Codex | packets/SGN_05.md | UI logout returns to login; valid re-login returns to map. |
| SGN_06 | PASS | Codex | packets/SGN_06.md | Splash `LOADING YOUR TRAILS` shown during delayed startup and replaced by loaded map. |
| SGN_07 | PASS | Codex | packets/SGN_07.md | Aborted startup APIs produce an unable-to-load message with Retry instead of frozen splash. |
| SGN_08 | PASS | Codex | packets/SGN_08.md | About dialog and page titles use `MTL Explorer`. |
| SGN_09 | PASS | Codex | packets/SGN_09.md | Browser back/forward across Map, Stats, and Admin worked without console/page errors. |
| MAP_01 | PASS | Codex | packets/MAP_01.md | Base map canvases, map config/status, style/sprite/tile requests, attribution, and controls rendered. |
| MAP_02 | PASS | Codex | packets/MAP_02.md | API exposes 12 visible successful tracks; map renders viewport-visible legend and source set excludes deleted tracks. |
| MAP_03 | PASS | Codex | packets/MAP_03.md | Import reload evidence shows newly imported tracks on map/filter/stats/browser without browser restart. |
| MAP_04 | PASS | Codex | packets/MAP_04.md | Deletion evidence shows removed tracks absent from map, selection, browser, filter, heatmap, stats, and related surfaces. |
| MAP_05 | PASS | Codex | packets/MAP_05.md | Zoom/detail check on track 100000 showed fine geometry has more points than coarse geometry and map remained intact. |
| MAP_06 | PASS | Codex | packets/MAP_06.md | UI settled after stress; external Mapterhorn DEM tile 404 was triaged as a rejected/non-blocking provider issue. |
| MAP_07 | BLOCKED | Codex | packets/MAP_07.md | Could not obtain valid visible high-zoom point-vertex evidence through browser-accessible map controls. |
| MAP_08 | PASS | Codex | packets/MAP_08.md | IMP_07 map-click pass opened single-track detail pages from rendered geometries. |
| MAP_09 | PASS | Codex | packets/MAP_09.md | IMP_07 overlap click opened a two-track selection popup and selecting one opened details. |
| MAP_10 | PASS | Codex | packets/MAP_10.md | Current overlap selection closed cleanly back to the normal 8-track map state. |
| MAP_11 | BLOCKED | Codex | packets/MAP_11.md | Direct marker-click evidence blocked by the MAP_07 inability to target visible canvas-rendered track-point markers. |
| MAP_12 | PASS | Codex | packets/MAP_12.md | Swiss Mobility nearby-routes popup opened with official route names/numbers and closed cleanly. |
| MAP_13 | NOT APPLICABLE | Codex | packets/MAP_13.md | Live config is `tileMode: local`; intentional server-level remote raster mode is not active. |
| MAP_14 | PASS | Codex | packets/MAP_14.md | Page-local PMTiles abort triggered runtime remote raster fallback while tracks, selection, pan, and zoom stayed usable. |
| MAP_15 | PASS | Codex | packets/MAP_15.md | Manual Remote source used remote tiles only, hid Swiss vector themes, persisted after reload, and Reset restored Auto. |
| TRD_01 | PASS | Codex | packets/TRD_01.md | Opened GPX `100001` from map selection and FIT `100005` from Stats > Tracks search. |
| TRD_02 | PASS | Codex | packets/TRD_02.md | FIT-backed Track 100005 detail surfaces loaded: overview, graphs, quality, related, events, and mini-map. |
| TRD_03 | PASS | Codex | packets/TRD_03.md | Track 100005 detail tabs loaded without blank states or request loops. |
| TRD_04 | PASS | Codex | packets/TRD_04.md | Track 100005 Graphs tab shows readable Speed, Elevation, Elevation Gain Rate, and Distance over Time charts with labels, units, and axis values. |
| TRD_05 | PASS | Codex | packets/TRD_05.md | Graphs tab controls updated x-axis mode, range bands, point count, and graph height without chart errors or detected layout breakage. |
| TRD_06 | PASS | Codex | packets/TRD_06.md | Chart-to-mini-map and mini-map-to-chart hover sync worked; leaving each surface cleared marker/crosshair/tooltip artifacts. |
| TRD_07 | PASS | Codex | packets/TRD_07.md | Shape preview SVG paths rendered in stats overview, track browser, filter track picker, related tracks, and map selection list. |
| TRD_08 | PASS | Codex | packets/TRD_08.md | Original GPX and FIT downloads matched recorded public-source filenames, sizes, and SHA-256 checksums. |
| TRD_09 | PASS | Codex | packets/TRD_09.md | Download-as-GPX produced valid track GPX files for FIT-backed track 100005 and IGC-backed track 100009. |
| TRD_10 | PASS | Codex | packets/TRD_10.md | Track 100005 activity changed Walking to Bicycle; energy changed 346.7 Wh/298 kcal to 395.1 Wh/340 kcal; restored to Walking. |
| TRD_11 | PASS | Codex | packets/TRD_11.md | Energy rider-weight what-if changed preview 346.7 Wh to 439.1 Wh at 95 kg, no save POST occurred, direct reload stayed at 346.7 Wh/Walking. |
| TRD_12 | PASS | Codex | packets/TRD_12.md | Statistics exclusion changed Stats Overview 8 tracks/955 km to 7 tracks/952 km, then restore returned to 8 tracks/955 km. |
| TRD_13 | PASS | Codex | packets/TRD_13.md | Track 100009 Related tab showed Previous 4, Next 3, Duplicates 4; clicking next row navigated to track 100008. |
| TRD_14 | PASS | Codex | packets/TRD_14.md | Track 100000 detected STOP rendered as Break 1; selection highlighted mini-map and deselection cleared selected state. |
| FLT_01 | BLOCKED | Codex | packets/FLT_01.md | No previously saved active filter existed: standard SmartBaseFilter with empty params/palette, filter toggle Off, and map chip `8 Tracks` with no funnel. |
| FLT_02 | PASS | Codex | packets/FLT_02.md | Catalog has 18 filters across Core, Activity, Date & Time, Performance, and Quality; search for `speed` narrowed results; Performance chip showed four rows. |
| FLT_03 | PASS | Codex | packets/FLT_03.md | `Activities by keyword` with `Path` applied immediately to 2/8 tracks across panel/map/stats; clearing restored 8/8 without loading/error state. |
| FLT_04 | FIXED | Codex | packets/FLT_04.md | FIXED locally: geo draw/clear now persists the current filter draft, including `geoCircles`; focused filter-store tests and type-check passed. Evidence: assets/FIXED-filter-planner-local-verification.txt. |
| FLT_05 | FIXED | Codex | packets/FLT_05.md | FIXED locally through the same geo-shape persistence change as FLT_04; saved shapes now have a persisted client-config source to reappear in later sessions. Evidence: assets/FIXED-filter-planner-local-verification.txt. |
| FLT_06 | PASS | Codex | packets/FLT_06.md | Clearing keyword restored 8 tracks; applying `Path` live-updated panel/map legend/Stats to 2/8 tracks and CYCLING count 2. |
| FLT_07 | PASS | Codex | packets/FLT_07.md | CYCLING legend hide changed map chip 2/8 to 0/8; restoring CYCLING returned 2/8. |
| FLT_08 | PASS | Codex | packets/FLT_08.md | Switching filter Off restored map to `8 Tracks` with no funnel and Stats to 8 tracks / 955 km. |
| TBS_01 | PASS | Codex | packets/TBS_01.md | Stats > Tracks shows 8 rows with Start, Track, Activity, Distance, Duration, Avg km/h, Energy, Exploration, and Imported columns. |
| TBS_02 | PASS | Codex | packets/TBS_02.md | Search matched name, description, date, distance, duration, activity, and file path (`Activity.fit`) queries. |
| TBS_03 | PASS | Codex | packets/TBS_03.md | All sortable columns produced non-empty sorted states; `Path` search summary changed to `2 of 8 tracks · 6.63 km · 0m 00s`. |
| TBS_04 | PASS | Codex | packets/TBS_04.md | Quick views switched through All, Excluded, Stats excluded, and No activity; empty subsets rendered correctly, returning All restored 8 rows, and `Path` search plus Distance sort showed 2 of 8 tracks. |
| TBS_05 | PASS | Codex | packets/TBS_05.md | Searching `Activity.fit` showed one row; clicking Track 100005 opened `/mtl/track/100005` with visible detail header, source filename, tabs, and no load error. |
| TBS_06 | PASS | Codex | packets/TBS_06.md | Overview showed 8 tracks, distance, duration, ascent, activity breakdown, highlights, most active periods, milestones; Trends rendered 7 chart containers and summary tiles. |
| TBS_07 | PASS | Codex | packets/TBS_07.md | Empty `[]`, single `[100005]`, and live 8-track statistics sets returned expected server-owned totals; current many-track UI and prior empty-install UI baseline were cross-checked. |
| TBS_08 | PASS | Codex | packets/TBS_08.md | IMP_09 proved five-GPX import stats delta, DEL_03 proved two-GPX delete stats delta, and current deleted-name searches for `voie verte`/`Lannion` show `0 of 8 tracks`. |
| TBS_09 | PASS | Codex | packets/TBS_09.md | Trends switched daily, weekly, and monthly groupings; each rendered 7 chart containers with expected period labels and stable 8-track totals. |
| TBS_10 | PASS | Codex | packets/TBS_10.md | Most Active Period row opened active drilldown; Recent Activity row opened `/mtl/track/100012` detail with tabs visible. |
| TBS_11 | PASS | Codex | packets/TBS_11.md | `Longest track` opened an 8-row highlight drilldown, first row opened `/mtl/track/100002`, and excluded-highlight count was not applicable because API count was 0. |
| TBS_12 | PASS | Codex | packets/TBS_12.md | Temporary SmartBaseFilter geo circle resolved 7 IDs; Map, Overview, Trends, Browser, and API totals agreed on 7 tracks / 952 km / 19h 04m, then standard filter restore returned 8 tracks / 955 km. |
| PLN_01 | PASS | Codex | packets/PLN_01.md | Planner opened with Drawing/Load tabs; routing options were visible and profile changed from Hiking to Road Bike. |
| PLN_02 | PASS | Codex | packets/PLN_02.md | Zoomed in and clicked two map points; planner route API returned 200 and live stats showed 491.75 km, 678 m ascent, 947 m descent, 23h 28m, 1 leg. |
| PLN_03 | BLOCKED | Codex | packets/PLN_03.md | Route creation worked, but route-hit insertion/drag could not be reliably targeted from Playwright; midpoint and visible-line clicks appended, projected clicks missed, and sheet repositioning closed the planner. |
| PLN_04 | PASS | Codex | packets/PLN_04.md | Dragging a waypoint issued a changed two-waypoint route request; delete marker reset legs to 0; undo/redo restored and removed the route; clear/undo/redo also reset/restored/cleared the route. |
| PLN_05 | PASS | Codex | packets/PLN_05.md | Live stats changed from zero to routed 555.03 km / 688 m / 26h 24m / 1 leg, reset on clear, restored on undo, and reset again on redo. |
| PLN_06 | PASS | Codex | packets/PLN_06.md | Elevation profile rendered; hovering the chart created a visible `.planner-hover-marker` at map rect `{x:334,y:195,width:14,height:14}`, and leaving the chart removed it. |
| PLN_07 | PASS | Codex | packets/PLN_07.md | Temporary plan saved as ID 100014, listed once, loaded with route stats, deleted through UI, and confirmed absent from `/api/planner/plans`. |
| PLN_08 | PASS | Codex | packets/PLN_08.md | Temporary plan ID 100015 exported as GPX; 2,578 GPX trackpoints matched 2,578 saved coordinates and first/last endpoints; plan deleted afterward. |
| PLN_09 | PASS | Codex | packets/PLN_09.md | Intercepted `segment-downloading` route response showed clear auto-retry notice with no page error; stale-notice issue PLN-09-P3 is FIXED locally. |
| PLN_10 | PASS | Codex | packets/PLN_10.md | Saved route ID 100016 remained visible with stats/chart while an intercepted new route request showed segment-downloading; temporary plan deleted afterward. |
| PLN_11 | PASS | Codex | packets/PLN_11.md | Mobile touch taps computed a 2.91 km route; CDP touch drag moved the waypoint and recomputed to 65.62 km with changed waypoint coordinates. |
| MCT_01 | PASS | Codex | packets/MCT_01.md | Segment Analyzer found 1 shared track (`MoselradwegAusWiki.gpx`) across A/B zones; speed, time, and distance metrics rendered. |
| MCT_02 | PASS | Codex | packets/MCT_02.md | Result link `MoselradwegAusWiki.gpx` opened `/mtl/track/100002` Track Details with sub-track API calls. |
| MCT_03 | PASS | Codex | packets/MCT_03.md | Stopping Segment Analyzer removed temporary overlay/flow nodes and no post-stop analyzer-radius zone calls returned. |
| MCT_04 | PASS | Codex | packets/MCT_04.md | Synthetic tracks 100017/100018 compared; result table, mini-map, charts, and live sub-track requests rendered. |
| MCT_05 | PASS | Codex | packets/MCT_05.md | Sub-track slices for synthetic tracks 100017/100018 returned inclusive local A-B point ranges with monotonic time/distance. |
| MCT_06 | PASS | Codex | packets/MCT_06.md | Compare mini-map geometry for synthetic A-B segment stayed within local Bern bounds with no zero/off-continent jump. |
| AVR_01 | PASS | Codex | packets/AVR_01.md | Animate speed/play/pause/resume/stop controls worked on current 10-track set. |
| AVR_02 | PASS | Codex | packets/AVR_02.md | Two-racer virtual race progressed from 0/0 to 4/3 then 68/50 and paused with progress visible. |
| AVR_03 | PASS | Codex | packets/AVR_03.md | Race reset/close left map zoom and Map settings navigation usable with no stuck Race/Segment sheets. |
| AVR_04 | PASS | Codex | packets/AVR_04.md | Running virtual race geometry stayed within local Bern bounds with no zero/off-continent jump. |
| MED_01 | BLOCKED | Codex | packets/MED_01.md | No indexed media exists; no media upload/create API; current SSH/filesystem access unavailable to seed synthetic media. |
| MED_02 | BLOCKED | Codex | packets/MED_02.md | No indexed media exists, so viewport-scoped media loading cannot be exercised. |
| MED_03 | BLOCKED | Codex | packets/MED_03.md | No indexed media pins exist to click; no current media ingestion path is available. |
| MED_04 | BLOCKED | Codex | packets/MED_04.md | No indexed HEIC media exists and current filesystem access is unavailable to seed one. |
| MED_05 | BLOCKED | Codex | packets/MED_05.md | No indexed media record exists to break/restore; current filesystem access unavailable to seed one. |
| HMO_01 | PASS | Codex | packets/HMO_01.md | Heatmap toggled on, opacity adjusted to 50, and GPS Tracks stayed enabled/visible. |
| HMO_02 | PASS | Codex | packets/HMO_02.md | All Waymarked and Swiss overlay rows toggled independently, opacity set to 70, GPS Tracks stayed enabled. |
| HMO_03 | PASS | Codex | packets/HMO_03.md | Heatmap stayed enabled while `Activities by keyword=Path` changed the map from 10 tracks to 2/10 tracks. |
| GPS_01 | NOT APPLICABLE | Codex | packets/GPS_01.md | Remote plain-HTTP target is not a secure context; GPS shows HTTPS/localhost requirement. |
| GPS_02 | NOT APPLICABLE | Codex | packets/GPS_02.md | Permission prompt and locate marker cannot be exercised on remote plain HTTP; app shows secure-origin warning. |
| GPS_03 | NOT APPLICABLE | Codex | packets/GPS_03.md | Follow-me cannot start because remote plain HTTP prevents live GPS. |
| GPS_04 | NOT APPLICABLE | Codex | packets/GPS_04.md | Permission-denied path cannot be exercised on remote plain HTTP; app shows secure-origin warning. |
| GPS_05 | NOT APPLICABLE | Codex | packets/GPS_05.md | GPS never starts on remote plain HTTP, so no marker/watch exists to disable. |
| SRC_01 | PASS | Codex | packets/SRC_01.md | Zurich search returned 20 visible results and the map remained usable. |
| SRC_02 | PASS | Codex | packets/SRC_02.md | Selecting the first Zurich result placed one location marker with a clear button. |
| SRC_03 | PASS | Codex | packets/SRC_03.md | Marker clear button removed the location search marker and map stayed usable. |
| SRC_04 | PASS | Codex | packets/SRC_04.md | Nonsense query showed `No matches`, zero result rows, and no marker. |
| GLB_01 | PASS | Codex | packets/GLB_01.md | Zooming out auto-engaged globe at `1000 km` scale with 10 tracks visible. |
| GLB_02 | PASS | Codex | packets/GLB_02.md | Zooming in returned to flat map at `300 km` scale with globe inactive/hidden. |
| GLB_03 | PASS | Codex | packets/GLB_03.md | Manual globe disable stayed inactive across zoom changes until the toggle was clicked again. |
| GLB_04 | PASS | Codex | packets/GLB_04.md | Low-zoom edge did not trap the map; zooming back in recovered flat mode. |
| ADM_01 | PASS | Codex | packets/ADM_01.md | Admin workspace opened and expected panel tiles were reachable. |
| ADM_02 | PASS | Codex | packets/ADM_02.md | Unsupported and empty-file validation worked; valid synthetic GPX uploads showed progress/success and indexed as tracks 100019/100020 with GPS pending 0. |
| ADM_03 | PASS | Codex | packets/ADM_03.md | Jobs panel showed GPS/MEDIA indexer rows and Refresh changed the updated timestamp while API status stayed settled. |
| ADM_04 | PASS | Codex | packets/ADM_04.md | Rescan GPS and Media queued successfully, indexers settled, and map zoom remained usable with 12 tracks. |
| ADM_05 | PASS | Codex | packets/ADM_05.md | Duplicate Finder, Activity Classifier, and Exploration Score were visible and settled at 16/16, 100%. |
| ADM_06 | PASS | Codex | packets/ADM_06.md | Vector map tiles, location search, and routing segments showed ready/done states with useful details. |
| ADM_07 | PASS | Codex | packets/ADM_07.md | Freshness showed timestamps/tokens, offered Reload when out of sync, and returned to matching tokens after Reload. |
| ADM_08 | PASS | Codex | packets/ADM_08.md | Server log loaded and refreshed; API returned 79 log lines before and after refresh. |
| ADM_09 | PASS | Codex | packets/ADM_09.md | Attribution listed expected map/data/library sources including OSM, GeoNames, MapLibre, Protomaps, and BRouter. |
| ADM_10 | PASS | Codex | packets/ADM_10.md | Garmin helper tools were ready; gcexport and fit-export install/update actions returned clear already-present/update output. |
| ADM_11 | PASS | Codex | packets/ADM_11.md | Closing/reopening Helpers while fit-export install was pending retained completed output and ready tool status. |
| SYN_01 | PASS | Codex | packets/SYN_01.md | Server-side synthetic upload changed freshness; open map showed `New data available` with Reload/Dismiss while stale at 12 tracks. |
| SYN_02 | PASS | Codex | packets/SYN_02.md | Banner Reload refreshed cached map and Stats from 13 to 14 tracks after synthetic upload 100022. |
| SYN_03 | PASS | Codex | packets/SYN_03.md | Required five-GPX import/delete flow audited against direct IMP/DEL evidence and current API state; GPS removed=2, retained files active. |
| SYN_04 | PASS | Codex | packets/SYN_04.md | Synthetic non-duplicate FIT import changed freshness; banner Reload refreshed map and Stats from 14 to 15 tracks. |
| SYN_05 | PASS | Codex | packets/SYN_05.md | Dismiss hid the banner, stayed hidden through another freshness change/poll cycle, and reappeared after 315s. |
| SYN_06 | PASS | Codex | packets/SYN_06.md | Credentials-only logout/login returned to a stable 15-track map without a repeated freshness reload/navigation loop. |
| SYN_07 | PASS | Codex | packets/SYN_07.md | GPS pending state surfaced Admin/Jobs badges while map zoom remained usable; synthetic upload 100025 settled and synced to 16 tracks. |
| APP_01 | PASS | Codex | packets/APP_01.md | Admin Settings theme switch changed data-theme and 8 sampled surfaces immediately; dark dropdown rendered without errors. |
| APP_02 | PASS | Codex | packets/APP_02.md | Visible text scan found zero low-contrast samples in both light and dark themes across map/Admin Settings surfaces. |
| APP_03 | PASS | Codex | packets/APP_03.md | Stats Trends charts recolored after a UI dark-mode switch without page reload; 8 Highcharts SVGs rendered before and after. |
| APP_04 | PASS | Codex | packets/APP_04.md | Dark theme persisted across reload, credentials-only logout screen, and re-login. |
| APP_05 | PASS | Codex | packets/APP_05.md | Hard-refresh sampler recorded zero light/non-dark rendered app samples before dark map appeared. |
| APP_06 | PASS | Codex | packets/APP_06.md | All seven map styles selected successfully under both light and dark UI themes while keeping canvases rendered. |
| APP_07 | PASS | Codex | packets/APP_07.md | Stored map theme `dark` and active `OSM Dark` tile persisted across reload. |
| APP_08 | PASS | Codex | packets/APP_08.md | Base Map/GPS/Heatmap opacity sliders saved, persisted after reload, dimmed basemap, and Reset restored defaults. |
| LOC_01 | PASS | Codex | packets/LOC_01.md | `de-CH` locale rendered Swiss date/number formatting in Settings and Stats without visible `NaN`/`undefined`. |
| LOC_02 | PASS | Codex | packets/LOC_02.md | Switching `de-CH` to `en-US` updated Settings and Stats formatting through SPA navigation with unchanged `performance.timeOrigin`. |
| LOC_03 | PASS | Codex | packets/LOC_03.md | `en-US` persisted across browser reload and continued formatting Settings and Stats after reload. |
| LOC_04 | PASS | Codex | packets/LOC_04.md | Zero, large, negative, and simulated null-elevation values rendered as readable values/fallbacks without visible `NaN`, `undefined`, or `null`. |
| MOB_01 | PASS | Codex | packets/MOB_01.md | 390x844 touch/coarse mobile context rendered map canvases, `16 Tracks`, mobile nav tools, and zero horizontal overflow. |
| MOB_02 | PASS | Codex | packets/MOB_02.md | Navigation sheet snapped 132px/46px/132px; Stats bottom sheet opened, dragged to ~743px, snapped back, and closed with zero open sheets. |
| MOB_03 | PASS | Codex | packets/MOB_03.md | Mobile track cards, Trends charts, and map zoom controls stayed usable with zero document overflow; long card text used controlled ellipsis. |
| MOB_04 | BLOCKED | Codex | packets/MOB_04.md | Mobile touch placement and drag worked, but route-line insertion could not be proven; attempted route-line tap appended rather than inserting by request order. |
| MOB_05 | PASS | Codex | packets/MOB_05.md | After each mobile tool, drag kept the map valid, double-tap changed `3000 km` to `2000 km`, and pinch changed back to `3000 km` without errors. |
| NET_01 | NOT APPLICABLE | Codex | packets/NET_01.md | Installed-PWA offline reload check does not apply to this normal browser-tab run; display mode was browser, not standalone. |
| NET_02 | PASS | Codex | packets/NET_02.md | Induced API failures showed a nonblank Retry state; restoring requests and clicking Retry returned to `16 Tracks`. |
| NET_03 | PASS | Codex | packets/NET_03.md | Simulated 401 and 403 auth-probe responses both redirected to `/mtl/login`, showed session-expired login controls, and cleared local JWT in isolated contexts. |
| NET_04 | NOT APPLICABLE | Codex | packets/NET_04.md | Service-worker update prompt check is installed-PWA-only; this remote HTTP normal tab had no service-worker support or registration. |
| ERR_01 | PASS | Codex | packets/ERR_01.md | Track load, map config/startup, media preview, planner route, and expired-session failures all showed actionable nonblank recovery states. |
| ERR_02 | PASS | Codex | packets/ERR_02.md | After 24 rapid tool opens, sheets closed cleanly, stale marker/popup counts were zero, cursors were normal, and zoom control changed scale from `500 km` to `200 km`. |
| RUN_CLEANUP | BLOCKED | Codex | packets/RUN_CLEANUP.md | Evidence audit passed, but SSH cleanup is blocked because password and key-based SSH access are rejected after setup-time password rotation; current root credential required. |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| FIT-03-P2 | P2 | FIT_03 | FIT-backed detail mini-map point click does not open a point popup. | REJECTED |
| MAP-06-P3 | P3 | MAP_06 | Fast pan/zoom triggers repeated remote tile 404 responses. | REJECTED |
| FLT-04-P2 | P2 | FLT_04 | Geo circle parameter is not persisted or re-applied after reload. | FIXED |
| PLN-09-P3 | P3 | PLN_09 | Clearing a failed planner route leaves the stale segment-downloading notice visible. | FIXED |

## Final Assembly Notes

- Missing coverage IDs: none; finalization gate passed for all 175 coverage IDs.
- Cleanup state: blocked; stack shutdown and disposable-directory removal not verified because current root SSH credential is unavailable.
- Final report path: `documentation/testing/full-regression/test_runs/2026-06-20_2114-beta-178-full-regression/report.md` created with `RESULT: FAIL` while cleanup remains blocked.
- Finalization gate: PASS (175 coverage IDs terminal).
- Early closure approval: none.
