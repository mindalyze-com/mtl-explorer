# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-06-01_1727-beta-201 |
| Target server | 167.233.16.201 |
| Source | GitHub main quick install with Docker image tag override `wauwau0977/mytraillog:beta` |
| App URL | http://167.233.16.201:18080/mtl/ |
| Started | 2026-06-01T17:27:47+02:00 |
| Coordinator | Codex |

## Shared Facts

- README facts: Docker Engine plus Compose plugin required; quick start downloads `docker-compose.yml`, starts `docker compose up -d`, app path `http://localhost:18080/mtl/`, login `mtl` / `change-me`, imports via `./data/gpx/`. User requested Docker BETA tag `wauwau0977/mytraillog:beta` instead of latest.
- Login credentials source: root SSH credentials supplied in user prompt for server access; GUI credentials from README quick start only (`mtl` / `change-me`).
- Import folder: `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer/data/gpx/` on target server, mounted to `/app/gpx`.
- Browser contexts: Codex in-app browser desktop baseline; narrow mobile/touch viewport still required later.
- Current shared track count: 12 visible tracks after LOC_04 cleanup removed all temporary `loc04_*.gpx` files.
- Known constraints: Remote plain-HTTP origin means browser geolocation secure-origin checks are expected NOT APPLICABLE unless tunneled/HTTPS; installed-PWA offline coverage may be NOT APPLICABLE if not installed as web app.

## Queue

- Source queue: `documentation/testing/frontend-regression-test-plan.md`
- Current coverage ID: COMPLETE
- Next coverage ID: COMPLETE

