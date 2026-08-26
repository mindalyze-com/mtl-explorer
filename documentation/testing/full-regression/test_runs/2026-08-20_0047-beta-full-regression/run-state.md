# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | 2026-08-20_0047-beta-full-regression |
| Target server | 62.238.106.141 |
| SSH user | root |
| Source | GitHub main quick install, with app image override `wauwau0977/mytraillog` |
| App image | `wauwau0977/mytraillog` |
| Coverage plan snapshot | `coverage-plan.md` |
| Coverage plan source | `documentation/testing/frontend-regression-test-plan.md` |
| Coverage plan Git revision | `1617d05056c1e95fb41d7c9ff0610f2208c8f93c` |
| Coverage plan SHA-256 | `122bd1ecf4a8cccf6285630a9801dd91bac35cb21cd0fdc3ae17907b7221c5b1` |
| App URL | http://62.238.106.141:18080/mtl/ |
| Started | 2026-08-20T00:47:30+02:00 |
| Coordinator | Codex |

## Shared Facts

- README facts: Docker Engine plus Compose plugin; `docker compose up -d`; local URL `http://localhost:18080/mtl/`; remote URL derived as `http://62.238.106.141:18080/mtl/`.
- Login credentials source: GitHub `main` README only; credential values are intentionally not copied into artifacts.
- Import folder: disposable Compose path `data/gpx/` (`/app/gpx` in the app container); media path `data/media/` (`/app/media`).
- Browser contexts: Codex in-app browser, desktop first; narrow viewport passes will be recorded per packet.
- Known constraints: target initially lacked Docker; Docker Engine 29.7.2 and Compose 5.5.0 were installed from Docker's stable Debian repository. Remote origin is plain HTTP. Browser screenshot capture is being retried; text and command evidence is available.

## Queue

- Source queue: `coverage-plan.md`
- Current coverage ID: COMPLETE
- Next coverage ID: None
- Frozen coverage ID count: 235

