# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-06-19_1952-beta-188-full-regression |
| Target server | 188.245.169.80 |
| Source | GitHub main quick install with image override `wauwau0977/mytraillog:beta` |
| App URL | http://188.245.169.80:18080/mtl/ |
| Started | 2026-06-19T19:52:23+0200 CEST |
| Coordinator | Codex |

## Shared Facts

- README facts: Quick start requires Docker Engine and Docker Compose plugin; create `mtl-explorer`, download GitHub `main` `docker-compose.yml`, run `docker compose up -d`, open `http://localhost:18080/mtl/`, login user `mtl` password `change-me`, import tracks by copying GPX/FIT files into `./data/gpx/`.
- Login credentials source: README quick start only for app login; SSH credential was supplied by the user for the disposable target and is not stored here.
- Import folder: `/root/mtl-full-regression-2026-06-19_1952-beta-188-full-regression/data/gpx` on the target, mounted to `/app/gpx`.
- Browser contexts: Scripted desktop/mobile contexts used for checks were closed by their scripts; no active browser context is required for cleanup.
- Known constraints: Remote app is expected to be plain HTTP at `http://188.245.169.80:18080/mtl/`; live geolocation and installed-PWA-only checks may be not applicable unless a secure/installed context is available. App image must be `wauwau0977/mytraillog:beta` for this run.

## Queue

- Source queue: `documentation/testing/frontend-regression-test-plan.md`
- Current coverage ID: RUN_CLEANUP
- Next coverage ID: COMPLETE after remote cleanup access is restored and shutdown/removal is verified