Track active, blocked, failed, and recently completed IDs here. Completed packet files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | packets/RUN_SETUP.md | Quick install completed; app reachable remotely; login baseline passed. |
| ACC_01 | PASS | Codex | packets/ACC_01.md | Accounting requirement satisfied with direct run artifact evidence. |
| ACC_02 | PASS | Codex | packets/ACC_02.md | Accounting requirement satisfied with direct run artifact evidence. |
| ACC_03 | PASS | Codex | packets/ACC_03.md | Accounting requirement satisfied with direct run artifact evidence. |
| ACC_04 | PASS | Codex | packets/ACC_04.md | Accounting requirement satisfied with direct run artifact evidence. |
| ACC_05 | PASS | Codex | packets/ACC_05.md | Accounting requirement satisfied with direct run artifact evidence. |
| DAT_01 | PASS | Codex | packets/DAT_01.md | Public data staged and manifest evidence recorded. |
| DAT_02 | PASS | Codex | packets/DAT_02.md | Public data staged and manifest evidence recorded. |
| DAT_03 | PASS | Codex | packets/DAT_03.md | Public data staged and manifest evidence recorded. |
| DAT_04 | PASS | Codex | packets/DAT_04.md | Public data staged and manifest evidence recorded. |
| DAT_05 | PASS | Codex | packets/DAT_05.md | Public data staged and manifest evidence recorded. |
| DAT_06 | PASS | Codex | packets/DAT_06.md | Public data staged and manifest evidence recorded. |
| IMP_01 | PASS | Codex | packets/IMP_01.md | Empty baseline counts/freshness/indexer captured. |
| IMP_02 | PASS | Codex | packets/IMP_02.md | Five public GPX files copied into watched import folder. |
| IMP_03 | PASS | Codex | packets/IMP_03.md | Live watcher processed five GPX files; no Rescan GPS needed. |
| IMP_04 | PASS | Codex | packets/IMP_04.md | Freshness/indexer/jobs complete for five GPX imports; P2 admin route issue recorded. |
| IMP_05 | PASS | Codex | packets/IMP_05.md | Helper reload and clean context showed map/stats/filter with five imported tracks. |
| IMP_06 | PASS | Codex | packets/IMP_06.md | Each imported filename search returned its matching track; map/stats/filter aggregate stayed at five. |
| IMP_07 | PASS | Codex | packets/IMP_07.md | Map clicks opened details for all five imported tracks; one overlap selector behaved correctly. |
| IMP_08 | PASS | Codex | packets/IMP_08.md | Baseline 0 increased to five tracks; no source split. |
| IMP_09 | PASS | Codex | packets/IMP_09.md | Totals, activity breakdown, periods, rankings, summary row, and heatmap density verified. |
| DEL_01 | PASS | Codex | packets/DEL_01.md | Deleted two imported GPX source files from watched folder. |
| DEL_02 | PASS | Codex | packets/DEL_02.md | Automatic delete processing removed two tracks; no Rescan GPS needed. |
| DEL_03 | PASS | Codex | packets/DEL_03.md | Deleted tracks absent from map/list/filter/heatmap/stats/related evidence. |
| DEL_04 | PASS | Codex | packets/DEL_04.md | Remaining three GPX tracks open correctly after deletion. |
| DEL_05 | PASS | Codex | packets/DEL_05.md | Delete flow judged by user-visible surfaces; stale deleted URLs not used as criteria. |
| FIT_01 | PASS | Codex | packets/FIT_01.md | Public Activity.fit copied into watched import folder. |
| FIT_02 | PASS | Codex | packets/FIT_02.md | FIT imported as track 100005; map, search, and stats show four tracks including Walking FIT activity. |
| FIT_03 | PASS | Codex | packets/FIT_03.md | FIT details tabs, mini-map, and point popup rendered for track 100005. |
| FIT_04 | PASS | Codex | packets/FIT_04.md | Original source download returned Activity.fit with matching public checksum and FIT signature. |
| FIT_05 | PASS | Codex | packets/FIT_05.md | Download as GPX returned valid GPX with one track, one segment, and 3,601 trkpt entries. |
| FIT_06 | NOT APPLICABLE | Codex | packets/FIT_06.md | Conditional unavailable-converter path did not apply because GPSBabel converted Activity.fit successfully. |
| FMT_01 | PASS | Codex | packets/FMT_01.md | GPX/FIT plus synthetic TCX/KML/KMZ/IGC/NMEA/GeoJSON/GDB samples accepted; eleven tracks visible after format imports. |
| FMT_02 | PASS | Codex | packets/FMT_02.md | Seven non-GPX formats display on map/stats/details, render graphs, and support original plus GPX downloads with trkpt data. |
| SGN_01 | PASS | Codex | packets/SGN_01.md | Fresh signed-out context redirected to /mtl/login. |
| SGN_02 | PASS | Codex | packets/SGN_02.md | Valid README credentials reached map with eleven tracks. |
| SGN_03 | PASS | Codex | packets/SGN_03.md | Wrong password stayed on login and showed invalid-credentials error. |
| SGN_04 | NOT APPLICABLE | Codex | packets/SGN_04.md | Demo mode was not active; no demo credentials banner expected. |
| SGN_05 | PASS | Codex | packets/SGN_05.md | Credentials-only logout returned to login; re-login reached map with eleven tracks. |
| SGN_06 | PASS | Codex | packets/SGN_06.md | Startup splash/loading text appeared and disappeared after map loaded eleven tracks. |
| SGN_07 | PASS | Codex | packets/SGN_07.md | Client-side startup API failure showed clear retry UI instead of frozen splash. |
| SGN_08 | PASS | Codex | packets/SGN_08.md | About dialog showed MTL Explorer branding and source copy. |
| SGN_09 | PASS | Codex | packets/SGN_09.md | Browser back/forward restored map, stats, and details views; no navigation-blocking errors. |
| MAP_01 | PASS | Codex | packets/MAP_01.md | Base map, attribution, controls, overlays, and track/map network responses loaded. |
| MAP_02 | PASS | Codex | packets/MAP_02.md | Map and API both reported eleven visible tracks. |
| MAP_03 | PASS | Codex | packets/MAP_03.md | Live watched-folder GPX import raised freshness prompt; Reload updated same browser from eleven to twelve tracks. |
| MAP_04 | PASS | Codex | packets/MAP_04.md | Deleted tracks remain absent from current API/search; linked DEL_03 surface evidence. |
| MAP_05 | PASS | Codex | packets/MAP_05.md | Zoomed from 500 km to 10 km; closer track geometry remained continuous without duplicate/broken artifacts. |
| MAP_06 | PASS | Codex | packets/MAP_06.md | Rapid pan/zoom settled without stale lines, missing tiles, or runaway loading text. |
| MAP_07 | PASS | Codex | packets/MAP_07.md | Track Points & Direction markers/arrows visible at 10 m on dense GPX track 100000. |
| MAP_08 | PASS | Codex | packets/MAP_08.md | Single track click highlighted route and opened details for #100000. |
| MAP_09 | PASS | Codex | packets/MAP_09.md | Overlap click opened 3-track chooser; selecting Moselradweg opened #100002 details. |
| MAP_10 | PASS | Codex | packets/MAP_10.md | Closing the 3-track selection sheet returned to normal map state with 12 tracks. |
| MAP_11 | PASS | Codex | packets/MAP_11.md | Actual high-zoom point marker opened metrics popup for track #100000. |
| MAP_12 | PASS | Codex | packets/MAP_12.md | Swiss Bike Routes popup showed SchweizMobil route #54 and closed cleanly. |
| MAP_13 | PASS | Codex | packets/MAP_13.md | Remote raster mode exposed light/light-topo/dark styles and used provider tiles without map-proxy requests. |
| MAP_14 | PASS | Codex | packets/MAP_14.md | Blocking local PMTiles triggered remote raster fallback while zoom and track selection still worked. |
| MAP_15 | PASS | Codex | packets/MAP_15.md | Manual Remote source used OSM raster themes without map-proxy, persisted after reload, and Reset restored Auto. |
| TRD_01 | PASS | Codex | packets/TRD_01.md | Opened GPX #100000 and FIT #100005 from map navigation and recorded source filenames. |
| TRD_02 | PASS | Codex | packets/TRD_02.md | Details loaded overview, graphs, quality, related, events, and mini-map for GPX #100000. |
| TRD_03 | PASS | Codex | packets/TRD_03.md | Repeated details tab switches stayed nonblank and did not refetch in a loop. |
| TRD_04 | PASS | Codex | packets/TRD_04.md | Elevation, speed, gain, and distance charts rendered with readable values. |
| TRD_05 | PASS | Codex | packets/TRD_05.md | X-axis, range, point-count, and graph-height controls updated charts without layout breakage. |
| TRD_06 | PASS | Codex | packets/TRD_06.md | Retested locally on 2026-06-04; chart-to-mini-map and mini-map-to-chart hover both moved the synchronized cursor/tooltip, so MTL-FR-003 is not reproducible. |
| TRD_07 | PASS | Codex | packets/TRD_07.md | Shape previews visible in stats, browser, searched browser, related cards, and selection list. |
| TRD_08 | PASS | Codex | packets/TRD_08.md | Original GPX download for #100002 matched the uploaded public file checksum. |
| TRD_09 | PASS | Codex | packets/TRD_09.md | Download as GPX for FIT-backed #100005 returned valid GPX with 3,601 trkpt entries. |
| TRD_10 | PASS | Codex | packets/TRD_10.md | Activity type change saved, recalculated energy, and was restored to Walking. |
| TRD_11 | PASS | Codex | packets/TRD_11.md | Rider-weight what-if recalculated energy without saving. |
| TRD_12 | PASS | Codex | packets/TRD_12.md | Excluding #100005 removed it from Stats; re-including restored totals. |
| TRD_13 | FIXED | Codex | packets/TRD_13.md | Retested after fix on local Vite frontend; clicking a related card changed detail content and updated the route URL to the clicked track. |
| TRD_14 | PASS | Codex | packets/TRD_14.md | Events tab on track #100002 showed one detected break; selecting it highlighted the mini-map and deselecting cleared the selected state. |
| FLT_01 | PASS | Codex | packets/FLT_01.md | Active `Activities by keyword` filter persisted, root chip showed `1 / 12 Tracks`, and reopened panel showed On plus keyword `MAP 03`. |
| FLT_02 | PASS | Codex | packets/FLT_02.md | Catalog showed 18 filters with expected group counts; Activity grouping and `gradient`/`duplicate` searches worked. |
| FLT_03 | PASS | Codex | packets/FLT_03.md | Keyword parameter auto-applied to one Moselradweg track; Stats reflected the filter; clearing keyword restored all 12 tracks. |
| FLT_04 | PASS | Codex | packets/FLT_04.md | Keyword, From date, and rectangle geo parameter persisted and re-applied after root app reload. |
| FLT_05 | PASS | Codex | packets/FLT_05.md | Circle/rectangle/polygon drawing, undo, cancel, explicit finish, reload persistence, and clear-all worked. |
| FLT_06 | PASS | Codex | packets/FLT_06.md | Clearing keyword live-updated count, map legend/colors, and Stats from 1/12 to 12/12 without full reload. |
| FLT_07 | PASS | Codex | packets/FLT_07.md | Legend showed Bicycle/Walking categories; hiding/restoring each group changed visible count and map state immediately; collapse/expand worked. |
| FLT_08 | PASS | Codex | packets/FLT_08.md | Toggling filter Off restored plain 12-track map and Stats with no filtered slash chip. |
| TBS_01 | PASS | Codex | packets/TBS_01.md | Tracks tab listed all 12 tracks with expected metadata columns and summary. |
| TBS_02 | PASS | Codex | packets/TBS_02.md | Search matched name, description, date, distance, duration, activity, and FIT source-file metadata; clearing restored 12 rows. |
| TBS_03 | PASS | Codex | packets/TBS_03.md | Sortable headers/chips responded; Walking search reduced summary to one FIT row. |
| TBS_04 | PASS | Codex | packets/TBS_04.md | Quick views switched subsets; empty preset states were controlled; returning to All preserved search/sort behavior. |
| TBS_05 | PASS | Codex | packets/TBS_05.md | Clicking MAP 03 row opened track details for #100014. |
| TBS_06 | PASS | Codex | packets/TBS_06.md | Stats Overview showed 12-track totals, activity breakdown, highlights, periods, milestones, and date range. |
| TBS_07 | PASS | Codex | packets/TBS_07.md | Cross-state evidence covers empty baseline, one-track filtered Stats, and current 12-track overview. |
| TBS_08 | PASS | Codex | packets/TBS_08.md | Required import/delete stats evidence shows five-GPX import totals and post-delete three-track state with deleted names absent. |
| TBS_09 | PASS | Codex | packets/TBS_09.md | Daily, weekly, and monthly Stats Trends chart groupings rendered and switched correctly. |
| TBS_10 | PASS | Codex | packets/TBS_10.md | Stats Overview period, recent-list, and milestone entries performed expected expansion/navigation. |
| TBS_11 | PASS | Codex | packets/TBS_11.md | Highlight drilldown opened ranked track list and selected detail; excluded-highlight count path worked and curation was restored. |
| PLN_01 | PASS | Codex | packets/PLN_01.md | Planner opened and Road Bike profile was selectable. |
| PLN_02 | PASS | Codex | packets/PLN_02.md | Two map waypoints computed/drew a 0.83 km route. |
| PLN_03 | PASS | Codex | packets/PLN_03.md | Dragging the route inserted a waypoint and changed route to two legs. |
| PLN_04 | PASS | Codex | packets/PLN_04.md | Move, delete, clear, undo, and redo route edits worked. |
| PLN_05 | PASS | Codex | packets/PLN_05.md | Live stats updated through route edits and clear/restore. |
| PLN_06 | PASS | Codex | packets/PLN_06.md | Elevation profile rendered and hover showed chart tooltip plus map marker. |
| PLN_07 | PASS | Codex | packets/PLN_07.md | Temporary plan saved, listed, loaded, deleted, and cleanup verified. |
| PLN_08 | PASS | Codex | packets/PLN_08.md | Planned-route GPX export downloaded valid GPX with 56 trkpt entries. |
| PLN_09 | PASS | Codex | packets/PLN_09.md | Segment-downloading route state showed clear auto-retry notice. |
| PLN_10 | PASS | Codex | packets/PLN_10.md | Loaded saved route remained displayed during simulated route trouble. |
| PLN_11 | PASS | Codex | packets/PLN_11.md | Mobile touch taps placed planner waypoints and touch drag moved/recomputed the route. |
| MCT_01 | PASS | Codex | packets/MCT_01.md | Segments UI placed A/B zones; Analyze returned Moselradweg result with speed/time/distance metrics. |
| MCT_02 | PASS | Codex | packets/MCT_02.md | Segment result link opened track #100002 details from the result sheet. |
| MCT_03 | PASS | Codex | packets/MCT_03.md | Closing Segments cleared visible measure overlay/nodes and post-close map click did not recreate measure state. |
| MCT_04 | PASS | Codex | packets/MCT_04.md | Two-track Segment Analyzer Compare overlay rendered minimap/charts and skipped one degenerate sub-track with a clear warning. |
| MCT_05 | PASS | Codex | packets/MCT_05.md | Sub-track endpoint returned requested 607105-607373 slice with monotonic points and expected distance. |
| AVR_01 | PASS | Codex | packets/AVR_01.md | 2D Animate playback advanced, paused, speed changed, and stop/reset worked. |
| AVR_02 | PASS | Codex | packets/AVR_02.md | Virtual Race ran two racers, updated ranked cards during playback, paused, and reset. |
| AVR_03 | PASS | Codex | packets/AVR_03.md | After Animate stop, map zoom controls and Stats tool remained usable; AVR_02 reset covered Race return-to-zero. |
| MED_01 | NOT APPLICABLE | Codex | packets/MED_01.md | No indexed media; world-bounds and with-location media APIs returned empty arrays. |
| MED_02 | NOT APPLICABLE | Codex | packets/MED_02.md | No indexed media exists, so viewport-scoped media loading cannot be exercised. |
| MED_03 | NOT APPLICABLE | Codex | packets/MED_03.md | No media pins exist to open previews or next/previous navigation. |
| MED_04 | NOT APPLICABLE | Codex | packets/MED_04.md | No indexed media or HEIC files exist in this configured run. |
| MED_05 | NOT APPLICABLE | Codex | packets/MED_05.md | No media records exist to safely simulate broken/missing photo preview. |
| HMO_01 | PASS | Codex | packets/HMO_01.md | Heatmap toggled on; opacity changed 100 to 40; GPS Tracks stayed enabled and visible. |
| HMO_02 | PASS | Codex | packets/HMO_02.md | Seven overlays toggled independently; each opacity slider changed; GPS Tracks stayed enabled. |
| HMO_03 | PASS | Codex | packets/HMO_03.md | Heatmap remained enabled and map changed to `1 / 12 Tracks` after `ActivitiesByKeyword` / `MAP 03`; cleanup restored unfiltered 12-track map. |
| GPS_01 | PASS | Codex | packets/GPS_01.md | Remote plain HTTP confirmed `isSecureContext=false`; Chrome blocks geolocation. |
| GPS_02 | NOT APPLICABLE | Codex | packets/GPS_02.md | Live permission prompt/locate marker cannot be exercised on remote plain HTTP. |
| GPS_03 | NOT APPLICABLE | Codex | packets/GPS_03.md | Follow/drift requires live geolocation stream; blocked by insecure origin. |
| GPS_04 | FIXED | Codex | packets/GPS_04.md | Retested after fix on plain HTTP non-loopback local URL; app shows `GPS unavailable` with HTTPS/localhost guidance and no longer shows `GPS started` for insecure-origin GPS. |
| GPS_05 | NOT APPLICABLE | Codex | packets/GPS_05.md | No live GPS stream or marker exists to disable on remote plain HTTP. |
| SRC_01 | PASS | Codex | packets/SRC_01.md | Search sheet opened; `Bern` returned place results. |
| SRC_02 | PASS | Codex | packets/SRC_02.md | Selecting `Bern, Switzerland` flew map to 100 m scale and placed one marker. |
| SRC_03 | PASS | Codex | packets/SRC_03.md | Marker clear button removed the temporary search marker. |
| SRC_04 | PASS | Codex | packets/SRC_04.md | Short query showed `Keep typing`; no-match query showed `No matches`. |
| GLB_01 | PASS | Codex | packets/GLB_01.md | Zooming out engaged globe at `1000 km`; globe control became active. |
| GLB_02 | PASS | Codex | packets/GLB_02.md | Zooming in returned to mercator; globe control inactive/hidden at `300 km`. |
| GLB_03 | PASS | Codex | packets/GLB_03.md | Manual disable kept globe inactive through further low-zoom zoom-out. |
| GLB_04 | PASS | Codex | packets/GLB_04.md | Repeated zoom-out/in remained responsive; no edge trap or console errors. |
| ADM_01 | PASS | Codex | packets/ADM_01.md | Admin workspace opened; all grouped tile entries were reachable and labelled. |
| ADM_02 | PASS | Codex | packets/ADM_02.md | Upload panel showed accepted formats, clear unsupported/empty errors, valid synthetic upload success, and cleanup restored 12 tracks. |
| ADM_03 | PASS | Codex | packets/ADM_03.md | Jobs panel/API showed GPS indexer counts including removed files; Refresh updated the timestamp. |
| ADM_04 | PASS | Codex | packets/ADM_04.md | Rescan GPS and Media queued cleanly, settled with no pending work, and map zoom remained responsive. |
| ADM_05 | PASS | Codex | packets/ADM_05.md | Duplicate Finder, Activity Classifier, and Exploration Score were visible and settled at 100%. |
| ADM_06 | PASS | Codex | packets/ADM_06.md | Vector map, GeoNames location search, and BRouter routing segment operational statuses showed ready detail. |
| ADM_07 | PASS | Codex | packets/ADM_07.md | Freshness panel/API showed in-sync tokens, latest timestamp, domains, and refresh affordance. |
| ADM_08 | PASS | Codex | packets/ADM_08.md | Server Log loaded and refreshed visible timestamped entries; API returned compact log evidence. |
| ADM_09 | PASS | Codex | packets/ADM_09.md | Attribution listed expected map, overlay, chart, location search, conversion, and routing sources. |
| ADM_10 | PASS | Codex | packets/ADM_10.md | Helpers showed 2/2 ready; gcexport install/update action reported already-present/successful state. |
| ADM_11 | PASS | Codex | packets/ADM_11.md | Closing/reopening Admin preserved Helpers panel state and recent command output. |
| SYN_01 | PASS | Codex | packets/SYN_01.md | Freshness banner appeared after a synthetic watched-folder import from a 12-track baseline. |
| SYN_02 | PASS | Codex | packets/SYN_02.md | Banner Reload refreshed map and Stats to 13 tracks; cleanup restored 12 tracks. |
| SYN_03 | PASS | Codex | packets/SYN_03.md | Completed IMP/DEL packets verify required five-GPX import/delete flow across map/browser/stats/filter/heatmap/details. |
| SYN_04 | PASS | Codex | packets/SYN_04.md | Completed FIT packets verify FIT conversion updated map/browser/stats/details like native GPX. |
| SYN_05 | PASS | Codex | packets/SYN_05.md | Freshness banner Dismiss hid the banner and it did not immediately reappear; cleanup restored 12 tracks. |
| SYN_06 | PASS | Codex | packets/SYN_06.md | Credentials-only logout/login returned to a stable 12-track map without a freshness loop. |
| SYN_07 | FIXED | Codex | packets/SYN_07.md | Retested after fix on local Vite frontend; with clean idle jobs and GPS pending=1, visible Admin polling flipped the home chip to Jobs active and Jobs tile to Live while map zoom remained responsive. |
| APP_01 | PASS | Codex | packets/APP_01.md | Settings Light/Dark controls updated `data-theme` immediately. |
| APP_02 | PASS | Codex | packets/APP_02.md | Sampled Admin/Settings text remained readable in light and dark modes. |
| APP_03 | PASS | Codex | packets/APP_03.md | Stats Trends chart colors changed after UI theme switch without browser reload. |
| APP_04 | PASS | Codex | packets/APP_04.md | Dark theme persisted across hard reload and credentials-only logout/login. |
| APP_05 | PASS | Codex | packets/APP_05.md | Hard refresh with stored dark theme did not observe a light-theme startup value. |
| APP_06 | PASS | Codex | packets/APP_06.md | All six map styles selected correctly in both light and dark UI themes. |
| APP_07 | PASS | Codex | packets/APP_07.md | OSM Dark map style persisted across reload. |
| APP_08 | PASS | Codex | packets/APP_08.md | Base Map/GPS Tracks opacity settings persisted across reload and Reset restored defaults. |
| LOC_01 | PASS | Codex | packets/LOC_01.md | Browser-default `en-US` formatting verified in Settings and Stats. |
| LOC_02 | PASS | Codex | packets/LOC_02.md | `de-DE` locale switch updated Settings and Stats formatting without NaN/undefined output. |
| LOC_03 | PASS | Codex | packets/LOC_03.md | `mtl.locale=de-DE` persisted across reload. |
| LOC_04 | PASS | Codex | packets/LOC_04.md | Synthetic boundary GPX files rendered sensibly; cleanup restored 12 tracks and no LOC API markers. |
| MOB_01 | PASS | Codex | packets/MOB_01.md | 390 x 844 mobile context with `hasTouch`, coarse pointer, 12-track shell, and no horizontal overflow verified. |
| MOB_02 | PASS | Codex | packets/MOB_02.md | Filter bottom sheet and navigation sheet drag/snap/close behavior verified with touch events. |
| MOB_03 | PASS | Codex | packets/MOB_03.md | Mobile Stats tables/lists/charts and map zoom control remained usable at 390 px without page-level overflow. |
| MOB_04 | PASS | Codex | packets/MOB_04.md | Mobile Planner touch taps, waypoint insertion, and waypoint drag recomputed routes without saving. |
| MOB_05 | PASS | Codex | packets/MOB_05.md | Drag, double-tap, and pinch gestures worked after cycling each major mobile tool. |
| NET_01 | NOT APPLICABLE | Codex | packets/NET_01.md | Installed-PWA offline reload does not apply to this normal browser-tab run. |
| NET_02 | PASS | Codex | packets/NET_02.md | Aborted API requests showed a recoverable no-server/no-cache error with Retry, not a blank screen. |
| NET_03 | PASS | Codex | packets/NET_03.md | Invalid JWT caused 401 and redirect to `/mtl/login?reason=expired`. |
| NET_04 | NOT APPLICABLE | Codex | packets/NET_04.md | No service-worker support/registration in this remote plain-HTTP context and no deployed update event. |
| ERR_01 | FIXED | Codex | packets/ERR_01.md | Retested after fix on local Vite frontend with the same aborted track-detail APIs; the panel now shows `Track details could not be loaded` with Retry and Back to map. |
| ERR_02 | PASS | Codex | packets/ERR_02.md | Rapid tool switching returned cleanly to Map with no planner/segment/animation/GPS leftovers. |
| RUN_CLEANUP | PASS | Codex | packets/RUN_CLEANUP.md | Finalization gate passed; compose stack stopped; containers absent; disposable remote directory removed. |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| MTL-FR-001 | P2 | IMP_04 | Admin subroute hard-load returns Spring Whitelabel 404. | Open |
| MTL-FR-002 | P3 | FIT_03 | Highcharts accessibility module warning appears when rendering detail graphs. | Open |
| MTL-FR-003 | P2 | TRD_06 | Mini-map hover does not highlight the matching chart position. | NOT REPRODUCIBLE |
| MTL-FR-004 | P2 | TRD_13 | Related-track card navigation now updates the route URL. | FIXED |
| MTL-FR-005 | P2 | GPS_04 | GPS insecure-origin failure now shows `GPS unavailable` with HTTPS/localhost guidance instead of `GPS started`. | FIXED |
| MTL-FR-006 | P3 | SYN_07 | Admin Jobs tile now surfaces visible GPS indexer work while Admin is open. | FIXED |
| MTL-FR-007 | P2 | ERR_01 | Failed track-detail API load now shows actionable recovery. | FIXED |

## Final Assembly Notes

- Missing coverage IDs: None; RUN_SETUP through ERR_02 terminal and RUN_CLEANUP completed.
- Cleanup state: PASS; compose stack stopped, no matching running containers, disposable remote directory removed.
- Final report path: `documentation/testing/full-regression/test_runs/2026-06-01_1727-beta-201/report.md`.
- Finalization gate: PASS (171 coverage IDs terminal).
- Early closure approval: None.
