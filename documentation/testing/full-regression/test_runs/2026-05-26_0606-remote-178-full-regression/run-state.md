# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-05-26_0606-remote-178-full-regression |
| Target server | 178.105.173.254 |
| Source | GitHub main |
| App URL | `http://178.105.173.254:18080/mtl/` |
| Started | 2026-05-26 06:06:34 CEST |
| Coordinator | Codex |

## Shared Facts

- README facts: Docker Engine and Docker Compose plugin required; quick start downloads `docker-compose.yml` from GitHub `main` and runs `docker compose up -d`; local app URL `http://localhost:18080/mtl/`; login `mtl` / `change-me`; import folder `./data/gpx/`.
- Login credentials source: root `README.md` quick start.
- Import folder: `./data/gpx/` relative to `/root/mtl-full-regression-2026-05-26_0606/mtl-explorer`.
- Browser contexts: desktop browser signed in; clean context, narrow mobile/touch, and PWA/offline applicability still planned where useful.
- Known constraints: remote quick install is plain HTTP, so live geolocation is expected to be unavailable except on localhost/HTTPS unless a secure context is introduced.

## Queue

- Source queue: `documentation/testing/frontend-regression-test-plan.md`
- Current coverage ID: COMPLETE
- Next coverage ID: none

Track active, blocked, failed, and recently completed IDs here. Completed packet files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | packets/RUN_SETUP.md | Quick install passed; app running at remote URL; empty dataset baseline captured. |
| ACC_01 | PASS | Codex | packets/ACC_01.md | Full queue represented one stable coverage ID per row and packet target. |
| ACC_02 | PASS | Codex | packets/ACC_02.md | Parent/area results will not substitute for child packet statuses. |
| ACC_03 | PASS | Codex | packets/ACC_03.md | Final report to be assembled from packets with one row per coverage ID. |
| ACC_04 | PASS | Codex | packets/ACC_04.md | Representative working screenshots captured and screenshot policy established. |
| ACC_05 | PASS | Codex | packets/ACC_05.md | Constraints will be terminal BLOCKED/NOT APPLICABLE with explicit reasons. |
| DAT_01 | PASS | Codex | packets/DAT_01.md | Five public GPX files with real trkpt sequences were downloaded and validated. |
| DAT_02 | PASS | Codex | packets/DAT_02.md | All five GPX files contain timestamp tags suitable for duration/statistics checks. |
| DAT_03 | PASS | Codex | packets/DAT_03.md | Source metadata plus imported IDs/names recorded. |
| DAT_04 | PASS | Codex | packets/DAT_04.md | Used the five suggested gps-touring/sample-gpx raw files. |
| DAT_05 | PASS | Codex | packets/DAT_05.md | Downloaded Garmin public Activity.fit sample. |
| DAT_06 | PASS | Codex | packets/DAT_06.md | No waypoint-only GPX/non-GPS FIT counted as positive evidence. |
| IMP_01 | PASS | Codex | packets/IMP_01.md | Captured empty map, stats, admin/jobs/freshness baseline before import. |
| IMP_02 | PASS | Codex | packets/IMP_02.md | Copied five GPX files into README watched folder ./data/gpx. |
| IMP_03 | PASS | Codex | packets/IMP_03.md | Live watcher detected all five files and no manual rescan was required. |
| IMP_04 | PASS | Codex | packets/IMP_04.md | All five GPX files completed SUCCESS and jobs/freshness reflected import. |
| IMP_05 | PASS | Codex | packets/IMP_05.md | Freshness refresh showed imported data on map, stats, track browser, and filter surface. |
| IMP_06 | PASS | Codex | packets/IMP_06.md | All five imported names appeared in track list/search/stats/map/filter context and mapping was recorded. |
| IMP_07 | PASS | Codex | packets/IMP_07.md | Map clicks opened all five imported GPX tracks, including overlap selection for Mosel/VoieVerte. |
| IMP_08 | PASS | Codex | packets/IMP_08.md | Track count increased from 0 to 5 and each GPX produced one track. |
| IMP_09 | PASS | Codex | packets/IMP_09.md | Stats/browser totals increased and heatmap density rendered over imported tracks. |
| DEL_01 | PASS | Codex | packets/DEL_01.md | Deleted Vitry and VoieVerte GPX source files from watched folder. |
| DEL_02 | PASS | Codex | packets/DEL_02.md | Automatic delete processing removed tracks 100000 and 100004; no manual rescan required. |
| DEL_03 | PASS | Codex | packets/DEL_03.md | Deleted tracks disappeared from map/heatmap/list/search/filter context and stats dropped to 3 tracks. |
| DEL_04 | PASS | Codex | packets/DEL_04.md | Remaining Jura track displayed and opened after deletion. |
| DEL_05 | PASS | Codex | packets/DEL_05.md | Deletion verdict based on user-visible surfaces, not stale URL/API probes. |
| FIT_01 | PASS | Codex | packets/FIT_01.md | Copied Activity.fit into watched import folder. |
| FIT_02 | PASS | Codex | packets/FIT_02.md | FIT converted via GPSBabel, indexed as #100005, displayed on map/list/stats. |
| FIT_03 | PASS | Codex | packets/FIT_03.md | FIT-backed detail overview, graphs, quality, related, events, and mini-map rendered. |
| FIT_04 | PASS | Codex | packets/FIT_04.md | Download original returned Activity.fit matching uploaded checksum. |
| FIT_05 | PASS | Codex | packets/FIT_05.md | Download GPX returned valid GPX with 3,601 trkpt points. |
| FIT_06 | NOT APPLICABLE | Codex | packets/FIT_06.md | GPSBabel/FIT conversion was available and succeeded, so unavailable-converter state was not applicable. |
| FMT_01 | FAIL | Codex | packets/FMT_01.md | TCX/KML/IGC/GDB/NMEA imported; KMZ failed conversion; GeoJSON imported empty; SBP sample unavailable. |
| FMT_02 | FAIL | Codex | packets/FMT_02.md | Downloads worked for successful non-GPX imports, but KMZ/GeoJSON failed usable track workflow and KML was not user-visible. |
| SGN_01 | PASS | Codex | packets/SGN_01.md | Fresh unsigned context redirected to `/mtl/login`. |
| SGN_02 | PASS | Codex | packets/SGN_02.md | README credentials reached map with 10 tracks. |
| SGN_03 | PASS | Codex | packets/SGN_03.md | Wrong password showed clear error and stayed on `/mtl/login`. |
| SGN_04 | NOT APPLICABLE | Codex | packets/SGN_04.md | Demo mode API returned `demoMode:false`. |
| SGN_05 | PASS | Codex | packets/SGN_05.md | Admin Session `Logout` returned to login; re-login reached map. |
| SGN_06 | PASS | Codex | packets/SGN_06.md | Startup splash appeared and cleared to map with 10 tracks. |
| SGN_07 | PASS | Codex | packets/SGN_07.md | Simulated API startup failure showed retry and no frozen splash. |
| SGN_08 | PASS | Codex | packets/SGN_08.md | About dialog uses `MTL Explorer` branding. |
| SGN_09 | FAIL | Codex | packets/SGN_09.md | Back/forward did not move between Stats/Admin views. |
| MAP_01 | PASS | Codex | packets/MAP_01.md | Fresh login showed ready map with controls and 10 tracks. |
| MAP_02 | FAIL | Codex | packets/MAP_02.md | UI showed 10 tracks while API had 14 point-bearing indexed tracks after imports. |
| MAP_03 | PASS | Codex | packets/MAP_03.md | Freshness reload showed imported tracks without browser restart. |
| MAP_04 | PASS | Codex | packets/MAP_04.md | Deleted tracks disappeared from map/search/user-visible surfaces. |
| MAP_05 | PASS | Codex | packets/MAP_05.md | Zoomed/clicked tracks rendered continuous geometry without stale duplicates. |
| MAP_06 | PASS | Codex | packets/MAP_06.md | Fast pan/zoom settled without visible stuck loading or blank map. |
| MAP_07 | NOT APPLICABLE | Codex | packets/MAP_07.md | No enabled/exposed direction-arrow setting found. |
| MAP_08 | PASS | Codex | packets/MAP_08.md | Single-track map clicks opened track detail/selection state. |
| MAP_09 | PASS | Codex | packets/MAP_09.md | Overlapping map click showed selection list and opened selected tracks. |
| MAP_10 | PASS | Codex | packets/MAP_10.md | Overlap selection closed cleanly back to normal map state. |
| MAP_11 | FAIL | Codex | packets/MAP_11.md | Detail mini-map point clicks did not show a point metrics popup. |
| MAP_12 | NOT APPLICABLE | Codex | packets/MAP_12.md | No Swiss Mobility route popup/layer support exposed in quick-install map config. |
| TRD_01 | PASS | Codex | packets/TRD_01.md | GPX #100001 and FIT #100005 opened from user-facing navigation. |
| TRD_02 | PASS | Codex | packets/TRD_02.md | Detail overview, graphs, quality, related, events, and mini-map rendered. |
| TRD_03 | PASS | Codex | packets/TRD_03.md | Detail tabs rendered without blank panels. |
| TRD_04 | PASS | Codex | packets/TRD_04.md | Graphs tab rendered chart panels with readable values. |
| TRD_05 | PASS | Codex | packets/TRD_05.md | Graph controls updated x-axis/range/point-count/height without layout breakage. |
| TRD_06 | FAIL | Codex | packets/TRD_06.md | Chart hover worked, but reverse hover was not visible and cursor remained stale. |
| TRD_07 | PASS | Codex | packets/TRD_07.md | Shape previews visible in browser/stats, related, selection, and filter-map context. |
| TRD_08 | PASS | Codex | packets/TRD_08.md | GPX and FIT original downloads matched uploaded/source checksums. |
| TRD_09 | PASS | Codex | packets/TRD_09.md | GPX, FIT, and successful non-GPX exports downloaded valid GPX. |
| TRD_10 | FAIL | Codex | packets/TRD_10.md | No visible activity type edit control in tested detail view. |
| TRD_11 | FAIL | Codex | packets/TRD_11.md | Energy what-if/rider-weight control was not exposed. |
| TRD_12 | FAIL | Codex | packets/TRD_12.md | No visible statistics exclusion toggle in tested detail UI. |
| TRD_13 | PASS | Codex | packets/TRD_13.md | Related tab showed next tracks and duplicates; clicking related #100005 navigated details. |
| TRD_14 | PASS | Codex | packets/TRD_14.md | Event tab showed one break; selection highlighted mini-map and deselection cleared it. |
| FLT_01 | PASS | Codex | packets/FLT_01.md | Reopened Filter kept filtering On and Smart Base Filter selected with live preview. |
| FLT_02 | PASS | Codex | packets/FLT_02.md | Catalog search and group switching worked. |
| FLT_03 | FAIL | Codex | packets/FLT_03.md | Keyword parameter live-applied and reset by clearing, but no explicit Apply/Cancel controls were exposed. |
| FLT_04 | FAIL | Codex | packets/FLT_04.md | Text/date persisted after reload; rectangle geo parameter disappeared. |
| FLT_05 | FAIL | Codex | packets/FLT_05.md | Circle/rectangle/polygon drawing mostly worked, but explicit polygon Undo/Finish controls were disabled and geo persistence failed. |
| FLT_06 | PASS | Codex | packets/FLT_06.md | Visible count, legend/colors, and stats updated live from 1/10 to 10/10 without reload. |
| FLT_07 | PASS | Codex | packets/FLT_07.md | Legend category hide/collapse/restore updated map count immediately. |
| FLT_08 | PASS | Codex | packets/FLT_08.md | Turning filtering off restored normal all-track map state. |
| TBS_01 | PASS | Codex | packets/TBS_01.md | Tracks tab listed 10 tracks with expected columns, summary, sort buttons, and pagination. |
| TBS_02 | PASS | Codex | packets/TBS_02.md | Search matched representative name, description, date, distance, duration, activity, and FIT source-file terms. |
| TBS_03 | PASS | Codex | packets/TBS_03.md | Sort controls changed order and subset search summary reflected visible results. |
| TBS_04 | FAIL | Codex | packets/TBS_04.md | No visible quick-view/preset subset buttons in Tracks tab. |
| TBS_05 | PASS | Codex | packets/TBS_05.md | Clicking Track #100005 row opened its detail sheet. |
| TBS_06 | PASS | Codex | packets/TBS_06.md | Stats Overview showed totals, breakdown, rankings, recent activity, period summaries, and milestones. |
| TBS_07 | PASS | Codex | packets/TBS_07.md | Empty baseline, one-track filtered stats, and 10-track overview evidence were captured. |
| TBS_08 | PASS | Codex | packets/TBS_08.md | Required five-GPX import stats rose to 5 tracks; delete-two flow dropped user-visible totals to 3 with no stale deleted-name results. |
| TBS_09 | PASS | Codex | packets/TBS_09.md | Stats Trends switched daily, weekly, and monthly groupings and rendered charts. |
| TBS_10 | PASS | Codex | packets/TBS_10.md | Clicking Longest track activated a ranked drilldown list headed by Moselradweg. |
| TBS_11 | PASS | Codex | packets/TBS_11.md | Highlight drilldown listed ranked tracks, selected row opened #100002, excluded-count badge appeared and temporary exclusion was restored. |
| PLN_01 | PASS | Codex | packets/PLN_01.md | Planner opened with BRouter ready and Road Bike profile selected. |
| PLN_02 | PASS | Codex | packets/PLN_02.md | Two map clicks after close zoom computed a 1.93 km, one-leg Road Bike route. |
| PLN_03 | FAIL | Codex | packets/PLN_03.md | Dragging an existing route leg did not insert a waypoint; route stayed at 1 leg. |
| PLN_04 | PASS | Codex | packets/PLN_04.md | Waypoint move/delete plus clear/undo/redo worked on an unsaved planner route. |
| PLN_05 | PASS | Codex | packets/PLN_05.md | Live stats changed with waypoint move/delete/clear route edits. |
| PLN_06 | PASS | Codex | packets/PLN_06.md | Elevation profile rendered and hover showed tooltip plus map hover marker. |
| PLN_07 | PASS | Codex | packets/PLN_07.md | Disposable route saved, listed, loaded, and deleted; saved list returned empty. |
| PLN_08 | PASS | Codex | packets/PLN_08.md | Planner GPX export returned valid GPX matching saved-plan coordinates; disposable plan deleted. |
| PLN_09 | NOT APPLICABLE | Codex | packets/PLN_09.md | BRouter was ready and tested routes computed; no missing-routing-data state occurred. |
| PLN_10 | NOT APPLICABLE | Codex | packets/PLN_10.md | No planner route-fetch trouble occurred; BRouter was ready and saved-route list worked under normal state. |
| PLN_11 | BLOCKED | Codex | packets/PLN_11.md | Mobile/touch planner dragging needs a touch-capable test context; current tooling only supports desktop pointer automation. |
| MCT_01 | PASS | Codex | packets/MCT_01.md | Segment Analyzer A/B zones produced one Lannion crossing result with duration and speed metric. |
| MCT_02 | PASS | Codex | packets/MCT_02.md | Clicking the Lannion result opened Track Details #100003. |
| MCT_03 | PASS | Codex | packets/MCT_03.md | Closing Segment Analyzer removed temporary zones/result UI and restored normal map state. |
| MCT_04 | FAIL | Codex | packets/MCT_04.md | Compare opened for 3 Jura tracks and charts rendered, but the comparison mini-map collapsed and could not verify map alignment. |
| MCT_05 | PASS | Codex | packets/MCT_05.md | Sub-track endpoint returned an ordered 89-point NMEA slice between requested A/B crossing point IDs. |
| AVR_01 | FAIL | Codex | packets/AVR_01.md | Animate showed `Tracks 0 / 0` and disabled Play/Stop while the map showed 10 tracks. |
| AVR_02 | FAIL | Codex | packets/AVR_02.md | Virtual Race map/trail moved for 3 racers, but racer cards/ranking stayed static during playback. |
| AVR_03 | PASS | Codex | packets/AVR_03.md | After race playback, overlays closed and map zoom/pan plus location search remained usable. |
| MED_01 | PASS | Codex | packets/MED_01.md | Photos & Media layer toggled off/on; enabling showed the red media cluster for the two Arezzo sample photos. |
| MED_02 | PASS | Codex | packets/MED_02.md | Pan/zoom away removed visible media markers; returning to Arezzo restored cluster `2`; bounds API returned only current-viewport media. |
| MED_03 | PASS | Codex | packets/MED_03.md | Clicking media point opened preview; Previous and Next navigation moved between the two JPEG media items. |
| MED_04 | PASS | Codex | packets/MED_04.md | HEIC content endpoint returned JPEG bytes and the UI displayed the converted HEIC photo preview. |
| MED_05 | FAIL | Codex | packets/MED_05.md | Broken indexed media opened a blank/broken preview without an actionable recoverable error. |
| HMO_01 | PASS | Codex | packets/HMO_01.md | Heatmap toggled off/on over visible Lannion track; reduced opacity kept tracks visible above the overlay. |
| HMO_02 | PASS | Codex | packets/HMO_02.md | Worldwide and Swiss overlays toggled independently; representative opacity sliders moved; GPS track geometry remained visible. |
| HMO_03 | PASS | Codex | packets/HMO_03.md | Keyword filter changed map count 10/10 -> 1/10 -> 10/10 and the Lannion heatmap disappeared/restored accordingly. |
| GPS_01 | PASS | Codex | packets/GPS_01.md | Remote quick-install URL is plain HTTP on a non-localhost host, so live geolocation rows are not applicable in this run. |
| GPS_02 | NOT APPLICABLE | Codex | packets/GPS_02.md | Live GPS permission prompt and locate marker require localhost or HTTPS; this target is remote plain HTTP. |
| GPS_03 | NOT APPLICABLE | Codex | packets/GPS_03.md | Follow-me behavior requires live geolocation, which is not available on this remote plain-HTTP target. |
| GPS_04 | NOT APPLICABLE | Codex | packets/GPS_04.md | Permission-denied live GPS flow requires a secure origin; this target is remote plain HTTP. |
| GPS_05 | NOT APPLICABLE | Codex | packets/GPS_05.md | Disabling live GPS requires a live GPS marker/stream, unavailable on this remote plain-HTTP target. |
| SRC_01 | PASS | Codex | packets/SRC_01.md | Location search returned results for `Bern`, headed by Bern, Switzerland. |
| SRC_02 | PASS | Codex | packets/SRC_02.md | Selecting Bern flew the map and placed a visible location-search marker. |
| SRC_03 | PASS | Codex | packets/SRC_03.md | Marker clear control removed the location-search marker and left the map usable. |
| SRC_04 | PASS | Codex | packets/SRC_04.md | No-result query displayed `No matches` and placed no marker. |
| GLB_01 | PASS | Codex | packets/GLB_01.md | Zooming out to world scale activated the globe toggle/state automatically. |
| GLB_02 | PASS | Codex | packets/GLB_02.md | Zooming in from globe mode returned to flat/inactive globe state. |
| GLB_03 | PASS | Codex | packets/GLB_03.md | Manual globe disable stayed inactive through low-zoom nudges and re-enabled only after clicking the toggle again. |
| GLB_04 | PASS | Codex | packets/GLB_04.md | Low-zoom edge panning did not trap the map; zoom controls returned to flat view. |
| ADM_01 | PASS | Codex | packets/ADM_01.md | Admin dialog opened; grouped entries were visible and Upload opened in-place. |
| ADM_02 | FAIL | Codex | packets/ADM_02.md | Upload endpoint success/errors were clear, but the Admin upload UI only accepts `.gpx` while endpoint/coverage include multiple formats. |
| ADM_03 | PASS | Codex | packets/ADM_03.md | Jobs panel/API showed GPS and media indexer counts; refresh updated the timestamp. |
| ADM_04 | PASS | Codex | packets/ADM_04.md | GPS/media rescan controls queued scans, media showed scanning, and map zoom remained responsive. |
| ADM_05 | PASS | Codex | packets/ADM_05.md | Duplicate Finder and Exploration Score were visible and settled at DONE 100%. |
| ADM_06 | PASS | Codex | packets/ADM_06.md | Operational task rows showed hosted map service, GeoNames ready, and BRouter routing segments ready. |
| ADM_07 | PASS | Codex | packets/ADM_07.md | Freshness panel showed out-of-sync tokens, latest change timestamp, and visible Reload/Dismiss actions. |
| ADM_08 | PASS | Codex | packets/ADM_08.md | Server Log loaded timestamped entries and Refresh advanced visible lines. |
| ADM_09 | PASS | Codex | packets/ADM_09.md | Attribution listed expected map, overlay, chart, location search, conversion, and routing sources. |
| ADM_10 | PASS | Codex | packets/ADM_10.md | Helpers showed both exporter envs ready and gcexport install action reported already-present/successful state. |
| ADM_11 | PASS | Codex | packets/ADM_11.md | Closing/reopening Admin preserved Helpers panel state and recent command output. |
| SYN_01 | PASS | Codex | packets/SYN_01.md | Upload/rescan changes produced a visible freshness banner with Reload/Dismiss. |
| SYN_02 | PASS | Codex | packets/SYN_02.md | Banner reload cleared freshness warning and refreshed map/stats from 10 to 11 tracks. |
| SYN_03 | PASS | Codex | packets/SYN_03.md | Completed IMP/DEL packets verify the required five-GPX import and delete-two sync flow. |
| SYN_04 | PASS | Codex | packets/SYN_04.md | Completed FIT packets verify FIT conversion updated user-visible/cache surfaces like native GPX imports. |
| SYN_05 | FAIL | Codex | packets/SYN_05.md | Freshness banner appeared after new upload, but Dismiss did not hide it. |
| SYN_06 | PASS | Codex | packets/SYN_06.md | Logout/login returned to 12-track map without repeated freshness banner or auto-refresh loop. |
| SYN_07 | PASS | Codex | packets/SYN_07.md | Jobs processing state surfaced during media rescan and map zoom remained responsive. |
| APP_01 | PASS | Codex | packets/APP_01.md | Light/dark Settings controls changed `data-theme` immediately. |
| APP_02 | PASS | Codex | packets/APP_02.md | Sampled Settings/Admin text remained readable in light and dark themes. |
| APP_03 | PASS | Codex | packets/APP_03.md | Stats Trends chart text colors changed between light and dark without page reload. |
| APP_04 | PASS | Codex | packets/APP_04.md | Dark theme persisted across hard reload, safe logout, and login. |
| APP_05 | PASS | Codex | packets/APP_05.md | First captured post-reload state stayed dark; startup code applies stored theme before mount. |
| APP_06 | FAIL | Codex | packets/APP_06.md | Exposed styles worked independently in dark UI, but required grayscale style was missing from Map panel. |
| APP_07 | PASS | Codex | packets/APP_07.md | OSM Dark remained selected across hard reload. |
| APP_08 | PASS | Codex | packets/APP_08.md | Base Map and GPS Tracks opacity persisted across reload; reset restored default style/layers. |
| LOC_01 | PASS | Codex | packets/LOC_01.md | en-GB Settings preview and Stats values used expected date, grouping, decimal, and duration formats. |
| LOC_02 | FAIL | Codex | packets/LOC_02.md | de-DE updated Settings/dates/grouping, but decimal unit values kept period separators. |
| LOC_03 | PASS | Codex | packets/LOC_03.md | de-DE locale remained selected after hard reload. |
| LOC_04 | PASS | Codex | packets/LOC_04.md | Boundary/null-elevation disposable imports rendered without NaN/blank bad values; 14 tracks visible. |
| MOB_01 | BLOCKED | Codex | packets/MOB_01.md | Narrow viewport worked, but no touch-capable browser context is available. |
| MOB_02 | BLOCKED | Codex | packets/MOB_02.md | Mobile sheets opened/closed; touch drag/snap verification is blocked by tooling. |
| MOB_03 | PASS | Codex | packets/MOB_03.md | Narrow Stats tabs/list and map zoom control remained usable with no page-level horizontal overflow. |
| MOB_04 | BLOCKED | Codex | packets/MOB_04.md | Planner waypoint touch interactions require unavailable touch input. |
| MOB_05 | BLOCKED | Codex | packets/MOB_05.md | Pinch/double-tap touch gestures require unavailable touch/gesture emulation. |
| NET_01 | NOT APPLICABLE | Codex | packets/NET_01.md | Installed-PWA offline mode did not apply to this normal browser-tab run. |
| NET_02 | PASS | Codex | packets/NET_02.md | Aborted API requests showed recoverable Retry state, not blank/frozen screen. |
| NET_03 | PASS | Codex | packets/NET_03.md | Invalid JWT redirected to `/mtl/login?reason=expired`. |
| NET_04 | NOT APPLICABLE | Codex | packets/NET_04.md | No client/service-worker update was deployed during this fixed-target run. |
| ERR_01 | FAIL | Codex | packets/ERR_01.md | API/map failure and expired session recovered, but broken media still lacks actionable recovery UI. |
| ERR_02 | PASS | Codex | packets/ERR_02.md | Rapid tool switching left one active Map sheet, no stale overlays, default cursor, and responsive zoom. |
| RUN_CLEANUP | PASS | Codex | packets/RUN_CLEANUP.md | Gate passed; compose stack stopped; no quick-install containers or port listener remained; disposable directory removed. |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| FMT-01 | P2 | FMT_01/FMT_02 | KMZ is listed as supported but GPSBabel conversion fails with `Input type 'kmz' not recognized`. | Open |
| FMT-02 | P2 | FMT_01/FMT_02 | GeoJSON import completes without usable trackpoints and GPX export has 0 `<trkpt>`. | Open |
| FMT-03 | P3 | FMT_01 | SBP positive coverage could not run because no sample was found and installed GPSBabel cannot generate SBP. | Coverage constraint |
| FMT-04 | P2 | FMT_02 | KML import creates API track data but is not visible/searchable in the track browser. | Open |
| SGN-01 | P3 | SGN_09 | Browser Back/Forward does not navigate between primary views. | Open |
| MAP-01 | P2 | MAP_02 | Valid indexed tracks are omitted from the map/list visible count after format imports. | Open |
| MAP-02 | P3 | MAP_11 | Track point clicks did not expose a point metrics popup in the tested detail map. | Open |
| TRD-01 | P3 | TRD_06 | Hover sync is one-way and leaves a stale mini-map cursor. | Open |
| TRD-02 | P2 | TRD_10 | Track details do not expose a visible activity type change control. | Open |
| TRD-03 | P3 | TRD_11 | Energy what-if recalculation control is not exposed in tested track details. | Open |
| TRD-04 | P2 | TRD_12 | Track details do not expose a visible statistics exclusion toggle. | Open |
| FLT-01 | P3 | FLT_03 | Filter parameter workflow lacks explicit Apply/Cancel controls; changes live-apply and reset only by clearing values. | Open |
| FLT-02 | P2 | FLT_04 | Geo rectangle filter parameter is lost after reload while text/date parameters persist. | Open |
| FLT-03 | P3 | FLT_05 | Polygon draw toolbar reports 3 points while explicit Undo/Finish controls remain disabled. | Open |
| TBS-01 | P3 | TBS_04 | Track browser does not expose quick-view/preset subset controls beyond search/sort/pagination. | Open |
| TBS-02 | P3 | TBS_11 | Excluded-highlight count opens an empty track-browser search instead of a useful excluded-track review/manage view. | Open |
| PLN-01 | P2 | PLN_03 | Dragging an existing planner route leg did not insert a waypoint; route stayed at one leg. | Open |
| MCT-01 | P2 | MCT_04 | Segment comparison mini-map collapses while charts render, preventing visual segment alignment verification. | Open |
| AVR-01 | P2 | AVR_01 | Animate tool sees zero tracks and disables playback while tracks are visible on the map. | Open |
| AVR-02 | P3 | AVR_02 | Virtual Race moves map markers, but racer cards/ranking do not visibly update during playback. | Open |
| MED-01 | P2 | MED_05 | Broken media renders as a blank/broken image instead of a recoverable error. | Open |
| ADM-01 | P2 | ADM_02 | Admin upload UI only allows `.gpx` while backend and regression scope include multiple track formats. | Open |
| SYN-01 | P2 | SYN_05 | Data freshness banner Dismiss does not hide the banner. | Open |
| APP-01 | P3 | APP_06 | Grayscale base-map style is missing from Map panel despite regression scope listing it. | Open |
| LOC-01 | P2 | LOC_02 | Locale switch does not update decimal-unit formatting on some Stats values. | Open |
| MOB-01 | Coverage constraint | MOB_01/MOB_02/MOB_04/MOB_05 | Touch input and gesture automation are unavailable in the current browser surface. | Coverage constraint |

## Final Assembly Notes

- Missing coverage IDs: none; finalization gate pending before RUN_CLEANUP.
- Cleanup state: PASS.
- Final report path: `documentation/testing/full-regression/test_runs/2026-05-26_0606-remote-178-full-regression/report.md`.
- Finalization gate: PASS (168 coverage IDs terminal).
- Early closure approval: none.
