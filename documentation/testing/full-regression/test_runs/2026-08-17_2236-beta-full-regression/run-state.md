# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-08-17_2236-beta-full-regression |
| Target server | 62.238.106.141 |
| SSH user | root |
| Source | GitHub main quick install, with app image override `wauwau0977/mytraillog:beta` |
| App image | `wauwau0977/mytraillog:beta` |
| Coverage plan snapshot | `coverage-plan.md` |
| Coverage plan source | `documentation/testing/frontend-regression-test-plan.md` |
| Coverage plan Git revision | `a6c7dfe9cfdb1781619b161f67cff16a77a06de1` |
| Coverage plan SHA-256 | `eddfd09f4f88beeba47d5a932ab7f0231744b1a4d9922ba2e12c55f27706c472` |
| App URL | http://62.238.106.141:18080/mtl/ |
| Started | 2026-08-17T22:36:36+02:00 |
| Coordinator | Codex |

## Shared Facts

- README facts: Docker Engine plus Compose plugin required; start with `docker compose up -d`; local URL `http://localhost:18080/mtl/`; import folder `./data/gpx/`.
- Login credentials source: Public GitHub `main` README (`mtl` / documented default password; password value is not stored in run artifacts).
- Import folder: `/root/mtl-explorer-2026-08-17_2236-beta-full-regression/data/gpx/`.
- Browser contexts: Codex in-app browser, desktop viewport 1280 x 720, signed in on the empty main map. Pre-install navigation returned `ERR_CONNECTION_REFUSED`.
- Known constraints: Remote origin is plain HTTP; live geolocation is not applicable. Browser screenshot API timed out during RUN_SETUP and must be retried before ACC_04.
- Current controlled data: Browser and backend are in sync at 15 tracks. The two original deletion targets are recoverably quarantined and indexed REMOVED; the three retained original GPX imports remain usable. The SYN_07 stress batch is recoverably quarantined with all 30 temporary rows REMOVED.
- Controlled activity state: track 100017 was temporarily Hiking for FLT_10-FLT_15 and is restored to Running.

## Queue

- Source queue: `coverage-plan.md`
- Current coverage ID: COMPLETE
- Next coverage ID: None
- Frozen coverage ID count: 228