Track active, blocked, failed, and recently completed IDs here. Completed packet
files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | [packets/RUN_SETUP.md](packets/RUN_SETUP.md) | Beta quick-start stack running; remote URL verified. |
| ACC_01 | PASS | Codex | [packets/ACC_01.md](packets/ACC_01.md) | Accounting rule recorded. |
| ACC_02 | PASS | Codex | [packets/ACC_02.md](packets/ACC_02.md) | Accounting rule recorded. |
| ACC_03 | PASS | Codex | [packets/ACC_03.md](packets/ACC_03.md) | Accounting rule recorded. |
| ACC_04 | PASS | Codex | [packets/ACC_04.md](packets/ACC_04.md) | Accounting rule recorded. |
| ACC_05 | PASS | Codex | [packets/ACC_05.md](packets/ACC_05.md) | Accounting rule recorded. |
| DAT_01 | PASS | Codex | [packets/DAT_01.md](packets/DAT_01.md) | Five public GPX files staged with real trackpoint sequences. |
| DAT_02 | PASS | Codex | [packets/DAT_02.md](packets/DAT_02.md) | All five public GPX files are timestamped. |
| DAT_03 | PASS | Codex | [packets/DAT_03.md](packets/DAT_03.md) | Source metadata plus imported GPX IDs/names recorded. |
| DAT_04 | PASS | Codex | [packets/DAT_04.md](packets/DAT_04.md) | Used suggested sample-gpx public URLs. |
| DAT_05 | PASS | Codex | [packets/DAT_05.md](packets/DAT_05.md) | Garmin public Activity.fit staged. |
| DAT_06 | PASS | Codex | [packets/DAT_06.md](packets/DAT_06.md) | No invalid files counted as positive evidence. |
| DAT_07 | PASS | Codex | [packets/DAT_07.md](packets/DAT_07.md) | Synthetic shared-zone tracks staged for MCT/AVR. |
| IMP_01 | PASS | Codex | [packets/IMP_01.md](packets/IMP_01.md) | Empty baseline counts/freshness/jobs captured. |
| IMP_02 | PASS | Codex | [packets/IMP_02.md](packets/IMP_02.md) | Five public GPX files copied into watched import folder. |
| IMP_03 | PASS | Codex | [packets/IMP_03.md](packets/IMP_03.md) | Live watcher indexed 5/5 without manual rescan. |
| IMP_04 | PASS | Codex | [packets/IMP_04.md](packets/IMP_04.md) | Indexer 5/5 complete, jobs settled, freshness changed. |
| IMP_05 | PASS | Codex | [packets/IMP_05.md](packets/IMP_05.md) | Banner reload refreshed map/browser/filter/stats to 5 tracks. |
| IMP_06 | PASS | Codex | [packets/IMP_06.md](packets/IMP_06.md) | All five files mapped and visible in browser/map/stats/filter evidence. |
| IMP_07 | BLOCKED | Codex | [packets/IMP_07.md](packets/IMP_07.md) | Geometry for all five and one line click verified; per-track click/point-popup evidence blocked by map hit-testing limits. |
| IMP_08 | PASS | Codex | [packets/IMP_08.md](packets/IMP_08.md) | Count increased from 0 to 5; one imported track per source file. |
| IMP_09 | PASS | Codex | [packets/IMP_09.md](packets/IMP_09.md) | Totals, stats, browser summary, period/ranking sections, and heatmap density verified. |
| DEL_01 | PASS | Codex | [packets/DEL_01.md](packets/DEL_01.md) | Deleted Vitry and VoieVerte from watched folder. |
| DEL_02 | PASS | Codex | [packets/DEL_02.md](packets/DEL_02.md) | Delete processing automatic; indexer completed 3 and removed 2. |
| DEL_03 | PASS | Codex | [packets/DEL_03.md](packets/DEL_03.md) | Deleted tracks absent from map/browser/filter/heatmap/stats/related evidence. |
| DEL_04 | PASS | Codex | [packets/DEL_04.md](packets/DEL_04.md) | Remaining Jura/Mosel/Lannion still display; Mosel detail opens. |
| DEL_05 | PASS | Codex | [packets/DEL_05.md](packets/DEL_05.md) | User-visible absence verified without stale-URL criterion. |
| FIT_01 | PASS | Codex | [packets/FIT_01.md](packets/FIT_01.md) | Activity.fit copied into watched import folder. |
| FIT_02 | PASS | Codex | [packets/FIT_02.md](packets/FIT_02.md) | Activity.fit imported as track 100005, indexed successfully, visible/searchable/included in stats. |
| FIT_03 | PASS | Codex | [packets/FIT_03.md](packets/FIT_03.md) | FIT detail tabs, mini-map, and point popup render for track 100005. |
| FIT_04 | PASS | Codex | [packets/FIT_04.md](packets/FIT_04.md) | Original source download stayed Activity.fit and matched uploaded checksum. |
| FIT_05 | PASS | Codex | [packets/FIT_05.md](packets/FIT_05.md) | GPX export parsed as GPX 1.1 with real trkpt data. |
| FIT_06 | NOT APPLICABLE | Codex | [packets/FIT_06.md](packets/FIT_06.md) | Unavailable-conversion path did not apply because FIT conversion succeeded. |
| FMT_01 | PASS | Codex | [packets/FMT_01.md](packets/FMT_01.md) | GPX/FIT plus seven unique synthetic non-GPX formats accepted with GPS-bearing tracks. |
| FMT_02 | PASS | Codex | [packets/FMT_02.md](packets/FMT_02.md) | Seven non-GPX formats verified for UI/detail/stat/download/export coverage. |
| SGN_01 | PASS | Codex | [packets/SGN_01.md](packets/SGN_01.md) | Signed-out root redirected to login. |
| SGN_02 | PASS | Codex | [packets/SGN_02.md](packets/SGN_02.md) | Valid README credentials reached map. |
| SGN_03 | PASS | Codex | [packets/SGN_03.md](packets/SGN_03.md) | Invalid credentials showed clear error and stayed on login. |
| SGN_04 | NOT APPLICABLE | Codex | [packets/SGN_04.md](packets/SGN_04.md) | Demo mode disabled by auth demo-status API. |
| SGN_05 | PASS | Codex | [packets/SGN_05.md](packets/SGN_05.md) | Visible logout returned to login; re-login reached map. |
| SGN_06 | PASS | Codex | [packets/SGN_06.md](packets/SGN_06.md) | Splash branding/loading appeared then map loaded without splash. |
| SGN_07 | FIXED | Codex | [packets/SGN_07.md](packets/SGN_07.md) | FIXED locally: startup failure keeps retry/error state visible; see shared fix evidence. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| SGN_08 | PASS | Codex | [packets/SGN_08.md](packets/SGN_08.md) | MTL Explorer appears in title/About control; About panel opens. |
| SGN_09 | FIXED | Codex | [packets/SGN_09.md](packets/SGN_09.md) | FIXED locally: browser Back/Forward route sync explicitly opens the URL target panel. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| MAP_01 | PASS | Codex | [packets/MAP_01.md](packets/MAP_01.md) | First-open base/overlay map canvases, controls, 11 tracks, and clean console verified. |
| MAP_02 | PASS | Codex | [packets/MAP_02.md](packets/MAP_02.md) | Root map showed 11 Tracks matching authenticated API count. |
| MAP_03 | PASS | Codex | [packets/MAP_03.md](packets/MAP_03.md) | IMP_05 freshness reload evidence shows new imports appearing without browser restart. |
| MAP_04 | PASS | Codex | [packets/MAP_04.md](packets/MAP_04.md) | DEL_03 evidence shows deleted IDs absent from map/browser/filter/stats/related surfaces. |
| MAP_05 | PASS | Codex | [packets/MAP_05.md](packets/MAP_05.md) | Zoom scale changed 500 km to 100 km; 11 tracks persisted; finer API precision returned more geometry points. |
| MAP_06 | PASS | Codex | [packets/MAP_06.md](packets/MAP_06.md) | Rapid zoom/pan settled with 11 tracks, no loading text, and clean console. |
| MAP_07 | BLOCKED | Codex | [packets/MAP_07.md](packets/MAP_07.md) | Direction layer enabled at 300 m, but canvas-rendered arrows could not be directly verified due screenshot/canvas tooling limits. |
| MAP_08 | PASS | Codex | [packets/MAP_08.md](packets/MAP_08.md) | IMP_07 rendered Jura line click reopened Track Details #100002. |
| MAP_09 | BLOCKED | Codex | [packets/MAP_09.md](packets/MAP_09.md) | Synthetic overlap API returned IDs 100021/100023, but canvas clicks could not open selection list. |
| MAP_10 | BLOCKED | Codex | [packets/MAP_10.md](packets/MAP_10.md) | No selection list to close because MAP_09 could not open overlap selection. |
| MAP_11 | BLOCKED | Codex | [packets/MAP_11.md](packets/MAP_11.md) | FIT_03 proved popup metrics, but required direct marker click could not be targeted/verified. |
| MAP_12 | BLOCKED | Codex | [packets/MAP_12.md](packets/MAP_12.md) | Swiss/route overlay UI present, but route popup click/close blocked by canvas hit-targeting limits. |
| MAP_13 | PASS | Codex | [packets/MAP_13.md](packets/MAP_13.md) | Remote mode config/styles/attribution verified; no recent /api/map-proxy log matches. |
| MAP_14 | BLOCKED | Codex | [packets/MAP_14.md](packets/MAP_14.md) | Quick-install stack lacks safe local PMTiles unavailability control. |
| MAP_15 | PASS | Codex | [packets/MAP_15.md](packets/MAP_15.md) | Manual Remote override persisted, hid Swiss themes, no isolated proxy log matches, Reset restored Auto. |
| TRD_01 | PASS | Codex | [packets/TRD_01.md](packets/TRD_01.md) | GPX #100003 Mosel and FIT #100005 Activity.fit opened from user-facing navigation. |
| TRD_02 | PASS | Codex | [packets/TRD_02.md](packets/TRD_02.md) | FIT_03 loaded overview, graphs, quality, related, events, mini-map, and popup surfaces. |
| TRD_03 | PASS | Codex | [packets/TRD_03.md](packets/TRD_03.md) | FIT_03 switched Overview/Graphs/Quality/Related/Events with nonblank panels. |
| TRD_04 | PASS | Codex | [packets/TRD_04.md](packets/TRD_04.md) | FIT_03 Graphs rendered speed, elevation, gain, and distance-over-time charts. |
| TRD_05 | PASS | Codex | [packets/TRD_05.md](packets/TRD_05.md) | Distance, Range, point-count, and height controls updated charts without blank panels. |
| TRD_06 | BLOCKED | Codex | [packets/TRD_06.md](packets/TRD_06.md) | Hover targets worked, but visual chart/mini-map sync was not DOM-observable and screenshots unavailable. |
| TRD_07 | BLOCKED | Codex | [packets/TRD_07.md](packets/TRD_07.md) | Browser/filter/stats/related previews evidenced; selection-list preview blocked by MAP_09. |
| TRD_08 | PASS | Codex | [packets/TRD_08.md](packets/TRD_08.md) | FIT and seven non-GPX original downloads matched uploaded/source checksums. |
| TRD_09 | PASS | Codex | [packets/TRD_09.md](packets/TRD_09.md) | FIT and non-GPX GPX exports parsed as valid GPX with trackpoints. |
| TRD_10 | PASS | Codex | [packets/TRD_10.md](packets/TRD_10.md) | UI activity change saved and recalculated energy; visible type/energy restored to Walking. |
| TRD_11 | PASS | Codex | [packets/TRD_11.md](packets/TRD_11.md) | Rider-weight what-if recalculated 85 kg to 392.9 Wh and closed without saving; API stayed 75 kg. |
| TRD_12 | PASS | Codex | [packets/TRD_12.md](packets/TRD_12.md) | Exclude dropped stats count 13→12; re-include restored 12→13 and cleared reason. |
| TRD_13 | PASS | Codex | [packets/TRD_13.md](packets/TRD_13.md) | Related duplicate/previous/next groups and card navigation verified. |
| TRD_14 | BLOCKED | Codex | [packets/TRD_14.md](packets/TRD_14.md) | Events tab and row toggle verified; canvas mini-map highlight could not be directly captured/read. |
| FLT_01 | PASS | Codex | [packets/FLT_01.md](packets/FLT_01.md) | Activities-by-keyword filter with `synthetic` persisted after reload and showed active indicators. |
| FLT_02 | PASS | Codex | [packets/FLT_02.md](packets/FLT_02.md) | Catalog group chips and search/clear behavior verified. |
| FLT_03 | PASS | Codex | [packets/FLT_03.md](packets/FLT_03.md) | Keyword parameter edit/clear auto-applied across filter, map, legend, and stats. |
| FLT_04 | PASS | Codex | [packets/FLT_04.md](packets/FLT_04.md) | Date range, keyword, and drawn circle persisted after reload and re-applied to the visible result. |
| FLT_05 | PASS | Codex | [packets/FLT_05.md](packets/FLT_05.md) | Circle cancel, circle/rectangle draw, polygon undo/finish, reload persistence, and shape cleanup verified. |
| FLT_06 | PASS | Codex | [packets/FLT_06.md](packets/FLT_06.md) | Keyword live-apply updated count, color indicator, legend, and stats while a no-reload marker persisted. |
| FLT_07 | PASS | Codex | [packets/FLT_07.md](packets/FLT_07.md) | Colors preview and map legend matched; collapse, ON_FOOT hide, and restore updated the visible count immediately. |
| FLT_08 | PASS | Codex | [packets/FLT_08.md](packets/FLT_08.md) | Header switch disabled narrowed keyword filter and restored unfiltered `13 Tracks` state. |
| TBS_01 | PASS | Codex | [packets/TBS_01.md](packets/TBS_01.md) | Tracks tab listed all 13 tracks with summary, controls, columns, and representative field values. |
| TBS_02 | PASS | Codex | [packets/TBS_02.md](packets/TBS_02.md) | Search matched name, description, date, distance, duration, activity, and hidden file/source text; clearing restored all rows. |
| TBS_03 | PASS | Codex | [packets/TBS_03.md](packets/TBS_03.md) | Sorting by each visible track-browser column and visible-summary updates verified. |
| TBS_04 | FIXED | Codex | [packets/TBS_04.md](packets/TBS_04.md) | FIXED locally: quick-view preset changes preserve active search and sort. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| TBS_05 | PASS | Codex | [packets/TBS_05.md](packets/TBS_05.md) | Track-browser row click opened Jura detail and cleanup restored the list. |
| TBS_06 | FIXED | Codex | [packets/TBS_06.md](packets/TBS_06.md) | FIXED locally: server overview summary exposes ascentM and UI renders Ascent total. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| TBS_07 | PASS | Codex | [packets/TBS_07.md](packets/TBS_07.md) | Many, single-track, and empty stats states matched API summaries; filter state restored. |
| TBS_08 | PASS | Codex | [packets/TBS_08.md](packets/TBS_08.md) | Import/delete stats transition evidence confirmed; fresh current check has no deleted IDs/names. |
| TBS_09 | PASS | Codex | [packets/TBS_09.md](packets/TBS_09.md) | Monthly, weekly, and daily period charts rendered and selector restored to quarter grouping. |
| TBS_10 | PASS | Codex | [packets/TBS_10.md](packets/TBS_10.md) | Period drilldown, View all tracks, and Recent Activity detail navigation verified. |
| TBS_11 | PASS | Codex | [packets/TBS_11.md](packets/TBS_11.md) | Longest track drilldown opened ranked list, Mosel detail opened, and zero highlight exclusions hid the count note. |
| TBS_12 | PASS | Codex | [packets/TBS_12.md](packets/TBS_12.md) | Geo rectangle resolved to 2 tracks and matched Filter/map, Overview, Trends, Tracks, and post-reload Overview; filter restored. |
| PLN_01 | PASS | Codex | [packets/PLN_01.md](packets/PLN_01.md) | Planner opened and profile changed from Hiking to Road Bike. |
| PLN_02 | PASS | Codex | [packets/PLN_02.md](packets/PLN_02.md) | Two map clicks computed a one-leg route with distance/elevation chart. |
| PLN_03 | PASS | Codex | [packets/PLN_03.md](packets/PLN_03.md) | Exact route-line drag inserted a waypoint; legs changed 1→2. |
| PLN_04 | PASS | Codex | [packets/PLN_04.md](packets/PLN_04.md) | Move, delete, clear, undo, and redo verified through the UI. |
| PLN_05 | PASS | Codex | [packets/PLN_05.md](packets/PLN_05.md) | Live stats updated from empty to one-leg and extended two-leg route. |
| PLN_06 | PASS | Codex | [packets/PLN_06.md](packets/PLN_06.md) | Elevation profile rendered; hover marker appeared on map and cleared on pointer exit. |
| PLN_07 | PASS | Codex | [packets/PLN_07.md](packets/PLN_07.md) | Saved plan id 100024 listed, loaded, and deleted; cleanup verified. |
| PLN_08 | PASS | Codex | [packets/PLN_08.md](packets/PLN_08.md) | GPX export parsed and matched saved route coordinates; temporary plan deleted. |
| PLN_09 | PASS | Codex | [packets/PLN_09.md](packets/PLN_09.md) | One-shot simulated segment-downloading response showed clear notice and recovered on auto-retry. |
| PLN_10 | PASS | Codex | [packets/PLN_10.md](packets/PLN_10.md) | Saved route displayed under mocked route failure; new edit showed unavailable notice; temp plan deleted. |
| PLN_11 | PASS | Codex | [packets/PLN_11.md](packets/PLN_11.md) | Mobile touch placement and waypoint dragging recomputed route stats; mobile context closed. |
| MCT_01 | PASS | Codex | [packets/MCT_01.md](packets/MCT_01.md) | Segment Analyzer zones produced one crossing track with speed/time/distance result metrics. |
| MCT_02 | PASS | Codex | [packets/MCT_02.md](packets/MCT_02.md) | Segment Analyzer result link opened Track Details #100002. |
| MCT_03 | PASS | Codex | [packets/MCT_03.md](packets/MCT_03.md) | Stop cleanup removed temporary overlay/listener effects. |
| MCT_04 | PASS | Codex | [packets/MCT_04.md](packets/MCT_04.md) | Compare rendered two selected tracks with mini-map, charts, and live sub-track requests. |
| MCT_05 | PASS | Codex | [packets/MCT_05.md](packets/MCT_05.md) | Live sub-track endpoint returned expected inclusive A-B slices. |
| MCT_06 | PASS | Codex | [packets/MCT_06.md](packets/MCT_06.md) | Segment Compare geometry stayed local with no zero/off-continent/global-line artifact. |
| AVR_01 | PASS | Codex | [packets/AVR_01.md](packets/AVR_01.md) | Animate speed, play, pause, resume, and stop/reset controls worked. |
| AVR_02 | PASS | Codex | [packets/AVR_02.md](packets/AVR_02.md) | Virtual Race showed two racers, progress/ranking updates, and pause state. |
| AVR_03 | PASS | Codex | [packets/AVR_03.md](packets/AVR_03.md) | Race reset/close restored map zoom and Map tool usability. |
| AVR_04 | PASS | Codex | [packets/AVR_04.md](packets/AVR_04.md) | Virtual race geometry stayed on local measured segment with no zero/off-continent artifact. |
| MED_01 | BLOCKED | Codex | [packets/MED_01.md](packets/MED_01.md) | No indexed media and current saved run lacks SSH/filesystem access to seed documented `data/media` folder. |
| MED_02 | BLOCKED | Codex | [packets/MED_02.md](packets/MED_02.md) | No indexed media and no current filesystem access to seed viewport-test media. |
| MED_03 | BLOCKED | Codex | [packets/MED_03.md](packets/MED_03.md) | No indexed media pins and no current filesystem access to seed preview-test media. |
| MED_04 | BLOCKED | Codex | [packets/MED_04.md](packets/MED_04.md) | No indexed HEIC media and no current filesystem access to seed HEIC fixture. |
| MED_05 | BLOCKED | Codex | [packets/MED_05.md](packets/MED_05.md) | No indexed media and no current filesystem access to seed/manipulate broken-photo fixture. |
| HMO_01 | PASS | Codex | [packets/HMO_01.md](packets/HMO_01.md) | Heatmap toggled on, opacity changed to 50, tracks stayed visible/enabled. |
| HMO_02 | PASS | Codex | [packets/HMO_02.md](packets/HMO_02.md) | Seven overlays toggled independently, opacity sliders changed to 70, tracks stayed active. |
| HMO_03 | PASS | Codex | [packets/HMO_03.md](packets/HMO_03.md) | Heatmap stayed enabled and map changed from 13 Tracks to 1 / 13 after keyword filter. |
| GPS_01 | NOT APPLICABLE | Codex | [packets/GPS_01.md](packets/GPS_01.md) | Remote plain-HTTP target is not a secure origin; app showed HTTPS/localhost GPS requirement. |
| GPS_02 | NOT APPLICABLE | Codex | [packets/GPS_02.md](packets/GPS_02.md) | Permission prompt and live locate marker require HTTPS or localhost. |
| GPS_03 | NOT APPLICABLE | Codex | [packets/GPS_03.md](packets/GPS_03.md) | Follow-me mode requires active live GPS on HTTPS or localhost. |
| GPS_04 | NOT APPLICABLE | Codex | [packets/GPS_04.md](packets/GPS_04.md) | User-denied GPS permission path requires HTTPS or localhost; plain-HTTP target showed secure-origin message. |
| GPS_05 | NOT APPLICABLE | Codex | [packets/GPS_05.md](packets/GPS_05.md) | Disable flow requires an active live GPS marker/watch on HTTPS or localhost. |
| SRC_01 | PASS | Codex | [packets/SRC_01.md](packets/SRC_01.md) | Zurich query returned 20 location results. |
| SRC_02 | PASS | Codex | [packets/SRC_02.md](packets/SRC_02.md) | First Zurich result flew map to location and placed one clearable marker. |
| SRC_03 | PASS | Codex | [packets/SRC_03.md](packets/SRC_03.md) | Location marker clear control removed the marker cleanly. |
| SRC_04 | PASS | Codex | [packets/SRC_04.md](packets/SRC_04.md) | No-result query showed clear `No matches` state. |
| GLB_01 | PASS | Codex | [packets/GLB_01.md](packets/GLB_01.md) | Zooming out engaged active globe projection automatically. |
| GLB_02 | PASS | Codex | [packets/GLB_02.md](packets/GLB_02.md) | Zooming in returned to flat mercator state. |
| GLB_03 | PASS | Codex | [packets/GLB_03.md](packets/GLB_03.md) | Manual globe disable persisted across low-zoom changes until re-enabled. |
| GLB_04 | PASS | Codex | [packets/GLB_04.md](packets/GLB_04.md) | Repeated low-zoom interactions recovered to normal zoom-in flat state. |
| ADM_01 | PASS | Codex | [packets/ADM_01.md](packets/ADM_01.md) | Admin workspace opened with all panel tiles reachable. |
| ADM_02 | PASS | Codex | [packets/ADM_02.md](packets/ADM_02.md) | Upload validation and synthetic GPX upload succeeded. |
| ADM_03 | FIXED | Codex | [packets/ADM_03.md](packets/ADM_03.md) | FIXED locally: indexer status always includes GPS and MEDIA rows, including zero-count MEDIA. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| ADM_04 | PASS | Codex | [packets/ADM_04.md](packets/ADM_04.md) | Manual GPS/Media rescan actions showed queued state and map stayed usable. |
| ADM_05 | PASS | Codex | [packets/ADM_05.md](packets/ADM_05.md) | Duplicate Finder, Activity Classifier, and Exploration Score settled at 100%. |
| ADM_06 | PASS | Codex | [packets/ADM_06.md](packets/ADM_06.md) | Vector Map Tiles, Location Search, and Routing Segments showed useful status. |
| ADM_07 | PASS | Codex | [packets/ADM_07.md](packets/ADM_07.md) | Freshness panel showed tokens, latest change, domains, revision sum, and Refresh. |
| ADM_08 | PASS | Codex | [packets/ADM_08.md](packets/ADM_08.md) | Server log loaded and refresh surface worked. |
| ADM_09 | PASS | Codex | [packets/ADM_09.md](packets/ADM_09.md) | Attribution listed expected map/data/library sources. |
| ADM_10 | PASS | Codex | [packets/ADM_10.md](packets/ADM_10.md) | Garmin/helper statuses visible; invalid install reported clear validation error. |
| ADM_11 | PASS | Codex | [packets/ADM_11.md](packets/ADM_11.md) | Helpers validation output survived detail and full Admin reopen. |
| SYN_01 | PASS | Codex | [packets/SYN_01.md](packets/SYN_01.md) | Synthetic upload changed freshness and showed `New data available` over stale 14-track map. |
| SYN_02 | PASS | Codex | [packets/SYN_02.md](packets/SYN_02.md) | Banner Reload refreshed map/stats/browser to 15 tracks and found `syn-cache-refresh`. |
| SYN_03 | PASS | Codex | [packets/SYN_03.md](packets/SYN_03.md) | Required five-GPX import/delete-two flow cross-checked from IMP/DEL packet evidence. |
| SYN_04 | PASS | Codex | [packets/SYN_04.md](packets/SYN_04.md) | FIT import freshness/cache behavior cross-checked from FIT packet evidence. |
| SYN_05 | PASS | Codex | [packets/SYN_05.md](packets/SYN_05.md) | Dismissal hid the banner through another token change and next 30s poll. |
| SYN_06 | PASS | Codex | [packets/SYN_06.md](packets/SYN_06.md) | Logout/login reconciled freshness without repeated refresh/banner loop. |
| SYN_07 | PASS | Codex | [packets/SYN_07.md](packets/SYN_07.md) | Large synthetic upload showed `Jobs active`/`LIVE`; map drag/zoom worked while pending. |
| APP_01 | PASS | Codex | [packets/APP_01.md](packets/APP_01.md) | Light/dark Settings and Stats surfaces re-themed immediately. |
| APP_02 | PASS | Codex | [packets/APP_02.md](packets/APP_02.md) | No white-on-white/black-on-black text observed; sampled text remained readable. |
| APP_03 | PASS | Codex | [packets/APP_03.md](packets/APP_03.md) | Stats chart variables and visible chart surface recolored after theme switch without reload. |
| APP_04 | PASS | Codex | [packets/APP_04.md](packets/APP_04.md) | Dark theme persisted across reload, login screen, and post-login map. |
| APP_05 | PASS | Codex | [packets/APP_05.md](packets/APP_05.md) | First observable hard-refresh frame and settled map stayed dark. |
| APP_06 | PASS | Codex | [packets/APP_06.md](packets/APP_06.md) | All seven map styles selected under light and dark UI themes. |
| APP_07 | PASS | Codex | [packets/APP_07.md](packets/APP_07.md) | OSM Gray map style persisted across reload. |
| APP_08 | PASS | Codex | [packets/APP_08.md](packets/APP_08.md) | Layer opacity changes persisted, and Reset restored/persisted defaults. |
| LOC_01 | PASS | Codex | [packets/LOC_01.md](packets/LOC_01.md) | en-GB Settings, Stats, and Tracks formatting verified. |
| LOC_02 | PASS | Codex | [packets/LOC_02.md](packets/LOC_02.md) | Switching to de-DE updated Settings, Stats, and Tracks without reload artifact. |
| LOC_03 | PASS | Codex | [packets/LOC_03.md](packets/LOC_03.md) | de-DE persisted across reload and restored formatting on Stats. |
| LOC_04 | PASS | Codex | [packets/LOC_04.md](packets/LOC_04.md) | Large totals, zero-duration row, negative altitude/slope, and null-like fields rendered without NaN/blank output; locale restored to en-GB. |
| MOB_01 | PASS | Codex | [packets/MOB_01.md](packets/MOB_01.md) | 390x844 touch viewport rendered mobile map/nav with 16 Tracks. |
| MOB_02 | PASS | Codex | [packets/MOB_02.md](packets/MOB_02.md) | Navigation and Stats bottom sheets dragged/snapped/closed correctly. |
| MOB_03 | PASS | Codex | [packets/MOB_03.md](packets/MOB_03.md) | Mobile Tracks, Trends charts, and map controls usable; no incoherent text overflow. |
| MOB_04 | PASS | Codex | [packets/MOB_04.md](packets/MOB_04.md) | Reused direct PLN_11 mobile Planner touch placement/drag evidence. |
| MOB_05 | PASS | Codex | [packets/MOB_05.md](packets/MOB_05.md) | Map drag/double-tap/pinch gestures worked after using main tools; Planner retry confirmed scale change. |
| NET_01 | NOT APPLICABLE | Codex | [packets/NET_01.md](packets/NET_01.md) | Installed-web-app-only offline reload check; current context is normal browser tab. |
| NET_02 | FIXED | Codex | [packets/NET_02.md](packets/NET_02.md) | FIXED locally: no-cache API outage keeps actionable retry/error state instead of silent 0 Tracks. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| NET_03 | PASS | Codex | [packets/NET_03.md](packets/NET_03.md) | Invalid JWT redirected to `/mtl/login?reason=expired` and cleared token. |
| NET_04 | NOT APPLICABLE | Codex | [packets/NET_04.md](packets/NET_04.md) | Plain-HTTP normal browser context cannot register service workers or trigger update prompt. |
| ERR_01 | FIXED | Codex | [packets/ERR_01.md](packets/ERR_01.md) | FIXED locally: failed track/map/API recovery issue resolved through NET_02 startup recovery fix. Evidence: [assets/FIXED-issues-local-verification.txt](assets/FIXED-issues-local-verification.txt). |
| ERR_02 | PASS | Codex | [packets/ERR_02.md](packets/ERR_02.md) | Rapid tool switching left no visible stale sheets/toolbars/markers/cursors; map stayed usable. |
| RUN_CLEANUP | BLOCKED | Codex | [packets/RUN_CLEANUP.md](packets/RUN_CLEANUP.md) | Final report/evidence audit complete; remote stack/directory cleanup could not be verified because SSH/filesystem access is unavailable. |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| SGN-07-P2 | P2 | [SGN_07](packets/SGN_07.md) | Startup failure can strand users on progress UI without retry. | Fixed; [fix evidence](assets/FIXED-issues-local-verification.txt) |
| SGN-09-P2 | P2 | [SGN_09](packets/SGN_09.md) | Browser Back can desynchronize URL and active view. | Fixed; [fix evidence](assets/FIXED-issues-local-verification.txt) |
| TBS-04-P2 | P2 | [TBS_04](packets/TBS_04.md) | Track browser presets clear active search and sort. | Fixed; [fix evidence](assets/FIXED-issues-local-verification.txt) |
| TBS-06-P2 | P2 | [TBS_06](packets/TBS_06.md) | Stats overview does not expose a total elevation/ascent value. | Fixed; [fix evidence](assets/FIXED-issues-local-verification.txt) |
| ADM-03-P2 | P2 | [ADM_03](packets/ADM_03.md) | Jobs omits MEDIA indexer summary. | Fixed; [fix evidence](assets/FIXED-issues-local-verification.txt) |
| NET-02-P2 | P2 | [NET_02](packets/NET_02.md) | API outage without cached tracks renders an empty map without a recoverable message. | Fixed; [fix evidence](assets/FIXED-issues-local-verification.txt) |

## Final Assembly Notes

- Missing coverage IDs: None.
- Cleanup state: Local evidence/report cleanup complete and browser scripted contexts closed; remote stack shutdown and disposable-directory removal blocked by unavailable SSH/filesystem access to the target.
- Final report path: `documentation/testing/full-regression/test_runs/2026-06-19_1952-beta-188-full-regression/report.md`.
- Finalization gate: PASS (`Finalization gate: PASS (175 coverage IDs terminal)`).
- Early closure approval: None needed for coverage IDs; cleanup remains blocked on remote access.