Track active, blocked, failed, and recently completed IDs here. Completed packet
files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | PASS | Codex | packets/RUN_SETUP.md | Fresh quick install running with verified requested image/digest; README login and remote URL passed. MTL-FR-001 is fixed locally with evidence in `assets/MTL-FR-001-fix-local.{txt,webp}`. |
| ACC_01 | PASS | Codex | packets/ACC_01.md | All 235 frozen IDs are required queue rows. |
| ACC_02 | PASS | Codex | packets/ACC_02.md | No prefix/section collapse; one status row and packet path per ID. |
| ACC_03 | PASS | Codex | packets/ACC_03.md | Packet-per-ID evidence and report assembly contract retained. |
| ACC_04 | BLOCKED | Codex | packets/ACC_04.md | In-app screenshot command fails on fresh tabs; no alternate connected browser or OS capture permission. |
| ACC_05 | PASS | Codex | packets/ACC_05.md | Environment/tooling constraints are explicit and tied to exact packets. |
| DAT_01 | PASS | Codex | packets/DAT_01.md | Five public GPX files staged; 7,735 total real trackpoints. |
| DAT_02 | PASS | Codex | packets/DAT_02.md | All 7,735 trackpoints have timestamps. |
| DAT_03 | PASS | Codex | packets/DAT_03.md | Full source manifest now includes imported IDs 100000-100004 and UI-verified names. |
| DAT_04 | PASS | Codex | packets/DAT_04.md | Used all five suggested gps-touring/sample-gpx raw sources. |
| DAT_05 | PASS | Codex | packets/DAT_05.md | Official Garmin Activity.fit staged; 3,601 GPS-bearing records validated. |
| DAT_06 | FIXED | Codex | packets/DAT_06.md | MTL-FR-020 fixed locally: waypoint-only GPX is rejected before disk/indexing with clear desktop/mobile Admin evidence. |
| DAT_07 | PASS | Codex | packets/DAT_07.md | Two synthetic six-point tracks cross the same start/end zones. |
| DAT_08 | PASS | Codex | packets/DAT_08.md | Generated manifest preserved; matching GPX and eight media files placed exactly and indexed in MED_06. |
| IMP_01 | PASS | Codex | packets/IMP_01.md | Empty map/browser/stats baseline, freshness token, and GPS status captured. |
| IMP_02 | PASS | Codex | packets/IMP_02.md | Five byte-identical public GPX files copied into the documented watched folder. |
| IMP_03 | PASS | Codex | packets/IMP_03.md | Live watcher detected all files; GPS indexer completed 5/5 without manual rescan. |
| IMP_04 | PASS | Codex | packets/IMP_04.md | GPS 5/5, jobs 5/5 settled, and server freshness changed without failures. |
| IMP_05 | PASS | Codex | packets/IMP_05.md | Freshness reload synchronized revisions; map/filter/browser/stats show five tracks. |
| IMP_06 | PASS | Codex | packets/IMP_06.md | All five files found by exact name and opened with expected ID/name and track map. |
| IMP_07 | PASS | Codex | packets/IMP_07.md | All five map lines selected; overlap popup contained only the two expected tracks, once each. |
| IMP_08 | PASS | Codex | packets/IMP_08.md | Statistics count moved from 0 to 5; each source produced one track. |
| IMP_09 | PASS | Codex | packets/IMP_09.md | Totals, activity/rankings, period charts, heatmap, and track-browser summary are populated and consistent. |
| DEL_01 | PASS | Codex | packets/DEL_01.md | Exactly Vitry and Voie Verte GPX copies moved to recoverable disposable quarantine; no other source moved. |
| DEL_02 | PASS | Codex | packets/DEL_02.md | Live watcher detected both deletes and index workers removed tracks 100002/100003 without rescan. |
| DEL_03 | BLOCKED | Codex | packets/DEL_03.md | Accessible map/list/Stats/Related removal passed; rendered heatmap/polyline absence is blocked by ACC_04. |
| DEL_04 | PASS | Codex | packets/DEL_04.md | Jura, Mosel, and Lannion remained searchable and opened matching details after deletion. |
| DEL_05 | BLOCKED | Codex | packets/DEL_05.md | Frontend-only accessible surfaces pass without probes; rendered heatmap/polyline proof remains blocked by ACC_04. |
| FIT_01 | PASS | Codex | packets/FIT_01.md | Byte-identical official GPS-bearing Activity.fit copied into watched folder. |
| FIT_02 | PASS | Codex | packets/FIT_02.md | GPSBabel/import SUCCESS; track 100005 is mapped, searchable, and included in six-track stats. |
| FIT_03 | PASS | Codex | packets/FIT_03.md | FIT overview, charts, quality, related, events, mini-map, and selection behavior match GPX flow. |
| FIT_04 | PASS | Codex | packets/FIT_04.md | Visible original download produced byte-identical 94,096-byte FIT payload. |
| FIT_05 | PASS | Codex | packets/FIT_05.md | Visible Download GPX produced valid GPX 1.1 with 3,601 trkpt and no wpt. |
| FIT_06 | NOT APPLICABLE | Codex | packets/FIT_06.md | Conditional unavailable-converter branch did not apply; GPSBabel FIT flow succeeded. |
| FMT_01 | PASS | Codex | packets/FMT_01.md | All nine supported extensions directly tested; seven new formats indexed as tracks 100006-100012. |
| FMT_02 | FIXED | Codex | packets/FMT_02.md | MTL-FR-002 fixed and directly retested locally; evidence: `assets/MTL-FR-002-fix-local.{txt,webp}`. |
| MED_06 | PASS | Codex | packets/MED_06.md | Baseline media:0; matching GPX 100013 SUCCESS; visible rescan indexed 8/8 and synchronized media:9. |
| SGN_01 | PASS | Codex | packets/SGN_01.md | Signed-out root redirects to branded login; protected views are not shown. |
| SGN_02 | PASS | Codex | packets/SGN_02.md | Valid README credentials reach the loaded map with nine active tracks. |
| SGN_03 | PASS | Codex | packets/SGN_03.md | Invalid credentials show a clear error and remain on the login page. |
| SGN_04 | NOT APPLICABLE | Codex | packets/SGN_04.md | Public status reports demo mode disabled; the conditional login banner does not apply. |
| SGN_05 | PASS | Codex | packets/SGN_05.md | Credentials-only sign out returned to login; the next valid sign-in restored the nine-track map. |
| SGN_06 | PASS | Codex | packets/SGN_06.md | Branded photo splash and loading message appeared, then disappeared after the nine-track map loaded. |
| SGN_07 | FIXED | Codex | packets/SGN_07.md | MTL-FR-003 fixed and directly retested locally; evidence: `assets/MTL-FR-003-fix-local.{txt,webp}`. |
| SGN_08 | PASS | Codex | packets/SGN_08.md | About control, title, heading, licensing copy, and source link consistently use MTL Explorer. |
| SGN_09 | PASS | Codex | packets/SGN_09.md | Back restored populated Stats; Forward restored ready Planner; no visible errors. |
| MAP_01 | PASS | Codex | packets/MAP_01.md | Visible base-map canvases, attribution, controls, style, and nine-track overlay rendered on open. |
| MAP_02 | PASS | Codex | packets/MAP_02.md | Map/filter show 9; categories sum 7+2; Review Tracks has the same nine expected rows. |
| MAP_03 | PASS | Codex | packets/MAP_03.md | Required import flow moved the same browser from 0 to 5 map tracks after the visible freshness Reload. |
| MAP_04 | PASS | Codex | packets/MAP_04.md | Deleted tracks absent from lists; former Bussang overlap opens only Mosel and Joinville opens no deleted detail/chooser. |
| MAP_05 | BLOCKED | Codex | packets/MAP_05.md | Zoom scale and canvases stayed stable, but ACC_04 blocks visual duplicate/broken-line inspection. |
| MAP_06 | BLOCKED | Codex | packets/MAP_06.md | Rapid interaction stayed responsive, but ACC_04 blocks stale-line and missing-tile visual inspection. |
| MAP_07 | BLOCKED | Codex | packets/MAP_07.md | Correct setting and suitable 100 m Bern view established; ACC_04 blocks canvas-arrow visual confirmation. |
| MAP_08 | PASS | Codex | packets/MAP_08.md | Three isolated same-run map-line clicks opened the correct direct track details. |
| MAP_09 | PASS | Codex | packets/MAP_09.md | Bussang click showed exactly the two expected tracks; either choice opened its matching details. |
| MAP_10 | PASS | Codex | packets/MAP_10.md | Closing #100013 details removed selection state and restored normal map controls/count/attribution. |
| MAP_11 | PASS | Codex | packets/MAP_11.md | High-zoom #100013 point click opened time/altitude/speed/distance/duration/energy metrics. |
| MAP_12 | PASS | Codex | packets/MAP_12.md | Swiss popup listed ViaBerna #38 and trail 3782104; Close removed it cleanly. |
| MAP_13 | PASS | Codex | packets/MAP_13.md | Same image in remote mode exposed three styles/no legacy key; each theme was interactive with matching attribution and no proxy diagnostics. |
| MAP_14 | PASS | Codex | packets/MAP_14.md | Unavailable local PMTiles switched to configured CARTO fallback; pan/zoom/tracks/selection worked; local mode restored. |
| MAP_15 | PASS | Codex | packets/MAP_15.md | Manual Remote showed four OSM-only themes and persisted; Reset restored Automatic with seven themes. |
| TRD_01 | PASS | Codex | packets/TRD_01.md | GPX 100000/JuraRoute72011.gpx and FIT 100005/Activity.fit opened from map/filter and Stats Tracks. |
| TRD_02 | PASS | Codex | packets/TRD_02.md | FIT #100005 loaded overview, six charts, related list, events state, mini-map, and SUCCESS/UNIQUE quality. |
| TRD_03 | PASS | Codex | packets/TRD_03.md | Two tab cycles stayed populated; Graph settings persisted and no reload loop appeared. |
| TRD_04 | PASS | Codex | packets/TRD_04.md | Speed, elevation, gain-rate, and distance charts exposed populated series and readable axes. |
| TRD_05 | FIXED | Codex | packets/TRD_05.md | All graph controls now have direct evidence; MTL-FR-004 was NOT REPRODUCIBLE on the current-worktree local stack (`assets/MTL-FR-004-fix-local.{txt,webp}`). |
| TRD_06 | FIXED | Codex | packets/TRD_06.md | MTL-FR-005 fixed locally: chart leave removes hover and chart-source pin at desktop/mobile sizes. |
| TRD_07 | PASS | Codex | packets/TRD_07.md | Rendered track-shape previews verified in browser, filters, Stats, Related, and overlap selection. |
| TRD_08 | PASS | Codex | packets/TRD_08.md | Same-run visible FIT original download is byte-identical to its uploaded source. |
| TRD_09 | PASS | Codex | packets/TRD_09.md | Same-run visible FIT conversion produced valid GPX 1.1 with 3,601 trackpoints. |
| TRD_10 | PASS | Codex | packets/TRD_10.md | Bicycle saved and recalculated energy; reload persisted it; Walking baseline restored. |
| TRD_11 | PASS | Codex | packets/TRD_11.md | 90 kg what-if updated energy/power; closing and reopening restored the unsaved 75 kg baseline. |
| TRD_12 | PASS | Codex | packets/TRD_12.md | Stats changed 9 -> 8 on exclusion and returned exactly to the nine-track baseline on inclusion. |
| TRD_13 | PASS | Codex | packets/TRD_13.md | Canonical duplicate group and previous/next lists rendered; three cards navigated correctly. |
| TRD_14 | BLOCKED | Codex | packets/TRD_14.md | Event selection/deselection works; ACC_04 blocks visual confirmation of the canvas highlight. |
| TRD_15 | REJECTED | Codex | packets/TRD_15.md | MTL-FR-006 rejected: foreground-sheet Close restores Filter Review/search once at desktop/mobile sizes. |
| FLT_01 | FIXED | Codex | packets/FLT_01.md | MTL-FR-007 fixed locally: persisted Smart Base Filter chip is visible at desktop/mobile sizes. |
| FLT_02 | PASS | Codex | packets/FLT_02.md | Nineteen views grouped by theme; gradient/activity searches narrowed correctly and clear restored all. |
| FLT_03 | PASS | Codex | packets/FLT_03.md | Keyword auto-apply changed map/legend/Stats 9 -> 1; clear restored every surface to 9. |
| FLT_04 | PASS | Codex | packets/FLT_04.md | Reload retained exact From date, keyword, and saved circle center/radius. |
| FLT_05 | PASS | Codex | packets/FLT_05.md | Circle/rectangle/polygon, Undo/Cancel/Finish, reload persistence, and clear all passed. |
| FLT_06 | PASS | Codex | packets/FLT_06.md | No-reload keyword transition synchronized count, result label, legend/colors, and Stats. |
| FLT_07 | PASS | Codex | packets/FLT_07.md | Hide/show changed map-visible count 1 -> 0 -> 1; collapse/expand removed/restored legend rows. |
| FLT_08 | PASS | Codex | packets/FLT_08.md | Reset restored Smart Base/No criteria and the full nine-track map/Stats baseline. |
| FLT_09 | PASS | Codex | packets/FLT_09.md | Exact 2010+2026 selection produced the same seven tracks and totals across map, Review, heatmap/data layers, Overview, Trends, and Stats Tracks. |
| FLT_10 | PASS | Codex | packets/FLT_10.md | Main ON_FOOT and exact WALKING selections produced correct labels and two-track counts; CYCLING/BICYCLE each showed seven. |
| FLT_11 | PASS | Codex | packets/FLT_11.md | WALKING remained selected through a 2021 date range; BICYCLE reappeared unchecked after criteria expansion. |
| FLT_12 | PASS | Codex | packets/FLT_12.md | Empty exact-category selection stayed 0/9 across reload; selecting all normalized to All 2 categories and 9/9. |
| FLT_13 | PASS | Codex | packets/FLT_13.md | Selected WALKING remained visible as unavailable under 2010 criteria and was removable by unchecking it. |
| FLT_14 | PASS | Codex | packets/FLT_14.md | Same exact-type view retained WALKING-only 2/9; switching to year reset to All 4 categories and 9/9. |
| FLT_15 | PASS | Codex | packets/FLT_15.md | Reload resolved directly to restored 2010+2026 7/9 state; Review and Overview matched. |
| FLT_16 | PASS | Codex | packets/FLT_16.md | Legend hide changed map 7->3 while Stats stayed 7; adding 2013 reset hide and synchronized all surfaces at 8. |
| FLT_17 | PASS | Codex | packets/FLT_17.md | Unique clean origin auto-opened Important guidance; Got it prevented repetition on reopen. |
| FLT_18 | BLOCKED | Codex | packets/FLT_18.md | Desktop returning-user guidance passed; narrow viewport validation is blocked by the connected browser capability. |
| FLT_19 | BLOCKED | Codex | packets/FLT_19.md | Desktop pause/resume, persistence, and single-switch checks passed; mobile parity is blocked by viewport capability. |
| FLT_20 | BLOCKED | Codex | packets/FLT_20.md | Desktop Review/Stats shared browser passed; responsive narrow cards are blocked by viewport capability. |
| FLT_21 | BLOCKED | Codex | packets/FLT_21.md | Sheet controls worked structurally; exact dimensions and both mobile detents are blocked by visual/viewport capabilities. |
| TBS_01 | PASS | Codex | packets/TBS_01.md | Eight filtered rows populated name/date/activity/distance/duration/speed/energy/exploration/import fields. |
| TBS_02 | FIXED | Codex | packets/TBS_02.md | MTL-FR-008 fixed locally: full resolve includes indexed-file fields; sample.igc search passes at both viewports. |
| TBS_03 | PASS | Codex | packets/TBS_03.md | Six sort actions reordered all eight rows correctly; visible summary reduced/restored with search. |
| TBS_04 | PASS | Codex | packets/TBS_04.md | Presets switched correctly; search persisted and Distance sorting stayed usable after returning to All. |
| TBS_05 | PASS | Codex | packets/TBS_05.md | Stats row opened matching Mosel track 100001 details with Overview and maps. |
| TBS_06 | PASS | Codex | packets/TBS_06.md | Overview populated totals, ascent, activity breakdown, highlights, active periods, milestones, and recent activity. |
| TBS_07 | PASS | Codex | packets/TBS_07.md | Same-run 0/1/9-track Statistics states exposed correct counts and non-stale totals. |
| TBS_08 | PASS | Codex | packets/TBS_08.md | Five-import totals populated; deleting tracks 100002/100003 refreshed Stats to 7 tracks and 821 km without stale values. |
| TBS_09 | PASS | Codex | packets/TBS_09.md | Month/week/day Trends groupings rendered populated nine-chart/ten-series views with correct period counts. |
| TBS_10 | PASS | Codex | packets/TBS_10.md | Longest-track entry opened its one-track highlight drilldown and navigated to matching Mosel details. |
| TBS_11 | PASS | Codex | packets/TBS_11.md | One-row highlight drilldown/details passed; excluded count/list appeared and restoration returned Mosel ranking. |
| TBS_12 | PASS | Codex | packets/TBS_12.md | Lannion circle resolved one track; map/Overview/Trends/Tracks matched before and after reload settlement. |
| TBS_13 | BLOCKED | Codex | packets/TBS_13.md | Desktop pointer/Enter both opened Filter directly; mobile activation is blocked by viewport capability. |
| TBS_14 | FIXED | Codex | packets/TBS_14.md | MTL-FR-009 fixed locally: All indexed default and Track related scope pass at desktop/mobile sizes. |
| TBS_15 | FIXED | Codex | packets/TBS_15.md | MTL-FR-009/010 fixed locally: frozen scopes work and a media-only period keeps seven zero-filled activity cards. |
| TBS_16 | FIXED | Codex | packets/TBS_16.md | MTL-FR-009 fixed locally; original reachable mosaic/viewer paths pass and the frozen scopes are restored. |
| PLN_01 | PASS | Codex | packets/PLN_01.md | Planner opened with BRouter ready; Road Bike replaced Hiking as the active profile. |
| PLN_02 | BLOCKED | Codex | packets/PLN_02.md | Route computation/stats/profile pass; canvas-only rendered-line proof is blocked by ACC_04. |
| PLN_03 | PASS | Codex | packets/PLN_03.md | Dragging the route inserted an intermediate waypoint and changed one leg to two. |
| PLN_04 | PASS | Codex | packets/PLN_04.md | Move/delete/clear/undo/redo all produced the expected route and control state. |
| PLN_05 | PASS | Codex | packets/PLN_05.md | Distance, ascent, and duration updated across create/edit/delete/clear/undo/redo. |
| PLN_06 | PASS | Codex | packets/PLN_06.md | Profile rendered; hover exposed tooltip/crosshair and a visible linked map marker. |
| PLN_07 | PASS | Codex | packets/PLN_07.md | Named plan saved/listed/loaded/deleted; saved-plan list returned to empty. |
| PLN_08 | FIXED | Codex | packets/PLN_08.md | MTL-FR-011 fixed locally: desktop/mobile UI export downloads a valid 38-point GPX. |
| PLN_09 | PASS | Codex | packets/PLN_09.md | Controlled BRouter outage showed unavailable status and clear route recovery copy; service restored ready. |
| PLN_10 | PASS | Codex | packets/PLN_10.md | Existing route/stats/chart remained intact through failed reroute and BRouter recovery. |
| PLN_11 | BLOCKED | Codex | packets/PLN_11.md | Connected browser has fixed desktop geometry and no touch/mobile input context. |
| MCT_01 | PASS | Codex | packets/MCT_01.md | Two zones found four shared tracks; result table exposed speed/time/distance for each. |
| MCT_02 | PASS | Codex | packets/MCT_02.md | sample.geojson result opened Track Details #100010 for the expected synthetic track. |
| MCT_03 | PASS | Codex | packets/MCT_03.md | Stop removed active measurement behavior; reopening began with empty Zone A state. |
| MCT_04 | PASS | Codex | packets/MCT_04.md | Four-track compare kept local map and named chart series despite zero/missing speed/altitude values. |
| MCT_05 | PASS | Codex | packets/MCT_05.md | A-B results were non-empty bounded slices shorter than every source track extent. |
| MCT_06 | PASS | Codex | packets/MCT_06.md | Four local segments remained in an embedded 100 m comparison map; no global/off-continent refit. |
| AVR_01 | BLOCKED | Codex | packets/AVR_01.md | Desktop lifecycle passed; mobile/touch and pre-play canvas proof are unavailable. |
| AVR_02 | PASS | Codex | packets/AVR_02.md | Three racers advanced through live ranked cards to 100%; insufficient untimed row was skipped clearly. |
| AVR_03 | PASS | Codex | packets/AVR_03.md | Race reset/close left zoom and Filter usable; animation stop/finish likewise released tools. |
| AVR_04 | PASS | Codex | packets/AVR_04.md | Three racers stayed in a 30-50 m local race viewport; no global/off-continent geometry. |
| MED_01 | PASS | Codex | packets/MED_01.md | Media defaulted on with an eight-photo cluster; off/on states each survived reload. |
| MED_02 | PASS | Codex | packets/MED_02.md | Far pan issued new bounds requests and had no chooser; Bern return restored exactly eight photos. |
| MED_03 | PASS | Codex | packets/MED_03.md | Cluster photo rendered with details/location; Next and Previous changed/returned filenames. |
| MED_04 | PASS | Codex | packets/MED_04.md | Disposable HEIC indexed and rendered in mosaic/viewer as a complete 96x96 converted image. |
| MED_05 | PASS | Codex | packets/MED_05.md | Missing source produced an explicit 500 preview error with Retry/Download/details; cleanup restored eight active media files. |
| MED_13 | PASS | Codex | packets/MED_13.md | Eight capture-ordered rows and marker positions/origins matched exactly after page reload and app restart. |
| MED_14 | PASS | Codex | packets/MED_14.md | Photo GPS UI and read-only EXIF/resolved coordinates match the frozen fixture; origin is EXIF_EMBEDDED. |
| MED_15 | PASS | Codex | packets/MED_15.md | Estimated UI maps to persisted TRACK_INTERPOLATED correlation/resolved point on the raw route; original EXIF coordinates stay null. |
| MED_16 | PASS | Codex | packets/MED_16.md | +1h unsaved preview changed only camera-clock membership; Photo GPS rows and SQL stayed unchanged; Reset restored exact baseline. |
| MED_17 | FIXED | Codex | packets/MED_17.md | MTL-FR-012 fixed locally: shared global viewer exposes and completes Clear clock correction. |
| MED_18 | PASS | Codex | packets/MED_18.md | Set by you/manual notes preserved separate EXIF/route evidence; clear restored EXIF_EMBEDDED and TRACK_INTERPOLATED branches. |
| MED_19 | PASS | Codex | packets/MED_19.md | UI stored/showed two candidates per item; track 100013 stayed selected across alternate reimport and restart; cleanup restored baseline. |
| MED_20 | PASS | Codex | packets/MED_20.md | Add processed 4, replace the 6-item old/new union, and delete 2; resolved points updated/fell back with no MEDIA rescan. |
| MED_21 | PASS | Codex | packets/MED_21.md | 100k media and 300 activities: spatial and selected-correlation indexes used; HTTP means 26.146/26.876 ms; cleanup exact. |
| MED_22 | PASS | Codex | packets/MED_22.md | +15 min preview updated time/route while card, activity marker, viewer, mini-map, and Clear kept USER_ASSIGNED precedence; cleanup exact. |
| MED_23 | FIXED | Codex | packets/MED_23.md | MTL-FR-013 fixed locally: global viewer shows +1h correction and Position unknown at both viewports. |
| MED_24 | PASS | Codex | packets/MED_24.md | Identical bounds GET changed/restored media 400002 immediately after manual set/clear; all responses 200 no-store; cleanup exact. |
| MED_25 | PASS | Codex | packets/MED_25.md | Production watcher deletion preserved track_delete work after activity/correlation removal; worker selected fallback; fixture cleanup exact. |
| MED_26 | PASS | Codex | packets/MED_26.md | Deterministic batch failure deferred once with error/retry metadata; healthy follower completed; no tight retry; cleanup exact. |
| MED_27 | BLOCKED | Codex | packets/MED_27.md | Desktop cluster, map-view, single/multi-activity, and separated-marker paths passed; fixed viewport and eight-item fixture block the phone and adjacent-page branches. |
| MED_28 | FIXED | Codex | packets/MED_28.md | MTL-FR-014 fixed locally: activity media defaults to 100 and offers bounded 100/200 pages on desktop/mobile. |
| MED_29 | FIXED | Codex | packets/MED_29.md | MTL-FR-015 fixed locally: unknown viewer uses retained route coordinates and shows Position unknown/location map. |
| MED_30 | PASS | Codex | packets/MED_30.md | Desktop side/keyboard/swipe navigation, direct selection, collapse/expand, zoom/pan/reset, Details/time sources, fitted location map, and Open on main map all passed. |
| MED_31 | BLOCKED | Codex | packets/MED_31.md | Required 390x760 and 375x667 end-user viewer checks are unavailable because the only connected browser is fixed at 1049x942 with no viewport/device emulation. |
| MED_32 | BLOCKED | Codex | packets/MED_32.md | Panel maximize, state retention, navigation/zoom, Escape, and restore passed; native fullscreen is unavailable in the connected in-app browser. |
| MED_33 | FIXED | Codex | packets/MED_33.md | MTL-FR-014 fixed locally: activity viewer can receive the selected bounded 200-item page/window. |
| MED_34 | BLOCKED | Codex | packets/MED_34.md | Desktop defaults, independence, persistence, surfaces, focus, dark interactions, and preference cleanup passed; native fullscreen and phone Details sheet unavailable. |
| MED_35 | FIXED | Codex | packets/MED_35.md | MTL-FR-016 fixed locally: Photo tools copy and 100/200 paging pass at desktop/390x760. |
| MED_36 | FIXED | Codex | packets/MED_36.md | MTL-FR-017 fixed locally: visible video items show Open video labels/play badges at both viewports. |
| MED_37 | FIXED | Codex | packets/MED_37.md | MTL-FR-018 fixed locally: upper video swipes navigate on desktop/mobile while native controls remain reserved. |
| MED_38 | BLOCKED | Codex | packets/MED_38.md | Original MOV plays natively through full duration and serving/checksum pass; exact canPlayType and native seek control are inaccessible in the controlled browser. |
| MED_39 | PASS | Codex | packets/MED_39.md | MP4 stayed EXIF_EMBEDDED; MOV USER_ASSIGNED precedence preserved its track point and clear restored exact TRACK_INTERPOLATED state. |
| MED_40 | PASS | Codex | packets/MED_40.md | Reload and actual app restart preserved both video paths, playback, origins, 200/206/416, checksums, index/queues, and clean logs. |
| MED_41 | FIXED | Codex | packets/MED_41.md | MTL-FR-019 fixed locally: natural P720 HLS playback advances without MediaError on desktop/mobile. |
| MED_42 | PASS | Codex | packets/MED_42.md | All bounded lifecycle/cap/cleanup branches passed; normal app and 8/8/8 media, 0/0 queues, 0/0 transcode sessions/processes restored. |
| MED_07 | BLOCKED | Codex | packets/MED_07.md | IDs/checksums/coordinates/status/token and both visible pin paths recorded; required durable map screenshot blocked by ACC_04. |
| MED_08 | PASS | Codex | packets/MED_08.md | Exactly delete-a/delete-b moved to recoverable outside-watched backup with hashes intact; six other fixture media untouched. |
| MED_09 | PASS | Codex | packets/MED_09.md | GUI rescan changed MEDIA completed 8->6 and removed 5->7 with failures 0; freshness reload applied the new token. |
| MED_10 | PASS | Codex | packets/MED_10.md | Deleted IDs absent from markers/cards/filmstrip/bounds; photo-a -> Next -> photo-b and Open on main map passed at 100 m. |
| MED_11 | PASS | Codex | packets/MED_11.md | Pan/zoom/return/hard reload stayed at exact 6-photo cluster; duplicate/deleted pins absent and bounds is HTTP 200 no-store. |
| MED_12 | PASS | Codex | packets/MED_12.md | Both synthetic index rows are REMOVED with DELETE snapshots and zero active media_file rows. |
| HMO_01 | BLOCKED | Codex | packets/HMO_01.md | Toggle and 0/80/100 opacity pass with tracks independently on; canvas draw/stacking proof blocked by ACC_04. |
| HMO_02 | BLOCKED | Codex | packets/HMO_02.md | All seven independent toggle/0-100 opacity/attribution paths pass; pixel ordering relative to tracks blocked by ACC_04. |
| HMO_03 | BLOCKED | Codex | packets/HMO_03.md | Filter/heatmap state synchronized 8->1->8 and restored; rendered density-shape proof blocked by ACC_04. |
| GPS_01 | PASS | Codex | packets/GPS_01.md | Remote plain HTTP correctly triggers the visible HTTPS/localhost guard; live GPS rows classified per frozen rule. |
| GPS_02 | NOT APPLICABLE | Codex | packets/GPS_02.md | Remote HTTP is rejected before permission; accepted-permission marker path requires localhost/HTTPS per GPS_01. |
| GPS_03 | NOT APPLICABLE | Codex | packets/GPS_03.md | Follow me/drift requires an accepted live position, unavailable by design on remote HTTP. |
| GPS_04 | PASS | Codex | packets/GPS_04.md | Repeated GPS activation shows the exact HTTPS/localhost disabled message; alert closes and map remains usable. |
| GPS_05 | NOT APPLICABLE | Codex | packets/GPS_05.md | No active session/marker/update stream can exist on remote HTTP; disable transition requires localhost/HTTPS. |
| SRC_01 | PASS | Codex | packets/SRC_01.md | Zurich query returned labelled city/neighbourhood results with sort and zoom/type metadata. |
| SRC_02 | PASS | Codex | packets/SRC_02.md | Selecting Zürich moved the map to 100 m and placed a labelled, clearable search marker. |
| SRC_03 | PASS | Codex | packets/SRC_03.md | Clear search marker removed all marker controls without disturbing the usable map. |
| SRC_04 | PASS | Codex | packets/SRC_04.md | Impossible query showed `No matches`; cleared query showed explicit search guidance. |
| GLB_01 | PASS | Codex | packets/GLB_01.md | Zoom-out automatically changed the globe control from unpressed at 500 km to pressed/active at 1000 km. |
| GLB_02 | PASS | Codex | packets/GLB_02.md | Zoom-in from active globe returned to the flat zone at 50 km with normal controls intact. |
| GLB_03 | PASS | Codex | packets/GLB_03.md | Manual globe disable persisted through an in/out zoom cycle and re-enabled only on explicit toggle. |
| GLB_04 | PASS | Codex | packets/GLB_04.md | Both 1000 km minimum and 1 m maximum disabled only the blocked direction; opposite zoom recovered cleanly. |
| ADM_01 | BLOCKED | Codex | packets/ADM_01.md | All desktop Admin sections/routes passed; required mobile pass is blocked by unavailable viewport/touch emulation. |
| ADM_02 | FIXED | Codex | packets/ADM_02.md | MTL-FR-020 fixed locally: visible Admin upload rejects waypoint-only GPX before any watched file at both viewports. |
| ADM_03 | PASS | Codex | packets/ADM_03.md | GPS/MEDIA cards exposed live and terminal counts; Refresh updated cleanup totals and timestamp. |
| ADM_04 | PASS | Codex | packets/ADM_04.md | GPS/MEDIA queued messages, concurrent ALREADY_RUNNING, and map interaction all passed; not-ready was inapplicable. |
| ADM_05 | PASS | Codex | packets/ADM_05.md | Duplicate Finder and Exploration Score moved from running 93%/one pending to done 100%. |
| ADM_06 | PASS | Codex | packets/ADM_06.md | Tiles, GeoNames, and BRouter cards were ready with explicit progress, versions, sources, and counts. |
| ADM_07 | PASS | Codex | packets/ADM_07.md | Stale/current timestamps and index revisions were explicit; banner Reload synchronized exact tokens. |
| ADM_08 | PASS | Codex | packets/ADM_08.md | Populated 200-line server log loaded; Refresh advanced newest timestamp and reset freshness to just now. |
| ADM_09 | PASS | Codex | packets/ADM_09.md | Public/signed-in About, 12 source credits, Admin restoration, and login/map fallback all passed. |
| ADM_10 | PASS | Codex | packets/ADM_10.md | gcexport and fit-export were Ready; both Install actions reported Done and final Overview stayed 2/2 ready. |
| ADM_11 | PASS | Codex | packets/ADM_11.md | MEDIA rescan survived immediate Admin close; reopen showed freshness and a coherent done result. |
| ADM_12 | REJECTED | Codex | packets/ADM_12.md | MTL-FR-021 rejected: after startup readiness, direct Admin Close returns home once at desktop/mobile sizes. |
| SYN_01 | PASS | Codex | packets/SYN_01.md | GPS rescan triggered New data available with Reload/Dismiss after about 1 s; map stayed usable. |
| SYN_02 | PASS | Codex | packets/SYN_02.md | Banner Reload cleared stale state; map and both Statistics tabs consistently showed 8 tracks and matching totals. |
| SYN_03 | BLOCKED | Codex | packets/SYN_03.md | Import/delete source-of-truth passed on all accessible surfaces; rendered heatmap/polyline absence is blocked by ACC_04. |
| SYN_04 | PASS | Codex | packets/SYN_04.md | FIT conversion produced one track and followed the same freshness/banner/reload/cache path as native GPX. |
| SYN_05 | PASS | Codex | packets/SYN_05.md | Banner stayed hidden through 242.958 s despite another token change and reappeared at 300.520 s. |
| SYN_06 | PASS | Codex | packets/SYN_06.md | Re-login restored 8 Tracks; no banner through 25.345 s; final Data status was In sync/Healthy. |
| SYN_07 | PASS | Codex | packets/SYN_07.md | Processing Live/running progress was explicit; map close/zoom/navigation remained usable and work settled normally. |
| APP_01 | PASS | Codex | packets/APP_01.md | Root data-theme and exclusive pressed state switched light/dark immediately on every toggle without reload. |
| APP_02 | BLOCKED | Codex | packets/APP_02.md | Text remains present/structured in both themes; actual foreground/background contrast is pixel-only and blocked by ACC_04. |
| APP_03 | PASS | Codex | packets/APP_03.md | Nine Highcharts SVGs recolored point/grid contrast attributes dark-to-light without a page reload. |
| APP_04 | PASS | Codex | packets/APP_04.md | Dark remained the root/pressed theme across route reload, login page, re-login, and reopened Preferences. |
| APP_05 | BLOCKED | Codex | packets/APP_05.md | Persisted dark post-load state passed; transient first-paint flash needs unavailable screenshot/trace capture. |
| APP_06 | PASS | Codex | packets/APP_06.md | All seven map styles were selected under both light and dark UI roots; map style persisted through UI-only switch. |
| APP_07 | PASS | Codex | packets/APP_07.md | OSM Dark/Automatic/2D restored on `/mtl/map-settings` reload while UI independently stayed light. |
| APP_08 | PASS | Codex | packets/APP_08.md | 35% basemap/42% GPS persisted; reset restored preferred topo, all 100%, heatmap off, overlays none and persisted. |
| LOC_01 | PASS | Codex | packets/LOC_01.md | en-GB preview and live Stats agreed on day/month dates, comma grouping, metric scaling, and durations. |
| LOC_02 | PASS | Codex | packets/LOC_02.md | de-DE changed previews, separators, and all sampled Statistics dates immediately without reload. |
| LOC_03 | PASS | Codex | packets/LOC_03.md | de-DE separators/dates and selected preview persisted across `/mtl/stats` reload; Metric stayed independent. |
| LOC_04 | PASS | Codex | packets/LOC_04.md | Zero/large/missing plus injected negative/null values rendered without NaN/blank; exact track values restored. |
| LOC_05 | FIXED | Codex | packets/LOC_05.md | MTL-FR-022 fixed locally: distance summaries are localized and unit-aware at desktop/mobile sizes. |
| MOB_01 | BLOCKED | Codex | packets/MOB_01.md | Connected browser has a fixed desktop viewport and no touch/device emulation. |
| MOB_02 | BLOCKED | Codex | packets/MOB_02.md | Mobile sheet drag/snap gestures cannot be produced in the fixed desktop browser. |
| MOB_03 | BLOCKED | Codex | packets/MOB_03.md | Narrow rendered layout and overflow cannot be observed with the fixed desktop viewport/capture block. |
| MOB_04 | BLOCKED | Codex | packets/MOB_04.md | Planner touch taps/drags cannot be executed without touch input. |
| MOB_05 | BLOCKED | Codex | packets/MOB_05.md | Pinch/double-tap/touch-drag gesture matrix cannot be injected. |
| MOB_06 | BLOCKED | Codex | packets/MOB_06.md | Mobile filter-sheet start/transition/switch placement cannot be rendered or touched. |
| NET_01 | NOT APPLICABLE | Codex | packets/NET_01.md | Installed-PWA-only case; active client is a normal browser tab. |
| NET_02 | FIXED | Codex | packets/NET_02.md | MTL-FR-023 fixed locally: one Retry recovers parent Statistics and Overview at desktop/mobile sizes. |
| NET_03 | BLOCKED | Codex | packets/NET_03.md | Live 401 redirect passes; configured server has no reachable 403 role/endpoint path. |
| NET_04 | NOT APPLICABLE | Codex | packets/NET_04.md | Installed-web-app update lifecycle; active client is a normal tab and only one required build is in scope. |
| ERR_01 | FAIL | Codex | packets/ERR_01.md | Most paths are actionable; target startup failure lacks error/Retry (MTL-FR-003, fixed locally); map-config-only injection blocked. |
| ERR_02 | BLOCKED | Codex | packets/ERR_02.md | Rapid route/panel/error cleanup passes; canvas marker/cursor and listener inventory are not exposed. |
| UXP_01 | BLOCKED | Codex | packets/UXP_01.md | Three journeys, 123 APIs (max 353 ms), and console pass; UI-only/stall/pending instrumentation unavailable. |
| RUN_CLEANUP | PASS | Codex | packets/RUN_CLEANUP.md | Finalization gate passed; disposable Compose resources, exact directory, temporary capture, browser tabs, and SSH session removed or closed and endpoints verified unavailable. |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|
| MTL-FR-001 | P3 | RUN_SETUP | About reports `Version dev` instead of the precise deployed build identity available in server startup metadata. | FIXED |
| MTL-FR-002 | P2 | FMT_02 | IGC detail Download original and Download GPX controls are inert; no request or file is created. | FIXED |
| MTL-FR-003 | P2 | SGN_07 | Startup dependency failure exposes an empty 0-track map without error or Retry. | FIXED |
| MTL-FR-004 | P2 | TRD_05 | Point-count slider changes the request value but does not refresh the displayed chart series. | NOT REPRODUCIBLE |
| MTL-FR-005 | P2 | TRD_06 | Chart-created mini-map cursor remains visible after the pointer leaves the synchronized surfaces. | FIXED |
| MTL-FR-006 | P2 | TRD_15 | Track Details Close is inert when details opened from Filter Review tracks. | REJECTED |
| MTL-FR-007 | P3 | FLT_01 | Persisted active filter is not shown as a chip in Filter overview or catalog. | FIXED |
| MTL-FR-008 | P2 | TBS_02 | Track-browser file search returns no results for exact indexed filenames/paths despite the advertised file search field. | FIXED |
| MTL-FR-009 | P2 | TBS_14 | Trends Media lacks the frozen All indexed / Track related controls and shows Activity era / Media history / Matched only instead. | FIXED |
| MTL-FR-010 | P2 | TBS_15 | A media-only sub-unit removes all activity chart cards instead of retaining zero-value slots. | FIXED |
| MTL-FR-011 | P2 | PLN_08 | Saved-plan GPX export control produces no download or file. | FIXED |
| MTL-FR-012 | P2 | MED_17 | A saved camera correction can move an item outside the activity timeline, stranding its per-card Clear action; the global viewer has no equivalent. | FIXED |
| MTL-FR-013 | P2 | MED_23 | Global media viewer omits both the saved compact correction label and explicit Position unknown state when a corrected item has no resolved position. | FIXED |
| MTL-FR-014 | P2 | MED_28 | Activity Photos defaults to 25 items and exposes only 25/50, contrary to the frozen 100-default/200-maximum end-user page contract already supported by the backend. | FIXED |
| MTL-FR-015 | P2 | MED_29 | An unknown-provenance activity item keeps a valid gray circular route marker in the activity mini-map, but the viewer omits its location map and unknown marker. | FIXED |
| MTL-FR-016 | P3 | MED_35 | The activity Photos tab's single advanced disclosure is labelled `Media tools` instead of the frozen `Photo tools` label, although its gating and cleanup behavior works. | FIXED |
| MTL-FR-017 | P2 | MED_36 | The MP4 is correctly indexed, counted, and playable as video, but its main-map viewer filmstrip thumbnail is presented as a photo and has no visible video/play indicator. | FIXED |
| MTL-FR-018 | P2 | MED_37 | Pointer-swipe navigation works on photo items but does not navigate while the MP4 video is current; keyboard and side navigation still work. | FIXED |
| MTL-FR-019 | P2 | MED_41 | The generated 720p compatible HLS reaches ready metadata, but pressing Play fails decoding in both browser views; 480p remains a workaround. | FIXED |
| MTL-FR-020 | P2 | ADM_02, DAT_06 | Waypoint-only track upload reports success, then silently indexes as EMPTY_FILE with no per-file Admin outcome. | FIXED |
| MTL-FR-021 | P2 | ADM_12 | Direct Admin section routes require two Close activations; the first leaves the sheet and route unchanged. | REJECTED |
| MTL-FR-022 | P2 | LOC_05 | Distance-filter summary leaks a raw internal kilometre value without a unit in Imperial mode. | FIXED |
| MTL-FR-023 | P2 | NET_02 | Statistics Retry disappears without recovering after connectivity returns. | FIXED |

## Final Assembly Notes

- Missing coverage IDs: None
- Cleanup state: PASS; verified in `packets/RUN_CLEANUP.md`
- Final report path: `report.md`
- Finalization gate: PASS (235 coverage IDs terminal)
- Early closure approval: Not applicable
