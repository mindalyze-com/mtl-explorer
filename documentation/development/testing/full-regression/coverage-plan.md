# Regression Test Plan

Manual checklist for validating MTL Explorer end-to-end. Most checks use the app UI. Fixture setup, service-state changes, and API evidence need access to the disposable test environment.

For each item, perform the action and confirm the expected result. Note any deviation, including blank screens, error toasts, slow responses, or visually broken layouts.

Fresh-server runs do not use a personal Google account. They check Google
Health disabled, unconfigured, empty, and demo states only. Connected Health
checks are a separate local pass with the owner's existing account; see
[Google Health scope](#22-google-health). Do not copy account credentials,
tokens, local health data, or private tracks to a test server or report assets.

## Coverage Accounting

Coverage IDs such as `TRD_01` are stable report references. Once added, an ID
must never be renamed, renumbered, reused, or changed to mean a different check.
Add new checks with the next unused number in the closest matching ID prefix. If
a check becomes obsolete, keep the ID and mark it `NOT APPLICABLE` or explain
the replacement. Packet results and full-regression reports must reference
coverage IDs, not section numbers alone.

| Prefix | Area |
|---|---|
| `ACC` | Coverage accounting |
| `DAT`, `IMP`, `DEL`, `FIT`, `FMT` | Required data-change flows |
| `SGN` | Sign-in and first load |
| `MAP` | Map and tracks |
| `TRD` | Track details |
| `FLT` | Filters |
| `TBS` | Track browser and statistics |
| `PLN` | Planner |
| `MCT` | Measuring and comparison tools |
| `AVR` | Animation and virtual race |
| `MED` | Media |
| `HMO` | Heatmap and overlays |
| `GPS` | GPS location |
| `SRC` | Location search |
| `GLB` | Globe mode |
| `ADM` | Settings and operations |
| `SYN` | Data updates and sync |
| `GHL` | Google Health optionality and local connected checks |
| `APP` | Appearance |
| `LOC` | Locale, units, and formatting |
| `MOB` | Responsive mobile and touch |
| `NET` | Offline and network issues |
| `ERR` | Error recovery |
| `UXP` | UX performance |

- **ACC_01** Treat every checklist bullet as a required coverage item unless it is
  explicitly not applicable to the run.
- **ACC_02** Do not mark a section `PASS` when any bullet inside it was skipped, only
  spot-checked, or verified indirectly. Mark the section `PARTIAL`, `NOT
  COVERED`, `NOT APPLICABLE`, `BLOCKED`, or `FAIL` as appropriate.
- **ACC_03** In full-regression reports, include enough coverage detail to show which
  bullets were actually exercised and which were not. Broad section summaries
  are acceptable only when all child bullets have supporting evidence.
- **ACC_04** Capture compact screenshots for representative working user-facing functions
  as well as failures, so reports provide a visual overview and not only defect
  evidence.
- **ACC_05** If time, tooling, viewport, data, permissions, or environment constraints
  prevent a check, record that constraint explicitly instead of silently
  collapsing the check into a parent row.

## Required Data-Change Regression

Run this in every full regression and every release-candidate data-change pass.

### Public Test Data

- **DAT_01** Use at least **five public internet GPX files** with real track sequences (`trk` / `trkseg` / `trkpt`). Waypoint-only files (`wpt` without `trkpt`) are not valid positive import evidence.
- **DAT_02** Prefer GPX files with timestamped trackpoints so duration, speed, moving time, and period statistics can be verified.
- **DAT_03** Record for every source file: source URL, source page/license note, destination filename, SHA-256, byte size, `trkpt` count, timestamp count, imported track id(s), and imported track name(s).
- **DAT_04** Suggested verified GPX source: `https://github.com/gps-touring/sample-gpx` with raw files such as:
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/JuraRoute72011.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/MoselradwegAusWiki.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/Vitry-le-Francois_Langres.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/VoieVerteHauteVosges.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/RoscoffCoastal/Lannion_Plestin_parcours24.4RE.gpx`
- **DAT_05** Use at least **one public FIT activity file with GPS positions**. Suggested verified FIT source: Garmin's official FIT SDK examples, e.g. `https://raw.githubusercontent.com/garmin/fit-javascript-sdk/main/test/data/Activity.fit`.
- **DAT_06** Do not count non-GPS FIT files or waypoint-only GPX files as positive evidence. They are useful negative tests only: MTL Explorer should fail, ignore, or mark them clearly without adding map tracks, corrupting stats, or blanking the UI.
- **DAT_07** For measure / comparison / virtual-race checks, include at least one repeatable two-point segment with two or more tracks crossing the same two zones. Use public tracks or fully synthetic anonymized GPX data; do not use private local GPX files as fixtures or report evidence.
- **DAT_08** Use the fully synthetic, non-private media set generated by the packaged
  `/app/demo/generate_regression_photos.py` helper. It uses the same shared
  renderer as the demo photos and ffmpeg for short videos. The helper produces
  four GPS JPEGs, two camera-time-only JPEGs, one H.264/AAC MP4 with embedded
  GPS and capture time, one H.264/AAC MOV with capture time but no GPS, and a
  matching six-point synthetic GPX activity. Record the generated manifest,
  including checksums and ffprobe data. Copy the GPX into `data/gpx/<run-id>/`
  and all eight media files into `data/media/<run-id>/`; keep the manifest and
  deletion backups outside the watched media tree.

- **DAT_09** Prepare fully synthetic GPS cases for timing and motion: repeated visits to two ordered zones, a recorded pause below one minute, a longer observed stop, a source recording break, and missing/repeated/reversed timestamps. Record expected intervals and geometry independently of displayed results. Keep these fixtures outside the five-GPX count assertions and retain them through segment/replay checks. Do not derive them from local tracks.

### Five-GPX Import, Index, Map, And Stats Flow

- **IMP_01** Capture baseline map count, track-browser count, statistics totals, data-freshness token, and GPS indexer status.
- **IMP_02** Import the five GPX files through the Settings → Import & sync upload UI or by placing them in the documented watched import folder.
- **IMP_03** Wait for indexing to finish. If live file watching does not react, trigger **Rescan GPS** from Settings → Maintenance and record that it was needed.
- **IMP_04** Confirm upload/index status: all five source files reach completed state, no unexpected GPS index failures appear, data freshness changes, and background jobs (Duplicate Finder, Exploration Score) settle.
- **IMP_05** Reload from the freshness banner or helper reload action → the map, track browser, filters, and statistics all show the new data.
- **IMP_06** Verify each imported file by name: it appears in track browser search, on the map, in statistics summaries, and in at least one filter result.
- **IMP_07** On the map, zoom to the imported tracks, click each track, verify selection/detail opening, point popups, visible line geometry, and no stale or duplicated lines.
- **IMP_08** In statistics, verify count increased by five unless a source file legitimately split into multiple tracks; if it split, record the source-to-track mapping and expected count.
- **IMP_09** Verify totals changed in the correct direction: total distance, duration, ascent/descent, activity breakdown, period charts, rankings, heatmap density, and track-browser summary row.

### Delete-Two-Track Flow

- **DEL_01** Delete two of the imported source files from the watched import/upload folder. If the test environment only exposes browser upload and not the watched folder, mark deletion sync `BLOCKED` and run this flow in the install/full-regression environment.
- **DEL_02** Wait for automatic delete processing or trigger **Rescan GPS**.
- **DEL_03** Verify the two deleted tracks disappear from the map, track browser, filter results, selection lists, heatmap, related-track lists, and statistics totals.
- **DEL_04** Verify the remaining imported tracks still display and open correctly.
- **DEL_05** Deleted-track API probes or stale deleted-track URLs are not pass/fail criteria
  for this deletion flow. The frontend regression requirement is that deleted
  tracks no longer appear in user-visible map, browser, filter, heatmap,
  related-track, detail, or statistics surfaces.

### FIT Conversion Flow

- **FIT_01** Import the FIT activity file with GPS positions.
- **FIT_02** Verify it is accepted by upload/import, indexed successfully, displayed on the map, searchable in the browser, and included in statistics.
- **FIT_03** Open the FIT-backed track details → overview, graphs, quality, events, related tracks, mini-map, and point popups render as they do for GPX-backed tracks.
- **FIT_04** **Download original source file** → the downloaded file remains FIT and matches the uploaded checksum.
- **FIT_05** **Download as GPX** → a valid GPX file downloads and contains real `trkpt` trackpoints, not only waypoints.
- **FIT_06** If GPSBabel or FIT conversion is unavailable, the UI shows a clear conversion/indexing error and the failure is recorded as blocking for FIT support.

### Other Supported Track Formats

- **FMT_01** The server accepts `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.nmea`, `.geojson`, and `.gdb`. For a full release regression, test at least one GPS-bearing sample for each available format, or mark that format `NOT COVERED` with the reason.
- **FMT_02** For each non-GPX format tested, verify upload acceptance, GPSBabel conversion, map display, details/charts, statistics inclusion, **Download original source file**, and **Download as GPX**.

### Media Fixture Setup Flow

- **MED_06** Capture the media count, MEDIA indexer status, and media freshness
  baseline. Import the `DAT_08` matching GPX first and wait for GPS indexing.
  Place all six generated JPEGs and both generated videos in the watched media
  folder, open Settings → Maintenance, trigger **Rescan Media**, and wait for MEDIA indexing and
  correlation to settle. Accept the freshness reload and verify all eight files
  are completed without failures. Preserve the generated manifest and exact
  source files outside the watched tree for later deletion evidence and recovery.

## 1. Sign-in And First Load

- **SGN_01** Open the app while signed out → you are redirected to the login screen.
- **SGN_02** Sign in with valid credentials → you reach the map.
- **SGN_03** Sign in with wrong credentials → a clear error appears and you stay on login.
- **SGN_04** If demo mode is active, the login screen shows the demo credentials banner.
- **SGN_05** Sign out → you return to login; signing in again works.
- **SGN_06** The splash screen (logo, background, message) displays during startup and disappears once the map and tracks are loaded.
- **SGN_07** If startup fails (e.g. server down), a retry is offered instead of a frozen splash.
- **SGN_08** "MTL Explorer" branding appears in About / public-facing copy.
- **SGN_09** Browser back/forward navigation between views works without errors.
- **SGN_10** On desktop and phone, in light and dark themes, Sign In retains its translucent background, blur, white label, and visible keyboard focus. Check hover where supported, original field height and blur, and demo banner readability; successful and failed sign-in retain the same treatment on return.

## 2. Map And Tracks

- **MAP_01** Base map and overlays load on first open.
- **MAP_02** All your tracks appear on the map; the total/visible count is correct.
- **MAP_03** Newly imported tracks from the required data-change flow appear without a full browser restart after accepting the freshness/reload prompt.
- **MAP_04** Deleted tracks from the required data-change flow disappear from all map sources, selection lists, and popups.
- **MAP_05** Zoom in on a track → detail/precision improves (no duplicate or broken lines).
- **MAP_06** Fast pan/zoom doesn't leave stale lines, missing tiles, or runaway loading spinners.
- **MAP_07** Direction arrows appear on tracks at high zoom when Track Points & Direction is enabled.
  Use a track whose current high-zoom rendered shape has visible in-viewport point vertices.
  A sparse two-point or straight synthetic track whose endpoints are outside the viewport is not valid evidence:
  the line may cross the screen while no arrow marker is expected at the visible midpoint.
- **MAP_08** Click a single track → it highlights and details open.
- **MAP_09** Click an area where several tracks overlap → a selection list appears; picking one opens its details.
  Categories whose lookups complete with no results have no chip or result group.
  Pending and failed/cancelled categories stay visible. If a selected pending category
  completes empty, All becomes active and shows the remaining results or “Nothing found here”.
  GPS details open expanded without widening the standard desktop panel. They
  use a separate foreground sheet; mobile details fill the screen to the top.
  The dispatcher keeps a constant height while details open, resize and close.
  Back, Close and browser Back reveal its unchanged size and category/page.
- **MAP_10** Deselect / close the selection → the map returns to its normal state.
- **MAP_11** Clicking a rendered track-point marker shows a popup with the expected metrics
  (time, speed, elevation, etc.). Click an actual direction-arrow/point marker, not only the
  connecting track line.
- **MAP_12** Swiss Mobility routes popup (where applicable) shows nearby official routes and closes cleanly.
- **MAP_13** Intentional remote raster mode: with `mtl.map-server.tile-mode=remote`,
  `/api/map/config` exposes `remoteRasterStyles` for `light`, `light-topo`, and
  `dark`, and does not expose legacy `remoteTileUrl`. Select OSM Light, OSM Topo Light,
  and OSM Dark; each loads map tiles from its configured provider URL, shows the
  matching attribution, keeps the map interactive, and makes no `/api/map-proxy`
  tile requests.
- **MAP_14** Remote raster fallback from local vector mode: when local vector
  PMTiles are not ready or fail at runtime, the map does not blank or freeze. It
  switches to the configured remote raster style for the selected map theme, uses
  the configured attribution, and continues to support pan, zoom, track display,
  and track selection. If the environment cannot safely simulate local tile
  unavailability, mark this row `BLOCKED` with the missing control.
- **MAP_15** Manual remote raster source override: in a local-vector deployment,
  open **Map → Map style**, switch Map Source from Auto to Remote, and verify the base
  map reloads from the configured remote raster provider without `/api/map-proxy`
  tile requests. OSM raster themes remain selectable, Swiss vector themes are not
  offered in Remote mode, the setting persists after reload, and Reset restores
  Auto.

- **MAP_16** Open the Map overview, then Map style, terrain, personal data, and route-overlay screens. Each Back action restores the overview; summaries reflect the changes. Repeat on a phone and after reload; reset restores defaults without changing the UI theme.

## 3. Track Details

- **TRD_01** Open at least one GPX-backed track and one FIT-backed track from user-facing navigation (map, browser, or stats) and record the track ids/source filenames.
- **TRD_02** Opening a track loads its overview, charts, related-tracks list, event list, mini-map, and quality info.
- **TRD_03** Switch between **Overview**, **Graphs**, **Quality**, **Related**, and **Events**; tabs do not refetch in a loop, lose state, or show blank panels.
- **TRD_04** Elevation, speed, distance, and gain charts render with readable values.
- **TRD_05** Graph controls work: time/distance x-axis toggle, range band toggle, point-count slider, and graph-height slider update charts without layout breakage.
- **TRD_06** Hovering a chart highlights the matching point on the mini-map and hovering the mini-map highlights the chart. No stale cursors remain after leaving either surface.
- **TRD_07** The track shape preview (small thumbnail) is visible in browser, filters, stats, related tracks, and selection lists.
- **TRD_08** **Download original source file** (GPX/FIT/etc.) → file downloads and matches the uploaded one.
- **TRD_09** **Download as GPX** → a valid GPX file downloads even if the source was FIT or another format.
- **TRD_10** **Change activity type** (e.g. hiking → cycling) → saves successfully; energy/calorie values update automatically.
- **TRD_11** **Energy "what-if" recalculation** (custom rider weight, etc.) → updates the displayed values without permanently saving.
- **TRD_12** **Exclude from statistics** toggle → the track stops counting in stats overview; re-including it brings it back.
- **TRD_13** **Related tracks** show duplicates and previous/next tracks; clicking one navigates to it.
- **TRD_14** **Events tab** shows detected stops / GPS gaps where present; selecting an event highlights the matching mini-map position and deselects cleanly.
- **TRD_15** Open Track Details from **Statistics → Tracks** and from **Filter → Review tracks**. Closing details returns to the same originating sheet and list state. Repeat with browser Back/Forward and on narrow mobile; a direct track-detail link closes to the map.
  The referring panel stays open at a constant height underneath GPS details.
  Check the latest-activity shortcut and Related track links too; returning must
  reveal the existing panel without a closing or reopening animation.

- **TRD_16** Open **Quality → GPS review** on the synthetic continuity cases. Original and processed paths, source quality, processing decisions, and unsupported intervals remain distinguishable. Selecting a review item highlights its local geometry without connecting disconnected recording spans.
- **TRD_17** On the synthetic motion cases, compare Overview and Events: moving + stopped + unknown equals elapsed duration for a complete timeline. Short observed pauses count toward stopped time but do not create break events below one minute. Longer breaks show approximate boundaries; missing or invalid time remains unavailable/unknown, not a zero-duration stop. See [motion rules](../../../architecture/gps/motion.md).
- **TRD_18** Local connected Health pass only: compare whole-track and moving heart-rate summaries with their server values. Whole-track heart rate includes recorded stops; moving heart rate uses moving intervals only. Time/distance graphs share the full track extent and preserve partial coverage. A track without samples shows unavailable values and a useful sync-status link. On fresh servers mark `NOT APPLICABLE`: personal Health account required.
- **TRD_19** Open a track without Health samples on the fresh server. Core metrics, graphs, mini-map, Quality, and Events remain usable; missing heart rate does not cause repeated requests, blank core charts, or fabricated zero values.

## 4. Filters

- **FLT_01** Open the filter panel → previously saved filter is still active and shown as a chip.
- **FLT_02** Browse the filter catalog; search and grouping work.
- **FLT_03** Choose a filter in **Filter view**, confirm **Use this view**, and open **Criteria** (required missing criteria open automatically). Valid parameter edits auto-apply immediately; clearing or removing parameters resets their effect; active chips, visible count, map, legend, and stats reflect the current state without stale pending UI.
- **FLT_04** Date, text, and geo parameters all save and re-apply correctly after reload.
- **FLT_05** **Geo drawing**: draw a circle, rectangle, and polygon; undo, cancel, finish, and clear all work; saved shapes reappear next time.
- **FLT_06** Applied filter updates: visible track count, map colors, legend, and stats — without a full page reload.
- **FLT_07** Legend reflects the active filter (categories or gradient); collapsing/hiding groups updates the map immediately.
- **FLT_08** Clearing the filter restores all tracks.
- **FLT_09** Choose a grouped year filter, select two result categories, and verify the map, visible count, track browser, heatmap, Statistics Overview, Trends, and Stats Tracks all use the same selected tracks.
- **FLT_10** Repeat result-category selection with main activity groups and exact activity types such as Walking and Hiking. Labels and counts remain correct.
- **FLT_11** Change date, text, or geo parameters while an exact result-category selection is active. The selection remains active; newly discovered categories remain unchecked.
- **FLT_12** Select none and verify the empty result is intentional and stable. Select every available category and verify it returns to All categories when there are no unavailable saved categories.
- **FLT_13** Change parameters so a selected category has no matches. It remains visible as unavailable and can be removed.
- **FLT_14** Switch to a different filter and verify the result-category selection clears. Select the current filter again and verify its selection remains.
- **FLT_15** Reload with an exact result-category selection. It is restored before the first resolution and all filter-aware views still match.
- **FLT_16** Hide a group with the map legend visibility control. Map visibility changes, but statistics do not. Changing the global Result categories selection resets temporary legend hiding during map reload.
- **FLT_17** `NOT APPLICABLE` in the current UI: automatic first-visit filter guidance was removed. Keep this ID for historical reports; the current overview and on-demand guidance are covered by `FLT_18` and `FLT_22`.
- **FLT_18** Reopen Filter as a returning user on desktop and narrow mobile. Guidance stays inside the result summary, remains compact without overflow, and **More info** opens the full explanation without the Important badge. Back returns to Filter; Close closes the sheet.
- **FLT_19** Pause and resume from the **Filtering on / Filtering off** switch in the result summary. The result status, map, statistics, persisted state, and switch position remain synchronized on desktop and mobile; no header switch or duplicate action appears.
- **FLT_20** Open **Review tracks** from the shared footer beside **Show on map**. The review uses the same track-browser view as Statistics → Tracks, including search, summary, responsive table/cards, sorting, pagination, map selection, and track details. Recheck Statistics → Tracks after the shared-component change.
- **FLT_21** Filter and **Review tracks** use the standard Filter sheet dimensions and detents. Verify the nested review does not jump to an oversized desktop sheet and remains usable at both mobile detents.

- **FLT_22** Open Filter as a new and returning user. The overview provides Filter view, Criteria, Included categories, Map colors, Review tracks, and Show on map. Open each available inner screen and return with Back. Closing or Show on map keeps the filter active; reopening from the map result count works.
- **FLT_23** Stage a different Filter view. Compatible criteria are retained; criteria that would be cleared are listed before confirmation. A custom category order shows a notice that it will reset to the new view's default. Cancel preserves the old view and order. Use this view applies the choice, clears old category selection and custom order, and opens missing required criteria without sending an invalid query.
- **FLT_24** In Criteria, exercise Last 30 days, This year, Last year, and All time. Both date bounds update together. A reversed range shows a field error, disables Done, and retains the last successful map result; correcting it recovers. While paused, valid edits retain their draft and Apply and enable restores the intended result.
- **FLT_25** Search Included categories, stage a subset, and verify the displayed draft count before Apply while the map remains unchanged. Cancel discards edits; Apply updates map, review, Statistics, heatmap, and Segments consistently. Empty search and zero selected categories provide recovery actions.
- **FLT_26** Reset a non-default view with criteria, categories, and colors, then Undo while offered. The previous configuration and result return. Map color changes and Show all for hidden map groups leave the statistical track set unchanged. SQL inspection remains a secondary action.
- **FLT_27** On a narrow phone, complete and cancel circle, rectangle, and polygon drawing from Criteria. Return to Criteria with the correct retained/updated values. Back, Done, Review tracks, and Show on map remain reachable without horizontal scrolling.
- **FLT_28** In a view with a preferred palette, apply No coloring and reload. The choice survives without changing dates, categories, or matches. Repeat with filtering paused, then resume and switch between category and gradient views. Reset uses the view defaults; Undo restores No coloring. Repeat on desktop and phone.

## 5. Track Browser And Statistics

- **TBS_01** Track browser lists all (or filtered) tracks with name, date, distance, duration, activity, etc.
- **TBS_02** Search matches names, descriptions, dates, distances, durations, activity, and file paths.
- **TBS_03** Sort by each column works; summary row reflects what is currently visible.
- **TBS_04** Quick-view/preset buttons switch the browser subset correctly and preserve usable sorting/search behavior.
- **TBS_05** Clicking a row opens the track's details.
- **TBS_06** Statistics overview shows total distance, time, elevation, activity breakdown, rankings, milestones, and period charts.
- **TBS_07** Stats are correct for: empty dataset, a single track, and many tracks.
- **TBS_08** Stats update after the required five-GPX import and again after deleting two imported tracks; no stale deleted-track totals remain.
- **TBS_09** Time-period charts (daily/weekly/monthly) render and switch correctly.
- **TBS_10** Clicking a stats entry navigates / filters / highlights as expected.
- **TBS_11** Highlight drilldowns open the expected track list, open a selected track, and expose excluded-highlight counts where applicable.
- **TBS_12** Statistics use the same resolved track set as the active filter:
  apply a geo-drawn filter (circle, rectangle, or polygon) that changes the
  visible map count, then verify Stats Overview totals, Trends track totals,
  and the Stats Tracks tab all match the filtered map count. Repeat after a
  reload to confirm the fallback ID-resolution path still matches.
- **TBS_13** When Statistics Overview shows **Showing X of Y tracks**, activate the summary with pointer and keyboard. It opens Filter directly on desktop and mobile.
- **TBS_14** Open **Statistics → Trends → Charts**. Media is the final chart;
  **All media** is selected by default in the control at the top. Both choices
  expose visible, keyboard-accessible help. The choice is shared with
  **Map settings → Your data** and Overview media counts.
- **TBS_15** Change grouping and sub-unit. Activity and media periods remain
  aligned, with zero values where needed. Apply date, category, geographic and
  custom filters: media follows the earliest valid start and latest valid end
  of the actual matching tracks, including gaps and both endpoints. Older,
  newer and undated media never appear. Empty/undated track selections show no
  media. **All media** includes unlinked items; **Track related** additionally
  requires a selected link to a matching track. Map markers refresh with both
  filter and scope changes, and stale requests cannot restore old markers.
- **TBS_16** Select a non-empty photo or video stack. The period mosaic uses the
  same track-defined range and opens with the correct total, All/Photos/Videos
  filters, stable newest-first cards, 60-item **Load more** pages, recoverable
  loading/error states, and shared viewer navigation. In Track related scope,
  **Open activity** returns to that activity's Media tab; the action is absent
  in All media scope. There is no undated drill-down.

- **TBS_17** Open Statistics and each Overview, Trends, and Tracks entry. Back returns to the hub and restores focus; track search, sorting, and Trends controls survive the round trip. The latest-activity preview respects the current track set, opens details, and handles an empty set.
- **TBS_18** Change Trends chart height and order, reload, and restore defaults. The saved layout returns, height stays usable within the viewport, and the media chart remains included. Repeat on a narrow phone in light and dark themes.

## 6. Planner

- **PLN_01** Open **Planner → Start a new plan** (or **Continue your current plan** when a draft exists); pick a routing profile (e.g. hiking or touring bike).
- **PLN_02** Click on the map to add waypoints → a route is computed and drawn.
- **PLN_03** Insert a waypoint on an existing leg by dragging the route.
- **PLN_04** Move and delete waypoints; clear, undo, and redo all work.
- **PLN_05** Live stats bar (distance, ascent, time) updates as you edit.
- **PLN_06** Elevation profile renders and hovering it highlights the matching map point.
- **PLN_07** **Save plan**, list saved plans, load a saved plan, delete a plan.
- **PLN_08** **Download plan as GPX** → file is valid and matches the planned route.
- **PLN_09** If the routing engine (BRouter) is missing data for an area, the UI shows a clear "segment downloading / unavailable" state instead of an unhandled error.
- **PLN_10** Existing planned routes still display even when the planner has trouble fetching new data.
- **PLN_11** Touch dragging on mobile works for placing and moving waypoints.

- **PLN_12** From the Planner hub, open the contextual guide, return, draw a draft, and return to the hub again. The draft survives; map clicks edit only in the drawing screen. Saved plans and drawing remain reachable on desktop and mobile.
- **PLN_13** Select numbered waypoints and exercise tap-to-move, cancel move, insertion, removal, reverse, and trim before/after. Undo/redo restores geometry, stats, and routing profile. Drag release must not add an extra point; point actions remain reachable on touch screens.
- **PLN_14** Search and open a saved plan. Its exact saved geometry, profile, notes, and stats load and the map frames it above the panel. With an unsaved draft, cancel replacement and confirm the draft survives; then explicitly replace it. Loading failures offer retry without silently clearing the draft.
- **PLN_15** Save after an edit while routing is pending. Save waits for current geometry and prevents duplicate submission. A failed save keeps name/notes for retry. Editing a saved plan offers **Save copy** and leaves the original intact; success offers a working GPX download. A backend 200 alone is not evidence of browser file delivery.
- **PLN_16** With unsaved changes, attempt to leave/reload the page and cancel the browser prompt. The draft remains. Trigger a slow route request, then change profile, clear, undo, or load a saved plan; a late reply must not overwrite the newer state.
- **PLN_17** On desktop and phone, use **More map**, **Show tools**, slow and fast panel drags, and grabber taps. Totals, profile, and Undo stay visible without scrolling; the profile stays readable and its map cursor aligned until the totals-only size. Downward flicks never close the drawing panel, the chosen size returns on the next visit, and route updates keep the previous totals until the new result arrives.

## 7. Measuring And Comparison Tools

- **MCT_01** Open **Segments**, place start and end zones, and choose **Analyze** → crossing attempts appear with speed/time/distance.
- **MCT_02** Clicking a result opens that track's details / segment view.
- **MCT_03** Stop the measure tool → all temporary markers and listeners are cleaned up.
- **MCT_04** Segment comparison: pick several tracks → comparison chart + map align them correctly even with missing data.
- **MCT_05** Sub-track / segment extraction (between two points on a track) returns the expected slice.
- **MCT_06** Segment geometry sanity: after selecting a measured segment, the comparison map line stays within the selected tracks' real local bounds. There must be no straight global line, jump to `[0,0]`, or off-continent segment such as a line toward South Africa.

- **MCT_07** Place multiple ordered zones, adjust the radius, undo a zone, and Analyze. Zone counts and the results describe the same filtered tracks. Empty results explain how to adjust the selection; editing zones invalidates the old analysis.
- **MCT_08** Using repeated-visit fixtures, select individual attempts and a reference, then switch among result table, charts, and Replay. Labels, colors, reference, and exact zone/visit identities stay consistent; Select all is explicit. Consolidated summaries retain separate valid visit counts for each metric.
- **MCT_09** Compare exact attempt boundaries, distance, elapsed time, energy, ascent/descent, and power with server results for the synthetic fixtures. Changing chart density changes presentation only. Fractional boundary work must not include whole-track energy outside the segment. See [segment rules](../../../architecture/analysis/segments.md).
- **MCT_10** Analyze missing/repeated/reversed-time and recording-gap cases. Untimed attempts remain inspectable where geometry permits; unavailable timing is labelled rather than ranked as zero. Core charts preserve gaps and supported partial coverage without inventing a bridge.
- **MCT_11** On a fixture with a recorded stop, compare finish faster/slower labels and **Time difference at equal traveled distance**. The latter stays on a distance axis, includes stopped time, ends at shared coverage, and is not described as spatial lead/lag.
- **MCT_12** Inspect aligned graphs by hover, click-to-pin, keyboard, and touch. The shared value panel and map position agree; selecting an attempt from the whole value card updates highlighting. The panel sits beside charts when space permits and below them on a phone.
- **MCT_13** Local connected Health pass only: verify segment HR, coverage, cardiac cost, and unavailable reasons against server values for matching local samples. Ordinary HR includes recorded stops; cardiac cost uses covered moving intervals including coasting. Consolidated HR uses covered-time weighting. On fresh servers mark `NOT APPLICABLE`: personal Health account required.
- **MCT_14** On a fresh server without Health data, selected core results, charts, and replay still load. Missing Health results remain unavailable. Retry a failed core attempt without losing other selected results; rapidly changing selection must not restore stale responses.
- **MCT_15** Reimport or remove a disposable synthetic source after discovery, or let its attempt reference expire. Loading the old attempt offers **Refresh results**. Refresh rediscovers current data instead of combining old geometry with new metrics. Preserve the shared fixture set needed by later checks.

## 8. Animation And Virtual Race

- **AVR_01** Open Animate on desktop and mobile: existing tracks remain visible until preview or playback starts; Play collapses to the map-first transport bar; pause, resume, stop, expand, reset, date range, and speed controls work; estimated duration updates.
- **AVR_02** Virtual race: multiple racers move together; ranking and racer cards update in real time.
- **AVR_03** Stopping or finishing animation/race leaves map gestures and tools usable (no stuck state).
- **AVR_04** Virtual race GPS geometry regression: start a race from a measured segment with multiple racers and verify each racer marker and trail stays on the actual segment. The map must not zoom to world-scale bounds, draw a long straight line away from the route, or show a racer/trail near `[0,0]` or South Africa.

- **AVR_05** Start Replay from selected segment attempts. Play/pause, speed, reset, and elapsed-time scrubbing update markers, trails, and rankings consistently. Rankings retain activity/date labels; switching back to charts preserves selection and reference.
- **AVR_06** Replay a synthetic attempt with a recording gap and one without usable timing. Unsupported portions must not become invented movement; timing-unavailable attempts are not presented as valid timed racers. Stop and return to the map with normal gestures restored.

## 9. Media (Photos and Videos)

- **MED_01** With no saved map preference, the media layer is enabled and photo
  pins appear by default. Disable it and reload to verify it remains disabled;
  enable it and reload to verify it remains enabled.
- **MED_02** Pan/zoom → media is loaded for the current viewport (not the whole world at once).
- **MED_03** Click a pin → photo preview opens; next/previous navigation works.
- **MED_04** HEIC photos display correctly (converted server-side).
- **MED_05** A missing/broken photo shows a recoverable error, not a blank sheet.

### Activity Photo Correlation Flow

Use a timestamped public or fully synthetic activity and synthetic photos. The
fixtures must cover embedded EXIF GPS, camera time without GPS, a saved camera
correction, and an overlapping activity window.

- **MED_13** Open Track Details → Media. The baseline request returns persisted
  timeline rows in capture-time order. Reload the page and restart the server;
  the same rows, positions, origins, and order remain.
- **MED_14** Verify an embedded-GPS photo is labeled **Photo GPS**. Read-only
  database inspection shows its original EXIF point unchanged and the resolved
  origin `EXIF_EMBEDDED`.
- **MED_15** Verify a camera-time-only photo is labeled **Estimated** and its
  marker lies on the activity route. Database inspection shows a persisted
  `TRACK_INTERPOLATED` correlation and a separate resolved projection; it must
  not create original EXIF coordinates.
- **MED_16** Enter a camera offset and choose **Preview**. The UI marks the result
  as unsaved, changes only camera-clock photos, and leaves EXIF GPS time unchanged.
  Reset returns to the persisted baseline without a database change.
- **MED_17** Save the previewed correction. Reload and restart to verify the
  correction remains. Use **Clear clock correction** and verify the original
  capture metadata is unchanged and the baseline correlation is restored.
- **MED_18** Set a manual location and optional note. The item and marker are
  labeled **Set by you**. Read-only inspection confirms the manual, EXIF, and
  track-derived positions remain separate and resolution precedence is
  `USER_ASSIGNED > EXIF_EMBEDDED > TRACK_INTERPOLATED`. Clear the assignment and
  verify it falls back to the preserved EXIF or track position.
- **MED_19** With two eligible activities covering one photo time, verify the UI
  shows **Ambiguous**, stores all eligible correlations, and selects the same
  deterministic winner after recalculation and restart.
- **MED_20** On a disposable installation, add, replace, and delete relevant
  activity data. Verify only photos in the old or new activity time window are
  queued, persisted correlations are recalculated, and resolved map positions
  update without a full media-library scan.
- **MED_21** With at least 100,000 synthetic media rows and 300 activity rows,
  record `EXPLAIN ANALYZE` and HTTP timings. Map bounds must use the spatially
  indexed resolved-position projection; a Track Details timeline must use the
  indexed selected-correlation lookup. No request may interpolate across the
  full media library at runtime.
- **MED_22** Assign a manual location, then preview a non-zero camera offset.
  The card, viewer, and mini-map keep the **Set by you** position and clear
  action while route and capture-time preview data updates underneath it.
- **MED_23** Exercise a correction whose saved offset has non-zero seconds,
  such as `3603`. The compact label is valid (`+1h`, not `+1.h`). If position
  provenance is temporarily unavailable, the UI says **Position unknown** and
  never claims **Photo GPS**.
- **MED_24** After setting or clearing a manual location, request the same map
  bounds again without waiting. The marker updates immediately and the
  response uses `Cache-Control: no-store`.
- **MED_25** In a disposable PostGIS test, delete a correlated activity through
  the production deletion service. Its photos are requeued, the queue row
  survives deletion, and recalculation falls back to another activity, EXIF,
  or no resolved position.
- **MED_26** Seed one deterministically failing correlation item followed by a
  healthy item. The failing item is deferred with attempt/error metadata and
  the healthy item completes; it must not retry the blocked batch forever.
- **MED_27** On the main map, click a cluster containing multiple synthetic
  photos. A chooser distinguishes **This cluster**, **Current map view**, and
  **Photos along a GPS track** when a GPS activity passes the clicked location.
  Open the only activity directly. With several activities, verify the standard
  map track chooser is reused and the selected row opens its Media tab. Verify
  each destination and visible scope label. The
  cluster viewer opens at most 200 members without changing map zoom or camera;
  previous/next lazily loads the adjacent bounded page. After manually zooming
  until markers separate, click a single marker and verify the same chooser
  distinguishes **This photo** from **Current map view**. Repeat the complete
  flow on desktop and at 390 × 760.
- **MED_28** Open the matching synthetic activity's Media tab. Timeline rows
  and mini-map markers are server-paged in capture-time order: 100 items by
  default, no more than 200 when requested, and only the current page is
  rendered. Exercise first, middle, and last pages on the `MED_21` 100,000-row
  dataset and confirm page navigation remains responsive without an unbounded
  response or DOM.
- **MED_29** In the activity mini-map and viewer location map, Photo GPS,
  Estimated, Set by you, and unknown positions use the same circular camera
  symbol; only color and accessible text communicate provenance. No origin uses
  a different geometric marker shape. Clearing a manual position restores the
  previous color and label.
- **MED_30** On desktop, open a six-photo activity viewer and test the large side
  previous/next controls, keyboard arrows, swipe, direct Nearby selection,
  Nearby collapse/expand, and image zoom/pan/reset. Navigation controls must not
  zoom the image. Details can hide and return; Taken includes local date and
  time plus the GPS-time or camera-clock source. The location map stays in the
  lower-right details area, fits the full
  selected activity plus photo automatically, exposes only standard zoom
  in/out controls, and **Open on main
  map** closes the viewer at the photo location.
- **MED_31** At 390 × 760 and 375 × 667, the viewer opens photo-first with
  Details closed. Details opens as a native vertically scrollable dark sheet
  and reaches metadata, location map, main-map action, and download. Nearby
  collapses to one compact dark row retaining filename and count; the phone
  label shortens to **Nearby**. Expanded and collapsed states reach the bottom
  edge with no white safe-area or sheet-padding strip.
- **MED_32** Compare the separate viewer window controls. Panel maximize fills
  the MTL Explorer viewport; the adjacent fullscreen action enters browser
  fullscreen for the entire viewer and clears panel maximize. Details and
  Nearby retain their current states and can each be changed in either mode.
  Previous/next and zoom still work. The same fullscreen action or Escape
  returns to the normal still-open viewer; restoring panel size does not change
  Details or Nearby.
- **MED_33** Give the viewer a 100,000-ID selection centered away from either
  end. It renders only a bounded 200-thumbnail window, keeps the current
  item/count correct, supports direct nearby selection, and shifts the window
  in both directions while the DOM count stays bounded. Repeat with a paged map
  cluster and activity: reaching either strip or previous/next boundary loads
  the adjacent 200-photo page. Record DOM counts, requests and interaction
  timings; do not treat a screenshot alone as performance evidence.
- **MED_34** With the application in both light and dark themes, open map,
  activity and statistics photo viewers with no stored viewer preference. Each
  defaults to dark. Use the header shortcut to select light and verify the
  header, photo stage, navigation, Details, metadata dividers, mini-map area,
  Nearby dock, thumbnails, controls, focus states and mobile details sheet all
  adapt with readable contrast. Reload and open each viewer entry point; light
  persists only for photo viewers while the application theme remains
  unchanged. Switch back to dark and repeat maximize, browser fullscreen,
  Details, Nearby, zoom and previous/next interactions.
- **MED_35** Open an activity Media tab on desktop and at 390 × 760. The
  timeline, position origins, paging and selection are visible immediately;
  Camera clock, Adjust locations, and per-photo edit actions are not. Open the
  single **Photo tools** disclosure, verify the camera preview is usable, then
  enable **Adjust locations** and verify edit actions appear. Closing Photo
  tools also closes an open editor and leaves the timeline uncluttered. Repeat
  first/next/last paging on the 100,000-photo fixture and verify no page renders
  more than the selected limit.

### Video Playback and Location Flow

Use both generated `DAT_08` videos. The MP4 is the mandatory native-playback
case. The MOV verifies a second container and a camera-time-only location path.

- **MED_36** Verify both generated videos finish MEDIA indexing and appear on
  the map, in the matching activity timeline, in media navigation, and in the
  photo/video statistics counts. Every thumbnail surface returns a JPEG poster
  with a visible video/play indicator; no surface shows a broken image.
- **MED_37** Open `mtl-regression-video-gps.mp4` in the viewer. Native playback
  is required: metadata loads, duration is positive, dimensions are non-zero,
  play advances current time, and pause, seek, resume, keyboard/swipe
  navigation, poster, Details, Nearby, and original download work. Verify the
  full response is `200`, a byte-range response is `206` with correct
  `Content-Range` and length, an unsatisfiable range is `416`, and the downloaded
  original matches the manifest checksum.
- **MED_38** Open `mtl-regression-video-estimated.mov` and record
  `canPlayType('video/quicktime; codecs="avc1.42E01E, mp4a.40.2"')`. When the
  original plays, require the same play, pause, seek, and resume checks as
  `MED_37`, even if that support is broader than expected. When actual decoding
  reports an unsupported source or decode error, require a clear explanation,
  an Auto/480p/720p/1080p quality selector, and a user-started compatible stream.
  Preparing, progress, slow-conversion guidance, cancellation, failure, and
  retry states remain usable while the poster and checksum-matching original
  download stay available. The compatible HLS stream must start, play through
  its full duration, and retain pause/resume behavior. A blank viewer or broken
  poster fails either path. Verify `200`, `206`, and `416` serving behavior for
  the original MOV as well.
- **MED_39** Verify video location provenance end to end. The MP4 retains its
  embedded coordinates and resolves as `EXIF_EMBEDDED` even when the matching
  activity could supply an interpolated point. The MOV has no original
  coordinates, resolves onto the matching GPX route as `TRACK_INTERPOLATED`, and
  is labeled Estimated. Set a manual MOV location and note, verify
  `USER_ASSIGNED` wins without overwriting the track-derived point, then clear
  it and verify the persisted track-interpolated location returns. Compare UI
  values with the manifest and read-only database rows.
- **MED_40** Reload the app and restart the server. Reopen both videos from the
  main map and activity timeline. Poster, navigation, native or compatible
  playback, locations, origins, range requests, and downloads remain stable,
  with no failed MEDIA rows, blank viewer, or uncaught console error.
- **MED_41** Generate a short disposable archival-codec/PCM MOV with ffmpeg in
  the test run's temporary fixture directory; do not commit the video. Follow
  `video-transcoding/README.md` if the test browser plays the archival codec
  natively. Exercise the full compatible flow at 480p and 720p. Reopen during
  conversion and after a page reload: the same media revision and quality reuses
  the active or retained session instead of starting a second ffmpeg process,
  and switching quality starts only the newly selected bounded profile.
- **MED_42** With short test-only limits, verify one active conversion, one
  retained completed result, global and per-session byte caps, maximum runtime,
  disconnect grace, completed expiry, oldest-completed eviction, and startup
  orphan cleanup. Active output is never evicted. Crossing a hard limit stops
  ffmpeg, removes the session directory, and gives the viewer a recoverable
  message. Record the temporary directory size and process/session counts before,
  during, and after cleanup.

### Delete-Two-Photo Flow

MTL Explorer currently has no media-delete control. Remove only the disposable
source fixtures from the watched folder; perform the rescan, status, freshness,
and map verification through the GUI.

- **MED_07** Before deletion, record the filenames, media IDs, checksums,
  coordinates, visible pins, MEDIA indexer status, and freshness token for
  `mtl-regression-delete-a.jpg` and `mtl-regression-delete-b.jpg`. Capture a
  compact map screenshot showing the disposable photo pins.
- **MED_08** Remove those two source files from the watched media folder. Only
  manipulate the disposable `DAT_08` fixtures. Keep exact backups outside the
  watched tree until the flow is complete.
- **MED_09** In Admin, trigger **Rescan Media** and wait for MEDIA indexing to
  settle. The removed count increases by two, no new failure appears, and a
  media freshness change is available. Reload through the freshness action.
- **MED_10** Return to the recorded viewport. The two deleted photo pins are
  absent, cannot be opened through map navigation, and the two remaining
  synthetic photos still display and navigate correctly.
- **MED_11** Pan away and back, zoom across cluster levels, hard-reload the app,
  and revisit the same viewport immediately. The deleted pins do not reappear,
  the remaining pins are not duplicated, and the bounds response is not cached.
- **MED_12** On the disposable installation, use read-only database inspection
  to verify that each deleted source retains an `indexed_file` row with status
  `REMOVED`, has a `DELETE` snapshot in `media_file_audit`, and has no active
  `media_file` row. Record only synthetic fixture data in the evidence.

- **MED_43** Open a public or synthetic HEIC/HEIF image and a resized JPEG preview, including a format requiring fallback where available. Verify decodable output, orientation, dimensions, viewer recovery, and original download. Capture libvips/ImageMagick fallback logs or response headers when exercised; use the [media fixture and verification guide](../../../../docker/media/README.md).
- **MED_44** Request several uncached image previews while the media process limit is occupied. Busy responses remain recoverable; retry eventually displays the image without an endless broken-preview state. Use disposable media and record the controlled contention setup.

## 10. Heatmap And Overlays

- **HMO_01** Toggle the heatmap → it draws over the map without hiding the tracks; respects opacity.
- **HMO_02** Toggle each map overlay (Swiss / OSM / etc.) independently; opacity sliders work; ordering above/below tracks stays correct.
- **HMO_03** After changing filters, the heatmap updates accordingly.

## 11. GPS Location

- **GPS_01** Browser geolocation requires a secure origin. In quick-install runs opened
  from a remote plain-HTTP host, mark live GPS permission/marker checks
  `NOT APPLICABLE - expected browser limitation`; test those rows on
  `localhost` or HTTPS.
- **GPS_02** Enable GPS → permission prompt; on accept, the locate marker appears.
- **GPS_03** "Follow me" mode keeps the map centered until you pan away (drifted state).
- **GPS_04** Permission denied / disabled state shows a clear message.
- **GPS_05** Disabling GPS removes the marker and stops updates.

## 12. Location Search

- **SRC_01** Open the search → type a place name → results appear.
- **SRC_02** Pick a result → map flies to it and a marker is placed.
- **SRC_03** Clear search / pick a different tool → marker is removed cleanly.
- **SRC_04** Empty / no-result queries show a clear message.

## 13. Globe Mode

- **GLB_01** Zoom out far enough → globe view engages automatically.
- **GLB_02** Zoom in → map returns to flat view.
- **GLB_03** Manual disable of globe is respected (does not auto-re-enable until you re-enable it).
- **GLB_04** Zoom limits don't trap the map at edges.

## 14. Settings And Operations

- **ADM_01** Open Settings; the overview and grouped section navigation are reachable and usable on desktop and mobile.
- **ADM_02** **Track file upload**: drag or pick GPX/FIT/etc.; upload availability, accepted formats, progress, success, unsupported-format errors, and empty-file errors are clear.
- **ADM_03** **Indexer status**: shows GPS and media pending/running/completed/failed/removed state; refresh updates over time.
- **ADM_04** **Manual rescan**: **Rescan GPS** and **Rescan Media** show queued/already-running/not-ready states without breaking map interaction.
- **ADM_05** **Background jobs**: Duplicate Finder and Exploration Score progress is visible and settles after imports.
- **ADM_06** **Operational tasks**: vector map tiles, location search, and routing segment status show ready/downloading/unavailable/disabled states with useful detail.
- **ADM_07** **Data freshness**: shows last-update timestamp and offers reload.
- **ADM_08** **Server log**: log lines load and refresh.
- **ADM_09** **About and credits**: the public About page shows the expected map, library, and data sources before and after login; Back returns to the previous Settings section or the login/map fallback.
- **ADM_10** **Garmin export tools** (if present): status of installed exporters; install/update actions report success or error.
- **ADM_11** Closing/reopening the dialog doesn't lose state mid-action.
- **ADM_12** Direct Settings section URLs, browser Back/Forward, mobile Back, and closing the sheet keep the section and map routes synchronized.

- **ADM_13** Settings opens with Preferences in the first group and compact expandable system status. Status links open the correct detail section. Desktop navigation remains beside content; phone Back stays outside scrolling details. Section headings receive keyboard focus.
- **ADM_14** Import & sync shows compact Garmin and Google Health entries. Google Health Manage/setup and Processing links keep their own return path in Settings. The server-log error state offers retry; long log lines and service URLs do not force page overflow.
- **ADM_15** In a disposable browser context, compare credentials-only sign-out with full local-data cleanup from Session. Confirmation explains the scope and Cancel preserves the session. Confirmed cleanup removes the selected local state without deleting server tracks or saved plans. Never use this check to delete the owner's Health mirror.

## 15. Data Updates And Sync

- **SYN_01** After server-side data changes (new import, re-index, or restart), a data-freshness banner appears.
- **SYN_02** Reloading from the banner refreshes cached tracks and stats.
- **SYN_03** The required five-GPX import and delete-two-track flow passes: indexer state,
  freshness banner, map, browser, stats, filters, heatmap, and details all
  reflect the new source-of-truth files.
- **SYN_04** FIT conversion import changes freshness and cache state the same way a native GPX import does.
- **SYN_05** Map data warnings start compact. Expanding shows details and actions.
  Minimize collapses the details while keeping the warning chip visible; reopening
  retains Reload map and Data status or Processing details. Verify desktop and
  phone, including a status update while minimized.
- **SYN_06** Logging out and back in does not re-trigger an automatic data refresh repeatedly.
- **SYN_07** Indexer-running state surfaces as a badge but doesn't block map interaction.

- **SYN_08** On the disposable server, stop the app, add/replace/delete synthetic GPS and media source files, then restart. Startup catch-up updates source states and dependent results without a manual rescan. Accept reload and verify map, browser, media, and statistics. Record each change and restore the baseline; retain fixtures needed by later checks.
- **SYN_09** Open Settings Processing while indexers and other status consumers are active. Shared status polling remains bounded rather than multiplying per panel; closing consumers stops unnecessary polling. A failed status section exposes its error while available sections and map interaction continue.

## 16. Appearance (Theme And Map Style)

- **APP_01** Switch between **light** and **dark** mode → the whole UI re-themes immediately (text, panels, dialogs, sheets, dropdowns, tooltips, charts).
- **APP_02** No text is unreadable (white-on-white or black-on-black) in either theme.
- **APP_03** Charts re-color on theme switch without needing a reload.
- **APP_04** Selected theme persists across reload and login.
- **APP_05** Hard refresh in dark mode does not flash the light theme first.
- **APP_06** Map theme is independent: each of the available map styles (light, dark, grayscale, light-topo, topo-contrast, swisstopo, swisstopo-color) can be selected with either UI theme.
- **APP_07** Selected map style persists across reload.
- **APP_08** Layer opacity sliders, basemap dimming, and reset-to-defaults all behave and persist.
- **APP_09** Open the User Guide and a topic link in each app theme. Navigation,
  search, empty results, tables, and code blocks follow the saved preference on
  desktop and phone. Reload retains it; an open guide updates when the app theme
  changes in another tab. Printing uses the light palette.
  From the navigation, About, Filter, and Planner, browse several guide pages,
  then use **Back to app**. The guide tab closes and the original app keeps its
  map position, filters, open tool, and unsaved planner draft without reloading.
  A guide opened directly still offers a working app link.

## 17. Locale, Units, And Formatting

- **LOC_01** Numbers, distances, durations, and dates render in the expected locale format.
- **LOC_02** Changing locale (if available) updates formatting across the app without reload artifacts.
- **LOC_03** Locale persists across reload.
- **LOC_04** Boundary values (zero, very large, negative gain, null elevation) render sensibly, not as "NaN" or blank.
- **LOC_05** **Metric / imperial measurement flow**: in **Settings → Preferences → Region & units**, select Metric and record representative values from Statistics, Track Browser, Track Details and graphs, a map point popup, filters with numeric distance/elevation parameters, measuring or segment comparison, Planner, and replay/animation.
  Switch to **Imperial (US)** without changing the underlying track, route, or segment. Distance, elevation, speed, vertical rate, and weight values must convert consistently (`km`/`m`/`km/h`/`m/h`/`kg` → `mi`/`ft`/`mph`/`ft/h`/`lb`), while duration, dates, watts, and watt-hours remain semantically unchanged. No checked view may mix metric and imperial units.
  Reload the app and confirm the imperial preference and converted values persist, then use **Use default** or select Metric and confirm metric values return after another reload. Capture compact paired evidence from Preferences, Statistics, and at least one detailed metric surface.

## 18. Responsive / Mobile / Touch

- **MOB_01** Test at a narrow mobile width and with touch input enabled.
- **MOB_02** Bottom sheets and the navigation sheet drag, snap, and close correctly.
- **MOB_03** Tables, charts, and map controls stay usable; no text overflows.
- **MOB_04** Planner waypoints can be tapped, dragged, and inserted with touch.
- **MOB_05** Map gestures (pinch, double-tap, drag) work after using each tool.
- **MOB_06** `NOT APPLICABLE` in the current UI: the Filters/Settings tab switch was replaced by focused screens. Keep this ID for historical reports; use `FLT_22-FLT_27` for the replacement flow.

- **MOB_07** The phone toolbar exposes GPS, Map, Filter, Planner, and More. More opens Statistics, animation, Segments, Health, and Settings. Desktop exposes its direct tools with Settings separated at the bottom. Switching tools and returning closes obsolete sheets and retains the correct route.
- **MOB_08** Open short and long Settings, Filter, Statistics, and Planner sheets at narrow phone, tablet, short desktop, and wide desktop sizes. Small height growth may remove unnecessary scrolling with one opening animation; long content remains scrollable. Manual sizing takes priority until reopening, and focus/touch targets remain usable.

## 19. Offline And Network Issues

- **NET_01** Installed PWA / installed web-app mode only: after installing MTL Explorer in
  the browser and loading once online, reload while offline → cached tracks and
  tiles still display. A normal browser tab is not expected to pass this offline
  reload check; mark that row `NOT APPLICABLE` or `NOT COVERED` unless the app
  is installed as a web app.
- **NET_02** A flaky connection shows recoverable error states, not a blank screen.
- **NET_03** A 401 response on a protected route redirects to login. A 403 response reports the failed request while preserving the valid session; normal responses recover without signing in again.
- **NET_04** Service worker update: a "new version available" prompt appears after an update; accepting it reloads cleanly.
- **NET_05** Cached basemap with no network in an ordinary browser tab: sign in
  online with the automatic/local vector source and wait for the low-zoom map
  archive and tracks to be saved. Block all backend and external network
  requests, then pan/zoom within the saved archive's zoom range into an area not
  previously viewed; open Statistics and return to Map without reloading the
  document. Cached geography and tracks still render, with a clear cached/offline
  state and usable map controls. Unavailable fonts, sprites, overlays, or terrain
  must not leave the basemap blank or startup waiting indefinitely. Restore
  connectivity: online maps recover without clearing storage or signing in again,
  and the selected filter and map-source preference remain intact. Capture cache
  preparation, blocked requests, and before/offline/reconnected screenshots on
  desktop and a narrow viewport. Full offline document reload remains `NET_01`.

## 20. Error Recovery

- **ERR_01** Trigger or simulate: failed track load, failed map config, failed media, failed planner route, expired session. Each shows an actionable message (retry, re-login, dismiss) rather than freezing or going blank.
- **ERR_02** Rapid switching between tools does not leave the previous tool's markers, listeners, or cursors behind.

## 21. UX Performance

- **UXP_01** Start from a signed-in, warmed desktop session with the required
  imported data present and background jobs settled. Capture browser main-thread
  responsiveness and every completed first-party application API `fetch`/XHR
  while running three consecutive journeys through map pan/zoom, Filter
  apply/reset, Statistics Overview and Tracks, track search and details tabs,
  Planner open/close, and return to the map. Each input must show visible feedback
  within 200 ms, no measured main-thread stall may exceed 500 ms, every first-party
  application API response must finish within 2 seconds with its expected status
  and UI result, and no request may remain unexpectedly pending. Exclude static
  assets, map tiles/providers, uploads, downloads, and intentionally cancelled
  requests from the API budget. After the third journey, map pan/zoom and the main
  navigation must still respond. Save a timing table with action feedback, maximum
  stall, API route/status/duration, pending or failed requests, and any console
  error; a screenshot alone is not enough evidence. Any exceeded budget,
  unexpected response/result, error, or loss of interaction fails this coverage
  item.

## 22. Google Health

### Scope and prerequisites

| Environment | Coverage |
| --- | --- |
| Fresh disposable server | `GHL_01-GHL_02`, `GHL_04-GHL_06`: no personal account. Connected checks are `NOT APPLICABLE`, with reason “requires the owner's local Google Health account.” |
| Existing local installation | `GHL_07-GHL_15`, `TRD_18`, and `MCT_13`: a separate connected pass using the owner's existing account and available data. No account setup or data transfer to the fresh server. |
| Account lifecycle changes | `GHL_03`: retain this historical lifecycle ID, but connect/reauthorize, change consent, disconnect, or delete only with separate explicit authorization for the action. Do not perform these during routine local regression. |

A fresh-server regression can pass without connected Health coverage. State
that scope in the report; it does not certify synchronization or real Health
records. On local runs, record missing data or a required account action as
`BLOCKED` with the prerequisite. Do not change or seed the owner's mirror to
manufacture boundary cases. Existing synthetic unit/integration tests can
support those cases, but do not count as live account regression evidence.
Capture redacted UI/status evidence only; no tokens, account identifiers,
private record payloads, routes, or health values in committed assets.

### Fresh-server checks

- **GHL_01** With `MTL_GOOGLE_HEALTH_ENABLED=false`, MTL Explorer starts and the normal sign-in, map, filter, browser, statistics, and Settings smoke flow works. Google Health shows a disabled state; connection/sync actions fail without a 5xx response, and no Google jobs, remote requests, route files, or mirror rows are created. Local-mirror cleanup is a separate guarded operation, not a sync action.
- **GHL_02** With Google Health enabled but no client credentials, the same smoke flow works. The UI shows setup is required, connection and sync remain unavailable, and no remote sync starts. Restore the original disposable app configuration afterward.
- **GHL_03** Local account lifecycle check only, with separate explicit authorization: connect/reauthorize, inspect partial consent, sync, browse Health and exercise scope, disconnect, and delete the local mirror as applicable to the authorized action. Progress separates the current step from cumulative records and older-history coverage. Disconnect retains the mirror; authorized deletion requires its confirmation and must preserve ordinary imports and saved plans. On fresh servers mark `NOT APPLICABLE`; routine local passes exclude lifecycle mutations. Record the exact authorized scope and any unexecuted parts rather than claiming the full lifecycle passed.
- **GHL_04** On an unconfigured fresh server, open Settings → Import & sync → Google Health → Start setup. The assistant explains required account/project setup, read-only access, Desktop OAuth configuration, and manual return-address paste. Copyable environment/Compose examples use placeholders. Back and Close work; stop before account authorization.
- **GHL_05** With demo mode enabled and synthetic health disabled, Health shows its non-interactive preview and server Health data access is blocked. With `mtl.demo-health.enabled=true`, Overview, Data & Records, and matching track heart rate show labelled synthetic data through the ordinary APIs; generation progress is visible in Settings → Processing. In both cases account connection and Google synchronization remain blocked, and no Google remote requests run. Restore the original app configuration afterward.
- **GHL_06** Without a local mirror, open Health Overview and Data & Records. Overview keeps empty chart periods and setup links usable without requesting status or catalogue counts. Data & Records shows disabled/unconfigured/empty states and offers retry for failed local status requests. No endless spinner or effect on ordinary map/statistics use.

### Local connected pass

- **GHL_07** With the existing connected account, open Health → Overview and Data & Records, then Back. Navigation and focus return to the hub. Manage and sync notices open the appropriate Settings section and preserve a useful return path on desktop and phone.
- **GHL_08** Use date presets, including Today, then custom ranges. Presets apply immediately; custom Cancel preserves the previous range and Apply uses the selected inclusive calendar dates. Test desktop and single-calendar phone layouts; invalid/over-limit ranges explain the correction.
- Opening Overview requests charts only, including after closing and reopening it. It does not request or poll full Google Health status, type summaries, sources, or raw records. Expanding Latest values requests summaries and then its measurements; closing during loading prevents follow-up reads. Reopening a successfully loaded section reuses its values until a shared sync update or screen closure. Sync updates leave collapsed latest values unloaded.
- **GHL_09** Switch All, Activity, Heart & vitals, Sleep, and Body with available local records. Auto/Day/Week/Month/Year grouping follows calendar boundaries, Monday-start weeks, and the displayed time zone. Intraday uses one local day and the same grouped API; select a day, move to adjacent days, and open a day from a daily measurement chart with See day in detail. Use the 6 h/1 h/15 min/1 min presets and the earlier/later arrows, set the exact range 12:00–12:15 and Apply, and drag or pinch on a graph; every graph shows the same window without another request, and expanding and returning keeps it. Select a point and verify the marked time on every graph, each graph's nearest reading, presets centered on that time, exact timestamps, averages, observed ranges, line breaks only at long gaps, and the empty-window note. Full day restores the whole day. Daily-only values stay unavailable at Intraday. Returning to calendar grouping restores the previous range. Latest values remain independent of the Trends range. A period with no records is empty, not a fabricated zero measurement.
- **GHL_10** Switch summary/daily variation and observed-range choices where available; reload and verify each chart's preference. Hover, pin, keyboard-select, and touch-select a period. Coverage, dated extrema, clipped periods, and in-progress labels agree with the returned server data. Expanded charts preserve grouping and selection; Back/Escape restores focus and scroll.
- **GHL_11** Where existing local data supplies overnight sleep, naps, or daylight-saving boundaries, confirm each whole session belongs to its start date in the viewing time zone. Daily stacks and longer-period daily averages use the server's session duration. Record unavailable cases explicitly; do not insert synthetic health records into this account.
- **GHL_12** In Data & Records, apply type, representation, source, and date filters, load another page, open Details, reset, and retry a failed request if one occurs. Rows match the selected filters without duplicates. Date-only records retain their calendar date. Test responsive cards/table and unknown/null values when present in local data.
- **GHL_13** Observe an ordinary background sync. Current pass/window progress stays distinct from cumulative mirror counts; the verified snapshot age remains visible during work and counts refresh after the cycle stops. Pending history and retry ranges link to Processing. Do not force a reconnect, full-history sync, failure, or cancellation on the owner's account merely to satisfy this row.
- **GHL_14** When an existing exercise has an imported route, open the associated track and exercise-only scope. The matching route is shown, ordinary statistics stay consistent, and normal repeated background sync does not duplicate it. Record missing eligible routes as a local data prerequisite.
- **GHL_15** Switch metric/US customary units and light/dark themes across Health, track HR, and segment HR/cardiac cost. Distances, weight, and speed convert where applicable; bpm, watts, and beats/kJ retain their meaning. Coverage/unavailable labels and charts stay readable at phone width. Restore the owner's original preferences.

### Synthetic synchronization acceptance

Use an isolated database and synthetic provider responses for these checks.
Do not reload, reset, or mutate the owner's connected mirror.

- Fast work runs first, full work advances across bounded cycles and restart,
  and busy ticks create no replay. Show incomplete fast coverage under overload.
- Named corrections and timestamp moves retain one current identity; differing
  nameless observations survive. Invalid/conflicting pages cannot replace records.
- Complete children sweep only their own bounds/stamps, including NULL stamps;
  empty days finalize, revoked owners fail, and rollback leaves no final mark.
- Empty batches write daily audits, stop at gaps/caps, and shrink when data appears.
  A 1970 floor with parked gaps remains responsive through cached status.
- Range/all reload supersedes matching finals without pre-deletion. Unrequested
  parked portions remain parked. Retry releases failures; reset clears every new state.
- Exercise removal retains files, owners, and associations. Reappearance and track
  replacement reuse ownership. Adding location permission exports stored routes.
- Persist Retry-After cooldown; count all HTTP attempts, probes, retries, and decoded
  response bytes. Record real-account envelope, density, delay, and cost measurements
  separately before making production performance claims.

## Suggested Regression Passes

| Pass | Scope |
|---|---|
| Smoke (~10 min) | Sign-in, map loads, apply filter, open a track, open stats, open planner, sign out. |
| Full desktop | All applicable coverage IDs above on a desktop browser with a normal network. |
| Full mobile / touch | Applicable mobile checks for `SGN`, `MAP`, `TRD`, `FLT`, `TBS`, `PLN`, `MCT`, `AVR`, `MED`, `ADM`, `GPS`, `GHL`, and `MOB`; connected Health remains local-only. |
| Offline / cache | `NET_01-NET_05`; offline reload (`NET_01`) and service-worker update (`NET_04`) require installed web-app mode. Cached basemap outage/recovery (`NET_05`), network recovery, and authentication checks also apply to ordinary tabs. |
| Data-change | `DAT`, `IMP`, `FIT`, `DEL`, `MED_06-MED_44`, and `SYN`: import, conversion, track and media deletion flows, persisted media correlation, video playback, compatible streaming, bounded temporary storage, freshness, cache refresh, map, browser, statistics, responsive viewer, large-library paging, and media audit. |
| Theme | `APP_01-APP_08`: toggle dark/light and each map style; verify UI, charts, map, and persistence. |
| Locale and units | `LOC_01-LOC_05`: verify locale formatting and persistence, then run the metric/imperial conversion and persistence flow. |
| Remote raster maps | `MAP_13-MAP_15`: intentional remote tile mode, per-theme provider URLs and attribution, local-vector fallback when tiles are unavailable, and the manual Remote map source override. |
| UX performance | `UXP_01`: repeat the core desktop journey with browser responsiveness and first-party API timing capture. |
| Navigation and editing | `MAP_16`, `FLT_22-FLT_27`, `TBS_17-TBS_18`, `PLN_12-PLN_17`, `ADM_13-ADM_15`, `MOB_07-MOB_08`. |
| Segment and motion | `DAT_09`, `TRD_16-TRD_17`, `TRD_19`, `MCT_07-MCT_12`, `MCT_14-MCT_15`, `AVR_05-AVR_06`; synthetic/public fixtures on the fresh server. |
| Google Health fresh-server | `GHL_01-GHL_02`, `GHL_04-GHL_06`; no personal account. Mark connected rows `NOT APPLICABLE` with the local-account reason. |
| Google Health local connected | `GHL_07-GHL_15`, `TRD_18`, `MCT_13`; existing local account, available records, redacted evidence. `GHL_03` account mutations require separate explicit authorization. |