Track active, blocked, failed, and recently completed IDs here. Completed packet
files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | packets/RUN_SETUP.md | Fresh quick install passed with required beta image; local/remote access and valid login verified. |
| ACC_01 | PASS | Codex | packets/ACC_01.md | Frozen plan and queue each contain 228 unique coverage IDs. |
| ACC_02 | PASS | Codex | packets/ACC_02.md | Independent packet and terminal-status accounting enforced for every ID. |
| ACC_03 | PASS | Codex | packets/ACC_03.md | Per-ID packet evidence contract recorded; report must use packets only. |
| ACC_04 | BLOCKED | Codex | packets/ACC_04.md | Selected browser screenshot calls timed out on four direct attempts; DOM testing remains available. |
| ACC_05 | PASS | Codex | packets/ACC_05.md | Active/resolved constraints and unblock paths are explicitly recorded. |
| DAT_01 | PASS | Codex | packets/DAT_01.md | Five public GPX files with 381-2,954 real trackpoints staged outside watched import folder. |
| DAT_02 | PASS | Codex | packets/DAT_02.md | Every staged GPX trackpoint has a timestamp. |
| DAT_03 | PASS | Codex | packets/DAT_03.md | Complete per-source URLs/license note/hashes/counts plus user-navigation IDs 100000-100004 and names. |
| DAT_04 | PASS | Codex | packets/DAT_04.md | Exact five suggested public sample-gpx URLs downloaded and verified. |
| DAT_05 | PASS | Codex | packets/DAT_05.md | Public Garmin FIT fixture converted to 3,601-trackpoint GPX in structural preflight. |
| DAT_06 | PASS | Codex | packets/DAT_06.md | No waypoint-only GPX or non-GPS FIT counted as positive evidence. |
| DAT_07 | PASS | Codex | packets/DAT_07.md | Two fully synthetic timestamped tracks cross the same repeatable start/end zones. |
| DAT_08 | PASS | Codex | packets/DAT_08.md | Packaged generator created four GPS plus two camera-time JPEGs and matching six-point GPX; manifest preserved. |
| IMP_01 | PASS | Codex | packets/IMP_01.md | Empty baseline: 0 tracks, 0.00 m, 0m 00s, GPS 0/0 done, freshness revisions index/tracks/geometry/media r0. |
| IMP_02 | PASS | Codex | packets/IMP_02.md | Exactly five public GPX files copied into run-specific watched import folder. |
| IMP_03 | PASS | Codex | packets/IMP_03.md | Live watcher completed five GPX files; no Rescan GPS needed. |
| IMP_04 | PASS | Codex | packets/IMP_04.md | GPS 5 complete with no failures; Duplicate Finder/classifier/Exploration 5/5; freshness changed and client out of sync. |
| IMP_05 | PASS | Codex | packets/IMP_05.md | Freshness Reload updated map/browser/filter/stats to five tracks without browser restart. |
| IMP_06 | PASS | Codex | packets/IMP_06.md | Five source-to-track mappings verified by exact name search, IDs 100000-100004, mini-map/detail, stats, and filter. |
| IMP_07 | BLOCKED | Codex | packets/IMP_07.md | Screenshot unavailable and canvas lines/points lack semantic targets; visual targeting cannot be done without guessing. |
| IMP_08 | PASS | Codex | packets/IMP_08.md | Statistics increased 0 to 5; five sources map one-to-one to IDs 100000-100004. |
| IMP_09 | BLOCKED | Codex | packets/IMP_09.md | All numeric/activity/period/ranking/browser totals verified; canvas-only heatmap density lacks visual evidence channel. |
| DEL_01 | PASS | Codex | packets/DEL_01.md | Exactly two original public GPX sources moved from watched folder to recoverable run quarantine; three retained. |
| DEL_02 | PASS | Codex | packets/DEL_02.md | Both targets automatically reached REMOVED with no track join; no manual rescan or pending status. |
| DEL_03 | PASS | Codex | packets/DEL_03.md | Both deleted tracks absent from browser, filter, selector, map/popup, heatmap, Related, and all Statistics views. |
| DEL_04 | PASS | Codex | packets/DEL_04.md | Retained 100000, 100002, and 100004 remain searchable; 100004 details/Related and all Statistics entries work. |
| DEL_05 | PASS | Codex | packets/DEL_05.md | Frontend-only deletion pass/fail boundary recorded; API/stale URL probes excluded. |
| FIT_01 | PASS | Codex | packets/FIT_01.md | Original public Activity.fit entered watcher unchanged; watched file count 5 to 6. |
| FIT_02 | PASS | Codex | packets/FIT_02.md | FIT indexed without failure; map 6 tracks; search found track 100005 with stats and mini-map. |
| FIT_03 | BLOCKED | Codex | packets/FIT_03.md | All semantic tabs and mini-map passed; canvas-only point popup cannot be targeted without visual channel. |
| FIT_04 | BLOCKED | Codex | packets/FIT_04.md | UI control exercised; server returns exact FIT checksum, but browser exposes no download artifact for end-user verification. |
| FIT_05 | BLOCKED | Codex | packets/FIT_05.md | UI action exercised; server returns 3,601-trackpoint GPX, but browser exposes no download artifact. |
| FIT_06 | NOT APPLICABLE | Codex | packets/FIT_06.md | Conditional unavailable-converter path does not apply; GPSBabel 1.10.0 and FIT conversion succeeded. |
| FMT_01 | PASS | Codex | packets/FMT_01.md | All nine listed formats accepted positive samples; non-GPX tracks map to IDs 100006-100012. |
| FMT_02 | BLOCKED | Codex | packets/FMT_02.md | Product checks passed for all eight non-GPX formats; selected browser exposes no completed download artifacts. |
| MED_06 | REJECTED | Codex | packets/MED_06.md | FR-001 rejected: exact-beta UI queued MEDIA rescan and indexed 6/6 by pointer at desktop/mobile sizes. |
| SGN_01 | PASS | Codex | packets/SGN_01.md | Signed-out root navigation redirected to `/mtl/login`. |
| SGN_02 | PASS | Codex | packets/SGN_02.md | Valid login reached the populated map. |
| SGN_03 | PASS | Codex | packets/SGN_03.md | Wrong password showed a clear alert and stayed on login. |
| SGN_04 | NOT APPLICABLE | Codex | packets/SGN_04.md | Demo mode is false, so the conditional credentials banner does not apply. |
| SGN_05 | PASS | Codex | packets/SGN_05.md | UI sign-out returned to login and subsequent valid sign-in restored the map. |
| SGN_06 | PASS | Codex | packets/SGN_06.md | Startup WebP background, logo, message, and progress appeared, then disappeared after map load. |
| SGN_07 | BLOCKED | Codex | packets/SGN_07.md | Browser policy blocks navigation while the origin is down; service restored and healthy. |
| SGN_08 | PASS | Codex | packets/SGN_08.md | Login and About dialog consistently use MTL Explorer; version, license, and source identity verified. |
| SGN_09 | PASS | Codex | packets/SGN_09.md | Browser Back and Forward restored Admin Overview and Processing routes with no visible errors. |
| MAP_01 | PASS | Codex | packets/MAP_01.md | Two rendering canvases, map controls, OSM attribution, and 14-track overlay settled without console errors. |
| MAP_02 | PASS | Codex | packets/MAP_02.md | Map, filter, review summary, and complete table agree at 14 visible tracks. |
| MAP_03 | PASS | Codex | packets/MAP_03.md | Preserved required mutation: freshness Reload changed same browser from stale 2 to 5 tracks without restart. |
| MAP_04 | PASS | Codex | packets/MAP_04.md | Deleted targets absent from map selectors and former-area popup flows; current heatmap uses 15-track set; retained details work. |
| MAP_05 | BLOCKED | Codex | packets/MAP_05.md | Zoom and rendering health pass; canvas line precision/integrity cannot be observed without the blocked screenshot channel. |
| MAP_06 | BLOCKED | Codex | packets/MAP_06.md | Rapid interaction settled with no visible spinner/error; canvas stale-line/missing-tile inspection lacks visual channel. |
| MAP_07 | BLOCKED | Codex | packets/MAP_07.md | Valid real track and 100 m viewport prepared with layer enabled; canvas arrows lack usable visual channel. |
| MAP_08 | BLOCKED | Codex | packets/MAP_08.md | Details open through semantic table; direct canvas line click/highlight cannot be targeted without visual evidence. |
| MAP_09 | PASS | Codex | packets/MAP_09.md | Known Bern overlap click opened six-track list; choosing synthetic Segment B opened correct track 100017 details. |
| MAP_10 | PASS | Codex | packets/MAP_10.md | Closing overlap-chosen details cleared selection/detail panels and returned to settled 15-track root map. |
| MAP_11 | BLOCKED | Codex | packets/MAP_11.md | Four-point track and layer prepared; actual canvas marker cannot be distinguished from line without visual channel. |
| MAP_12 | BLOCKED | Codex | packets/MAP_12.md | Swiss layers and attribution work; canvas route path cannot be positively targeted for popup/close without visual channel. |
| MAP_13 | REJECTED | Codex | packets/MAP_13.md | FR-002 rejected: exact-beta OSM Dark loaded CARTO tiles and attribution at desktop/mobile sizes. |
| MAP_14 | BLOCKED | Codex | packets/MAP_14.md | Local mode restored; quick install has hosted public fallback, no local sidecar, and no browser PMTiles blocking capability. |
| MAP_15 | PASS | Codex | packets/MAP_15.md | Manual Remote used OpenTopoMap without new proxy assets, persisted, filtered Swiss themes, and Reset restored Auto/local. |
| TRD_01 | PASS | Codex | packets/TRD_01.md | Filter Review opened GPX 100004; Statistics Tracks opened Activity.fit 100005; filenames recorded. |
| TRD_02 | PASS | Codex | packets/TRD_02.md | Track 100004 loaded overview/mini-map, six charts, quality, related chronology, and one GPS-gap event. |
| TRD_03 | PASS | Codex | packets/TRD_03.md | Repeated five-tab cycle retained identical content/six charts with no blank state, loading loop, or new errors. |
| TRD_04 | PASS | Codex | packets/TRD_04.md | Six populated charts include readable speed, elevation, gain-rate, and distance ranges/axes/units. |
| TRD_05 | PASS | Codex | packets/TRD_05.md | Time/Distance, Range, points, and height controls changed charts measurably and restored without new errors. |
| TRD_06 | PASS | Codex | packets/TRD_06.md | Chart hover created mini-map marker; known mini-map vertex created chart crosshair/tooltip; both cleared cleanly. |
| TRD_07 | PASS | Codex | packets/TRD_07.md | Related, filter, statistics, and overlap-selection surfaces rendered non-empty SVG track-shape previews. |
| TRD_08 | BLOCKED | Codex | packets/TRD_08.md | UI controls and exact server source bytes validated; selected browser exposes no completed download artifact for end-user checksum proof. |
| TRD_09 | BLOCKED | Codex | packets/TRD_09.md | FIT conversion returned valid GPX with 3,601 trackpoints; selected browser exposes no completed download artifact for end-user parsing. |
| TRD_10 | PASS | Codex | packets/TRD_10.md | Running→Bicycle persisted across reload; energy and power recalculated; Running baseline restored. |
| TRD_11 | PASS | Codex | packets/TRD_11.md | 75→100 kg what-if changed 3.1→4.1 Wh and 93→123 W; closing without Save and reload retained baseline. |
| TRD_12 | PASS | Codex | packets/TRD_12.md | Statistics count changed 15→14 when excluded and returned to 15 with controlled recent activity after re-inclusion. |
| TRD_13 | REJECTED | Codex | packets/TRD_13.md | FR-003 rejected: exact-beta related cards changed route and identity at both viewports. |
| TRD_14 | BLOCKED | Codex | packets/TRD_14.md | GPS-gap event and clean selection/deselection states pass; WebGL-only map-position highlight cannot be inspected with screenshot channel blocked. |
| TRD_15 | REJECTED | Codex | packets/TRD_15.md | FR-004 rejected: exact-beta direct Close returned to the map at both viewports. |
| FLT_01 | FIXED | Codex | packets/FLT_01.md | FR-005 fixed: active saved-view identity appears on the map and in reopened Filter. |
| FLT_02 | PASS | Codex | packets/FLT_02.md | 19 views grouped into five sections; search narrowed correctly and clearing restored all groups/cards. |
| FLT_03 | FIXED | Codex | packets/FLT_03.md | FR-005 fixed: active string criterion appears with saved-view identity. |
| FLT_04 | PASS | Codex | packets/FLT_04.md | Date, keyword, and circle persisted/reapplied after reload; Reset criteria restored a clean 15-track result. |
| FLT_05 | PASS | Codex | packets/FLT_05.md | Circle/rectangle/polygon, undo, cancel, finish, reload persistence, and reset cleanup all worked. |
| FLT_06 | PASS | Codex | packets/FLT_06.md | Keyword and palette propagated live to count, two-category legend/RGB swatches, and 2-track Statistics without full reload. |
| FLT_07 | PASS | Codex | packets/FLT_07.md | Correct two-category legend; collapse/reopen worked; CYCLING hide/show changed visible count 2→1→2 without reload. |
| FLT_08 | PASS | Codex | packets/FLT_08.md | Reset restored Smart Base/no criteria/no coloring, 15-track map, no category legend, and 15-track Statistics. |
| FLT_09 | PASS | Codex | packets/FLT_09.md | Exact 2013/2021 selection yielded the same 2 tracks / 29.5 km across map, review, heatmap, Overview, Trends, and Stats Tracks. |
| FLT_10 | PASS | Codex | packets/FLT_10.md | ON_FOOT counted 3; exact HIKING (1) + WALKING (2) produced the correct 3-row result. |
| FLT_11 | PASS | Codex | packets/FLT_11.md | HIKING/WALKING selection survived date restriction; restored BICYCLE remained unchecked when available again. |
| FLT_12 | PASS | Codex | packets/FLT_12.md | 0-of-3 stayed empty after reload; Select current checked all and normalized to All 3 categories / 15 tracks. |
| FLT_13 | PASS | Codex | packets/FLT_13.md | Date change kept selected HIKING visible/checked as unavailable with 0 matches; it could be removed cleanly. |
| FLT_14 | PASS | Codex | packets/FLT_14.md | Switching exact activity→year cleared to All 4; reselecting current year view preserved exact 2013+2021. |
| FLT_15 | PASS | Codex | packets/FLT_15.md | First resolved reload state was exact 2013+2021 / 2 tracks; Review and all Statistics views stayed at 2 / 29.5 km; Running restored. |
| FLT_16 | PASS | Codex | packets/FLT_16.md | Hiding 2013 changed map count 2→1 while stats stayed 2; global selection to 2013 restored hidden state and kept 1 visible. |
| FLT_17 | PASS | Codex | packets/FLT_17.md | Clean-origin first Filter open auto-showed Important guidance; Got it returned and prevented a second automatic opening. |
| FLT_18 | PASS | Codex | packets/FLT_18.md | Returning guidance had zero overflow on 1280x720 and 390x844; Read more omitted Important; Back/Close worked; viewport/tabs cleaned. |
| FLT_19 | PASS | Result, map, statistics, persistence, and single Apply filter switch stayed synchronized on desktop/mobile. | packets/FLT_19.md | |
| FLT_20 | PASS | Review tracks and Statistics Tracks shared search, summary, sorting, pagination, responsive rendering, map selection, and details. | packets/FLT_20.md | |
| FLT_21 | PASS | Codex | packets/FLT_21.md | Review keeps standard desktop Filter width and works at matching partial/full 390x844 detents with no overflow. |
| TBS_01 | PASS | All 15 resolved tracks listed with shape, dates, name/description, activity, distance, duration, speed, energy, exploration, and import time. | packets/TBS_01.md | |
| TBS_02 | PASS | Name, description, date, distance, duration, activity, and source-filename queries matched expected rows; summaries followed and clear restored 15 rows. | packets/TBS_02.md | |
| TBS_03 | PASS | All nine sortable columns reversed correctly; visible Walking rows produced the exact 2/15, 3.79 km, 59m 57s summary before/after sorting. | packets/TBS_03.md | |
| TBS_04 | PASS | All/Excluded/Stats excluded/No activity selected correct subsets; Path search and descending Name sort survived every switch and return. | packets/TBS_04.md | |
| TBS_05 | PASS | Track 100005 row opened matching /track/100005 details with map/metrics; Close returned to Statistics Tracks with query preserved. | packets/TBS_05.md | |
| TBS_06 | PASS | Overview rendered 15-track totals, 12/2/1 activity split, rankings, active periods, recent activity, milestones, and date range. | packets/TBS_06.md | |
| TBS_07 | PASS | Overview/map/filter counts and totals were correct for 15 tracks, only 2013 (1 track), zero selected years, and reset 15-track restoration. | packets/TBS_07.md | |
| TBS_08 | PASS | Exact delete-two processing changed stats/map 15→13 and removed both names from the 13-track browser; retained GPX names and revised totals are correct. | packets/TBS_08.md | |
| TBS_09 | PASS | Daily/weekly/monthly Trends rendered 8/5/4 periods across all eight Highcharts cards with correct axes and stable 13-track totals. | packets/TBS_09.md | |
| TBS_10 | PASS | Overview period entry drilled down, Recent Activity opened matching #100017 details, and Tracks shape preview selected/centered Lannion on the map. | packets/TBS_10.md | |
| TBS_11 | PASS | 13-row highlight drilldown/open worked; temporary exclusion exposed a correct 1-track count/preset and full Included restoration removed it. | packets/TBS_11.md | |
| TBS_12 | FIXED | Statistics overrides are constrained to the current resolved track set (FR-006). | packets/TBS_12.md | |
| TBS_13 | FIXED | Enter and Space open Filter from the filtered summary at both viewports (FR-007). | packets/TBS_13.md | |
| TBS_14 | FIXED | Media-mode explanations appear on keyboard focus with ARIA linkage (FR-008). | packets/TBS_14.md | |
| TBS_15 | BLOCKED | Timelines/zero slots/sub-unit/filter scopes pass; prescribed six-file set has no undated item, so its compact drill-down cannot be exercised. | packets/TBS_15.md | |
| TBS_16 | BLOCKED | Six-item mosaic/filter/viewer/activity-return paths pass; no Undated, 61-plus-item, or deterministic error fixture exists for remaining children. | packets/TBS_16.md | |
| PLN_01 | PASS | Planner opened with BRouter ready and Road Bike replaced the default Hiking profile. | packets/PLN_01.md | |
| PLN_02 | PASS | Two map clicks computed one 2.93 km Road Bike leg with metrics and a four-point elevation profile. | packets/PLN_02.md | |
| PLN_03 | BLOCKED | Two expected-leg drags left the one-leg route unchanged; reliable WebGL route targeting is unavailable under ACC_04. | packets/PLN_03.md | |
| PLN_04 | PASS | Waypoint move/delete recomputed/removed the route; undo, redo, clear, and undo-clear restored exact states. | packets/PLN_04.md | |
| PLN_05 | PASS | Live bar tracked compute/move/delete/undo/clear/profile transitions with coherent distance/ascent/time/leg values. | packets/PLN_05.md | |
| PLN_06 | PASS | Five-point elevation profile hovered at 0.06 km/643 m and created/removed the matching planner map marker. | packets/PLN_06.md | |
| PLN_07 | PASS | Named 710 m plan saved/listed/loaded with exact metrics and was deleted back to an empty list. | packets/PLN_07.md | |
| PLN_08 | BLOCKED | Export action returned valid five-point GPX matching the 710 m plan, but browser download storage is inaccessible. | packets/PLN_08.md | |
| PLN_09 | PASS | Cross-segment reroute showed updating then a clear route-unavailable notice with guidance and intact controls. | packets/PLN_09.md | |
| PLN_10 | PASS | Original 710 m route and five-point profile stayed rendered during and after a failed Car-profile reroute. | packets/PLN_10.md | |
| PLN_11 | BLOCKED | Mobile 390x844 pointer placement/drag worked, but the browser exposes no native touch-pointer injection. | packets/PLN_11.md | |
| MCT_01 | PASS | Two zones found six crossing tracks with names/start/duration/A-B speed and speed/time/distance metric controls. | packets/MCT_01.md | |
| MCT_02 | PASS | mtl-segment-b.gpx opened matching #100017 Track Details with correct identity/start/duration. | packets/MCT_02.md | |
| MCT_03 | PASS | Closing analyzer removed active guidance/overlay; a later map click created no zone, confirming listener cleanup. | packets/MCT_03.md | |
| MCT_04 | PASS | Five-track comparison rendered local map, named charts, aligned slice metrics, and explicit missing-value points without errors. | packets/MCT_04.md | |
| MCT_05 | PASS | Point-ID 637010→637012 returned the exact inclusive canonical slice 637010/11/12 with monotonic local geometry. | packets/MCT_05.md | |
| MCT_06 | PASS | Five selected geometries stayed within a 0.0022° local Bern box with no zero/off-continent jump; Compare map scale remained 100 m. | packets/MCT_06.md | |
| AVR_01 | PASS | Desktop/mobile Animate preserved pre-play tracks and passed collapse, pause/resume, stop, expand, reset, range, duration, and speed controls. | packets/AVR_01.md | |
| AVR_02 | PASS | Two valid racers advanced 0/0→32/14→100/51% with live card/rank/distance updates; pause preserved progress. | packets/AVR_02.md | |
| AVR_03 | PASS | Race reset to 0/0%, overlays closed cleanly, map zoom changed 100→50 m, and Map settings opened normally. | packets/AVR_03.md | |
| AVR_04 | PASS | Two racers advanced on a 50 m local map; both source geometries stayed in Bern bounds with no zero/off-continent step. | packets/AVR_04.md | |
| MED_01 | PASS | Reset defaulted media on with six local points; off and on both survived reload with matching GUI layer state. | packets/MED_01.md | |
| MED_02 | FIXED | FR-009 fixed: broad overview skipped; Bern/New York issue distinct bounded requests with stale-response protection. | packets/MED_02.md | |
| MED_03 | BLOCKED | Six live media points exist, but precise WebGL pin targeting remained unavailable under ACC_04 despite zoom/layer/filter isolation. | packets/MED_03.md | |
| MED_04 | BLOCKED | The prescribed six-file media set is JPEG-only; no HEIC/HEIF fixture exists for conversion/display. | packets/MED_04.md | |
| MED_05 | PASS | Codex | packets/MED_05.md | Missing source showed Preview unavailable with Retry/Download; hash-verified restoration plus Retry decoded the image in place. |
| MED_13 | PASS | Codex | packets/MED_13.md | Exact six-row timeline, origin labels, markers, and selected persisted correlations survived reload and app restart. |
| MED_14 | PASS | Codex | packets/MED_14.md | Photo GPS UI and read-only persistence retain unchanged EXIF coordinates with EXIF_EMBEDDED origin. |
| MED_15 | PASS | Codex | packets/MED_15.md | Estimated item has NULL original GPS and identical persisted route/resolved TRACK_INTERPOLATED coordinates. |
| MED_16 | REJECTED | Codex | packets/MED_16.md | FR-010 rejected: corrected 4/2 timestamp fixture keeps six and changes only two camera-clock rows. |
| MED_17 | BLOCKED | Codex | packets/MED_17.md | FR-010 plus the 0.25 h input step leave no saveable preview item on the prescribed five-minute activity fixture. |
| MED_18 | PASS | Codex | packets/MED_18.md | Manual Set by you state persisted separately and clearing restored the exact preserved TRACK_INTERPOLATED location. |
| MED_19 | PASS | Codex | packets/MED_19.md | Two eligible activities stored two alternatives; six Ambiguous (2) UI states and selected winner 100016 survived recalculation and restart. |
| MED_20 | FIXED | Codex | packets/MED_20.md | FR-011 fixed: size-and-mtime detection reingests same-size changed GPX and recalculates six media. |
| MED_21 | BLOCKED | Codex | packets/MED_21.md | Frozen run has 6 media/17 stored activities, not the required isolated 100,000-media/300-activity scale fixture. |
| MED_22 | REJECTED | Codex | packets/MED_22.md | FR-010 rejected: retained preview row keeps USER_ASSIGNED; manual position is not activity membership. |
| MED_23 | BLOCKED | Codex | packets/MED_23.md | UI accepts only 900-second steps, saved correction is blocked by FR-010, and no Position unknown fixture exists. |
| MED_24 | PASS | Codex | packets/MED_24.md | Same-bounds HTTP 200 no-store response changed baseline→manual→baseline immediately after end-user set/clear. |
| MED_25 | PASS | Codex | packets/MED_25.md | Production deletion of alternate 100022 was followed by six-media correlation fallback to preserved 100016 positions. |
| MED_26 | BLOCKED | Codex | packets/MED_26.md | Retry metadata columns exist, but no deterministic product-triggered failure fixture is available. |
| MED_27 | BLOCKED | Codex | packets/MED_27.md | Desktop cluster/map/activity destinations passed; viewport override, FR-009 local markers, and adjacent-page fixture block the remaining branches. |
| MED_28 | BLOCKED | Codex | packets/MED_28.md | Six-row order and bounded 100/200 API behavior pass; the required 100,000-row first/middle/last fixture is absent. |
| MED_29 | BLOCKED | Codex | packets/MED_29.md | Three available provenance states share one circular glyph with color/text changes and clear restoration; no unknown-position fixture exists. |
| MED_30 | PASS | Codex | packets/MED_30.md | Full desktop viewer navigation, Nearby, zoom/pan/reset, Details, location map, and main-map handoff passed. |
| MED_31 | FIXED | Codex | packets/MED_31.md | FR-012 fixed: phone labels use Nearby; desktop/accessibility keep full text. |
| MED_32 | BLOCKED | Codex | packets/MED_32.md | Panel maximize/restore and state/navigation/zoom pass; the in-app browser exposes no Fullscreen API for true-fullscreen checks. |
| MED_33 | BLOCKED | Codex | packets/MED_33.md | Six-item viewer is bounded; the required 100,000-ID selection and page boundaries are absent. |
| MED_34 | BLOCKED | Codex | packets/MED_34.md | Default/persistence/shared-entry/app-independence and dark interactions pass; true fullscreen remains unavailable. |
| MED_35 | REJECTED | Codex | packets/MED_35.md | FR-010 rejected: corrected fixture retains six, shifts two clock rows, and enables Save. |
| MED_07 | PASS | Codex | packets/MED_07.md | Two watched sources uniquely recorded with hashes, media/index IDs, GPS coordinates, accessible pins, settled MEDIA 6/6, freshness r30, and durable map screenshot. |
| MED_08 | PASS | Codex | packets/MED_08.md | Exactly delete-a/delete-b moved recoverably outside watched media; four non-target fixtures remain and backup hashes match. |
| MED_09 | PASS | Codex | packets/MED_09.md | GUI rescan reached MEDIA 4 completed/2 removed/0 failed; media r30→r32 and freshness Reload cleared stale state. |
| MED_10 | PASS | Codex | packets/MED_10.md | Recorded viewport has exactly four retained pins/rows; deleted IDs absent/unopenable; viewer navigated retained 1/4→4/4. |
| MED_11 | PASS | Codex | packets/MED_11.md | Pan/zoom/reload kept exactly four unique retained markers; repeated Bern bounds were no-store with no deleted IDs. |
| MED_12 | PASS | Codex | packets/MED_12.md | Both index rows REMOVED; former media IDs have v2 DELETE audits; active media counts are zero. |
| HMO_01 | PASS | Codex | packets/HMO_01.md | Heatmap rendered over visible tracks; exact opacity changed 100→52 with live visual reduction. |
| HMO_02 | PASS | Codex | packets/HMO_02.md | All 7 worldwide/Swiss route overlays toggled independently; each opacity moved 100→57-64; ordering/attribution passed. |
| HMO_03 | PASS | Codex | packets/HMO_03.md | WALKING-only changed 13→2/13 and reduced heatmap; Reset restored 13 and broader density without reload. |
| GPS_01 | NOT APPLICABLE | Codex | packets/GPS_01.md | Expected browser limitation: remote plain HTTP is neither localhost nor HTTPS; live GPS belongs on a secure origin. |
| GPS_02 | NOT APPLICABLE | Codex | packets/GPS_02.md | Accepted permission/locate marker cannot be validly exercised on remote plain HTTP; no location fabricated. |
| GPS_03 | NOT APPLICABLE | Codex | packets/GPS_03.md | Follow-me/drift requires an accepted live update stream unavailable on the remote plain-HTTP origin. |
| GPS_04 | NOT APPLICABLE | Codex | packets/GPS_04.md | A standards-valid user denial/disabled decision cannot be exercised on remote plain HTTP; no state fabricated. |
| GPS_05 | NOT APPLICABLE | Codex | packets/GPS_05.md | Disable/removal requires an active accepted live marker/update stream unavailable on remote plain HTTP. |
| SRC_01 | PASS | Codex | packets/SRC_01.md | Search `Bern` returned structured Bern/other results in under 1 s with sort and clear controls. |
| SRC_02 | PASS | Codex | packets/SRC_02.md | Selecting Bern closed sheet, flew/reframed map, and exposed a distinct marker with clear action. |
| SRC_03 | PASS | Codex | packets/SRC_03.md | Explicit clear and switching to Map both removed marker/clear controls cleanly. |
| SRC_04 | FIXED | Codex | packets/SRC_04.md | FR-013 fixed: empty and cleared Search show guidance without a request. |
| GLB_01 | PASS | Codex | packets/GLB_01.md | Zoom out alone transitioned from flat continent view to circular globe with data/controls intact. |
| GLB_02 | PASS | Codex | packets/GLB_02.md | Five Zoom in steps returned circular globe to flat regional map with data/controls intact. |
| GLB_03 | PASS | Codex | packets/GLB_03.md | Manual flat preference survived threshold zoom in/out; explicit second toggle restored globe. |
| GLB_04 | PASS | Codex | packets/GLB_04.md | At min/max only impossible zoom disabled; pan worked and one opposite zoom escaped each boundary. |
| ADM_01 | PASS | Codex | packets/ADM_01.md | Desktop exposes 10 grouped sections; 390x760 mobile Overview→Processing→Overview route/navigation fit safely. |
| ADM_02 | PASS | Codex | packets/ADM_02.md | Valid GPX selected/uploaded/indexed as 100023; unsupported extension and zero-byte GPX rejected with clear specific messages. |
| ADM_03 | PASS | Codex | packets/ADM_03.md | GPS/MEDIA counts reconciled; valid/broken uploads exposed completion/failure plus running/pending jobs and refresh-over-time transitions. |
| ADM_04 | BLOCKED | Codex | packets/ADM_04.md | Browser queued GPS/MEDIA and map continuity passed; server ALREADY_RUNNING proven, but browser frame and authenticated NOT_RUNNING startup state were not safely capturable. |
| ADM_05 | PASS | Codex | packets/ADM_05.md | Duplicate Finder and Exploration exposed live percentages/pending counts and settled to 18/18, 100%; all active tracks are CALCULATED. |
| ADM_06 | PASS | Codex | packets/ADM_06.md | Healthy details, location/routing unavailable and recovered, vector tiles disabled in remote mode and restored; hosted topology has no download phase. |
| ADM_07 | PASS | Codex | packets/ADM_07.md | Latest-change timestamp advanced after real reindex; Out of sync offered Reload, which returned to In sync with 14 Tracks. |
| ADM_08 | PASS | Codex | packets/ADM_08.md | Timestamped server log loaded; Refresh changed content, line-count selection refreshed, and Wrap applied without errors. |
| ADM_09 | PASS | Codex | packets/ADM_09.md | Public/authenticated About showed the same 12 credit sources; Close returned to login or preserved Admin Server log. |
| ADM_10 | PASS | Codex | packets/ADM_10.md | Both Garmin helpers were Ready; same-config Install actions reported Done with clear safe-skip/update output. |
| ADM_11 | PASS | Codex | packets/ADM_11.md | Helper Done/output state survived detail and full Admin close/reopen; routes stayed synchronized. |
| ADM_12 | PASS | Codex | packets/ADM_12.md | Direct routes, desktop Back/Forward, 390x844 Back to overview, and Close-to-map stayed synchronized. |
| SYN_01 | PASS | Codex | packets/SYN_01.md | Unique GPX import indexed as track 100026; freshness banner appeared in ~7 s while client remained at 14 Tracks. |
| SYN_02 | PASS | Codex | packets/SYN_02.md | Banner Reload cleared stale state, changed 14→15 Tracks, and Stats listed the new 673.67 m activity. |
| SYN_03 | PASS | Codex | packets/SYN_03.md | Exact +5 import/-2 deletion propagated through indexer, freshness, map/browser/stats/filter/heatmap/details; current DB/UI audit agrees. |
| SYN_04 | PASS | Codex | packets/SYN_04.md | Original FIT import triggered banner/Reload and 5→6 cache change; current FIT-backed 100005 row/details remain correct. |
| SYN_05 | PASS | Codex | packets/SYN_05.md | Real invocation-2 re-index stayed snoozed through 259 s; banner reappeared at 320 s while cached 15-track map remained usable. |
| SYN_06 | PASS | Codex | packets/SYN_06.md | After one sync and sign-out/sign-in, map stayed at 15 Tracks for 20 s with no banner, reload alert, or loading loop. |
| SYN_07 | PASS | Codex | packets/SYN_07.md | Live/GPS SCANNING badge remained while Zoom in changed 500→300 km; all 49 jobs settled and 30-file batch cleanup verified. |
| APP_01 | PASS | Codex | packets/APP_01.md | Light→Dark flipped CSS scheme within 250 ms; Preferences, charts, dropdown, About sheet, text, and controls repainted coherently. |
| APP_02 | PASS | Codex | packets/APP_02.md | Light/dark audits each covered 111 visible text nodes; none fell below 3.0:1 or showed unreadable foreground/background combinations. |
| APP_03 | PASS | Codex | packets/APP_03.md | Same nine charts retained data while series-edge/grid colors inverted appropriately from light to dark without reload. |
| APP_04 | PASS | Codex | packets/APP_04.md | Dark stayed checked after reload and persisted through signed-out login plus post-login 15-track map. |
| APP_05 | PASS | Codex | packets/APP_05.md | Empty pre-content sample contained no light UI; first meaningful content at ~20 ms and first captured startup frame were dark. |
| APP_06 | PASS | Codex | packets/APP_06.md | All seven map styles selected under both Light and Dark application themes; the opposing combinations remained independently selectable and rendered. |
| APP_07 | PASS | Codex | packets/APP_07.md | OSM Gray remained the current and checked map style after a full page reload; track count and attribution stayed healthy. |
| APP_08 | PASS | Codex | packets/APP_08.md | Base/GPS/media/points/heatmap opacities updated live, persisted after reload, and Reset restored persistent defaults. |
| LOC_01 | PASS | Codex | packets/LOC_01.md | Browser-detected en-GB formatted representative numbers, dates, distances, durations, energy, and ascent coherently. |
| LOC_02 | PASS | Codex | packets/LOC_02.md | en-GB→de-DE changed preview, grouping, decimal, date, distance, speed, energy, and ascent formats immediately without reload artifacts. |
| LOC_03 | PASS | Codex | packets/LOC_03.md | de-DE selection and representative formatted values persisted across a full reload. |
| LOC_04 | BLOCKED | Codex | packets/LOC_04.md | Large/zero/negative formatting and bad-token scans passed; current data has zero null altitude points, so the null-elevation branch could not be exercised safely. |
| LOC_05 | FIXED | Codex | packets/LOC_05.md | FR-014 fixed: main, 3D, and mini-map scales follow live measurement preference. |
| MOB_01 | BLOCKED | Codex | packets/MOB_01.md | Exact 390 x 844 rendering passed; native touch input/device emulation is unavailable in the browser test channel. |
| MOB_02 | PASS | Codex | packets/MOB_02.md | Statistics and Map sheets opened, pointer-dragged, snapped/restored, and closed without stale overlays. |
| MOB_03 | PASS | Codex | packets/MOB_03.md | Charts and contained wide table stayed usable at 390 px; no visible text clipping or page overflow; map controls responded. |
| MOB_04 | BLOCKED | Codex | packets/MOB_04.md | Pointer placement, drag, insertion, and cleanup passed; native touch injection is unavailable. |
| MOB_05 | BLOCKED | Codex | packets/MOB_05.md | Double-click and pointer drag passed after all eight tools; native multi-touch pinch injection is unavailable. |
| MOB_06 | REJECTED | Codex | packets/MOB_06.md | FR-015 rejected: Apply returns the reversible catalog draft to Settings; Cancel preserves prior view. |
| NET_01 | NOT APPLICABLE | Codex | packets/NET_01.md | Normal browser tab (not standalone/fullscreen/minimal-ui); frozen plan excludes normal-tab offline reload. |
| NET_02 | FIXED | Codex | packets/NET_02.md | FR-016 fixed: cached Statistics show an error and Retry, then recover. |
| NET_03 | PASS | Codex | packets/NET_03.md | Protected endpoint returned 401; unauthenticated app-root navigation redirected to login; re-login restored the 15-track map. |
| NET_04 | NOT APPLICABLE | Codex | packets/NET_04.md | Normal tab has no registered/waiting service worker; frozen offline/cache pass assigns NET_01-NET_04 to installed web-app mode. |
| ERR_01 | BLOCKED | Codex | packets/ERR_01.md | Track/media/planner/session recovery passed; isolated failed-map-config simulation lacks a safe control in this topology/browser. |
| ERR_02 | PASS | Codex | packets/ERR_02.md | Two all-tool switch sequences left only final content; map cursor/gestures recovered; zero new console errors. |
| UXP_01 | BLOCKED | Codex | packets/UXP_01.md | All functional journeys passed; required page responsiveness/main-thread/API timing fields are unavailable from the selected browser. |
| RUN_CLEANUP | PASS | Codex | packets/RUN_CLEANUP.md | Gate/audit passed; four project containers, project network/volumes, and exact disposable directory removed; endpoint refused. |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| FR-001 | P1 | MED_06 | Admin `Rescan Media` is a no-op; documented endpoint works directly. | REJECTED |
| FR-002 | P1 | MAP_13 | OSM Dark in intentional remote-raster mode does not request its configured Carto tiles. | REJECTED |
| FR-003 | P1 | TRD_13 | Related-track previous, next, and duplicate cards do not navigate. | REJECTED |
| FR-004 | P1 | TRD_15 | Track Details Close is inert for a directly opened track route. | REJECTED |
| FR-005 | P2 | FLT_01, FLT_03 | Active filter/view identity was absent. | FIXED |
| FR-006 | P1 | TBS_12 | Statistics Tracks could include a stale curated track outside an active geo filter. | FIXED |
| FR-007 | P2 | TBS_13 | Filtered Statistics summary lacked direct keyboard handling. | FIXED |
| FR-008 | P2 | TBS_14 | Media timeline explanations appeared on hover but not on keyboard focus. | FIXED |
| FR-009 | P1 | MED_02 | Media overlay reused an initial world-scale request instead of loading local viewports. | FIXED |
| FR-010 | P1 | MED_16, MED_22, MED_35 | Camera-offset assertions used invalid synthetic timestamp and duration input. | REJECTED |
| FR-011 | P1 | MED_20 | Modified watched GPS source was detected but not re-ingested. | FIXED |
| FR-012 | P2 | MED_31 | Phone viewer kept the longer `Nearby photos` label. | FIXED |
| FR-013 | P2 | SRC_04 | Empty location search had no prompt. | FIXED |
| FR-014 | P1 | LOC_05 | Imperial preference left MapLibre scale controls in metric units. | FIXED |
| FR-015 | P2 | MOB_06 | Packet omitted the catalog Apply step. | REJECTED |
| FR-016 | P1 | NET_02 | Statistics silently presented cached data when refresh requests failed. | FIXED |

## Final Assembly Notes

- Missing coverage IDs: None; all 228 frozen IDs terminal.
- Cleanup state: PASS; original and remediation project resources, derived test image, and exact disposable directories are absent; public endpoint refused.
- Final report paths: `report.md` and `remediation-report.md`; 228 rows verified against packets; zero broken local links.
- Finalization gate: PASS (228 coverage IDs terminal).
- Early closure approval: Not applicable; no resumable gaps remained.
