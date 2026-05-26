# Regression Test Plan

Manual checklist for validating MTL Explorer end-to-end. Written for a tester clicking through the app; no code knowledge required.

For each item, perform the action and confirm the expected result. Note any deviation, including blank screens, error toasts, slow responses, or visually broken layouts.

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
| `MED` | Media photos |
| `HMO` | Heatmap and overlays |
| `GPS` | GPS location |
| `SRC` | Location search |
| `GLB` | Globe mode |
| `ADM` | Admin tools |
| `SYN` | Data updates and sync |
| `APP` | Appearance |
| `LOC` | Locale, units, and formatting |
| `MOB` | Responsive mobile and touch |
| `NET` | Offline and network issues |
| `ERR` | Error recovery |

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

### Five-GPX Import, Index, Map, And Stats Flow

- **IMP_01** Capture baseline map count, track-browser count, statistics totals, data-freshness token, and GPS indexer status.
- **IMP_02** Import the five GPX files through the Admin upload UI or by placing them in the documented watched import folder.
- **IMP_03** Wait for indexing to finish. If live file watching does not react, trigger **Rescan GPS** from Admin and record that it was needed.
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

- **FMT_01** The server accepts `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.sbp`, `.nmea`, `.geojson`, and `.gdb`. For a full release regression, test at least one GPS-bearing sample for each available format, or mark that format `NOT COVERED` with the reason.
- **FMT_02** For each non-GPX format tested, verify upload acceptance, GPSBabel conversion, map display, details/charts, statistics inclusion, **Download original source file**, and **Download as GPX**.

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

## 2. Map And Tracks

- **MAP_01** Base map and overlays load on first open.
- **MAP_02** All your tracks appear on the map; the total/visible count is correct.
- **MAP_03** Newly imported tracks from the required data-change flow appear without a full browser restart after accepting the freshness/reload prompt.
- **MAP_04** Deleted tracks from the required data-change flow disappear from all map sources, selection lists, and popups.
- **MAP_05** Zoom in on a track → detail/precision improves (no duplicate or broken lines).
- **MAP_06** Fast pan/zoom doesn't leave stale lines, missing tiles, or runaway loading spinners.
- **MAP_07** Direction arrows appear on tracks at high zoom (if enabled in settings).
- **MAP_08** Click a single track → it highlights and details open.
- **MAP_09** Click an area where several tracks overlap → a selection list appears; picking one opens its details.
- **MAP_10** Deselect / close the selection → the map returns to its normal state.
- **MAP_11** Clicking a point on a track shows a popup with the expected metrics (time, speed, elevation, etc.).
- **MAP_12** Swiss Mobility routes popup (where applicable) shows nearby official routes and closes cleanly.

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

## 4. Filters

- **FLT_01** Open the filter panel → previously saved filter is still active and shown as a chip.
- **FLT_02** Browse the filter catalog; search and grouping work.
- **FLT_03** Pick a filter → its parameters appear; apply, reset, and cancel all behave correctly.
- **FLT_04** Date, text, and geo parameters all save and re-apply correctly after reload.
- **FLT_05** **Geo drawing**: draw a circle, rectangle, and polygon; undo, cancel, finish, and clear all work; saved shapes reappear next time.
- **FLT_06** Applied filter updates: visible track count, map colors, legend, and stats — without a full page reload.
- **FLT_07** Legend reflects the active filter (categories or gradient); collapsing/hiding groups updates the map immediately.
- **FLT_08** Clearing the filter restores all tracks.

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

## 6. Planner

- **PLN_01** Open the planner; pick a routing profile (e.g. hike, bike).
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

## 7. Measuring And Comparison Tools

- **MCT_01** Start the measure tool, pick start and end points → result list of crossing tracks appears with speed/time/distance.
- **MCT_02** Clicking a result opens that track's details / segment view.
- **MCT_03** Stop the measure tool → all temporary markers and listeners are cleaned up.
- **MCT_04** Segment comparison: pick several tracks → comparison chart + map align them correctly even with missing data.
- **MCT_05** Sub-track / segment extraction (between two points on a track) returns the expected slice.

## 8. Animation And Virtual Race

- **AVR_01** Start animation: tracks play back smoothly; pause, reset, and speed controls work.
- **AVR_02** Virtual race: multiple racers move together; ranking and racer cards update in real time.
- **AVR_03** Stopping or finishing animation/race leaves map gestures and tools usable (no stuck state).

## 9. Media (Photos)

- **MED_01** Toggle the media layer → photo pins appear in the map view.
- **MED_02** Pan/zoom → media is loaded for the current viewport (not the whole world at once).
- **MED_03** Click a pin → photo preview opens; next/previous navigation works.
- **MED_04** HEIC photos display correctly (converted server-side).
- **MED_05** A missing/broken photo shows a recoverable error, not a blank sheet.

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

## 14. Admin Tools

- **ADM_01** Open the admin dialog; the tab list is reachable and usable.
- **ADM_02** **Track file upload**: drag or pick GPX/FIT/etc.; upload availability, accepted formats, progress, success, unsupported-format errors, and empty-file errors are clear.
- **ADM_03** **Indexer status**: shows GPS and media pending/running/completed/failed/removed state; refresh updates over time.
- **ADM_04** **Manual rescan**: **Rescan GPS** and **Rescan Media** show queued/already-running/not-ready states without breaking map interaction.
- **ADM_05** **Background jobs**: Duplicate Finder and Exploration Score progress is visible and settles after imports.
- **ADM_06** **Operational tasks**: vector map tiles, location search, and routing segment status show ready/downloading/unavailable/disabled states with useful detail.
- **ADM_07** **Data freshness**: shows last-update timestamp and offers reload.
- **ADM_08** **Server log**: log lines load and refresh.
- **ADM_09** **Attribution**: shows expected map/data sources.
- **ADM_10** **Garmin export tools** (if present): status of installed exporters; install/update actions report success or error.
- **ADM_11** Closing/reopening the dialog doesn't lose state mid-action.

## 15. Data Updates And Sync

- **SYN_01** After server-side data changes (new import, re-index, or restart), a data-freshness banner appears.
- **SYN_02** Reloading from the banner refreshes cached tracks and stats.
- **SYN_03** The required five-GPX import and delete-two-track flow passes: indexer state,
  freshness banner, map, browser, stats, filters, heatmap, and details all
  reflect the new source-of-truth files.
- **SYN_04** FIT conversion import changes freshness and cache state the same way a native GPX import does.
- **SYN_05** Dismissing the banner doesn't loop or re-show immediately.
- **SYN_06** Logging out and back in does not re-trigger an automatic data refresh repeatedly.
- **SYN_07** Indexer-running state surfaces as a badge but doesn't block map interaction.

## 16. Appearance (Theme And Map Style)

- **APP_01** Switch between **light** and **dark** mode → the whole UI re-themes immediately (text, panels, dialogs, sheets, dropdowns, tooltips, charts).
- **APP_02** No text is unreadable (white-on-white or black-on-black) in either theme.
- **APP_03** Charts re-color on theme switch without needing a reload.
- **APP_04** Selected theme persists across reload and login.
- **APP_05** Hard refresh in dark mode does not flash the light theme first.
- **APP_06** Map theme is independent: each of the available map styles (light, dark, grayscale, light-topo, swisstopo, swisstopo-color) can be selected with either UI theme.
- **APP_07** Selected map style persists across reload.
- **APP_08** Layer opacity sliders, basemap dimming, and reset-to-defaults all behave and persist.

## 17. Locale, Units, And Formatting

- **LOC_01** Numbers, distances, durations, and dates render in the expected locale format.
- **LOC_02** Changing locale (if available) updates formatting across the app without reload artifacts.
- **LOC_03** Locale persists across reload.
- **LOC_04** Boundary values (zero, very large, negative gain, null elevation) render sensibly, not as "NaN" or blank.

## 18. Responsive / Mobile / Touch

- **MOB_01** Test at a narrow mobile width and with touch input enabled.
- **MOB_02** Bottom sheets and the navigation sheet drag, snap, and close correctly.
- **MOB_03** Tables, charts, and map controls stay usable; no text overflows.
- **MOB_04** Planner waypoints can be tapped, dragged, and inserted with touch.
- **MOB_05** Map gestures (pinch, double-tap, drag) work after using each tool.

## 19. Offline And Network Issues

- **NET_01** Installed PWA / installed web-app mode only: after installing MTL Explorer in
  the browser and loading once online, reload while offline → cached tracks and
  tiles still display. A normal browser tab is not expected to pass this offline
  reload check; mark that row `NOT APPLICABLE` or `NOT COVERED` unless the app
  is installed as a web app.
- **NET_02** A flaky connection shows recoverable error states, not a blank screen.
- **NET_03** 401 / 403 from the server redirects to login.
- **NET_04** Service worker update: a "new version available" prompt appears after an update; accepting it reloads cleanly.

## 20. Error Recovery

- **ERR_01** Trigger or simulate: failed track load, failed map config, failed media, failed planner route, expired session. Each shows an actionable message (retry, re-login, dismiss) rather than freezing or going blank.
- **ERR_02** Rapid switching between tools does not leave the previous tool's markers, listeners, or cursors behind.

## Suggested Regression Passes

| Pass | Scope |
|---|---|
| Smoke (~10 min) | Sign-in, map loads, apply filter, open a track, open stats, open planner, sign out. |
| Full desktop | All applicable coverage IDs above on a desktop browser with a normal network. |
| Full mobile / touch | Mobile checks for `SGN`, `MAP`, `TRD`, `FLT`, `TBS`, `PLN`, `MED`, `GPS`, and `MOB`. |
| Offline / cache | `NET_01-NET_04`: installed PWA / installed web-app mode only. Normal browser-tab runs should mark offline reload `NOT APPLICABLE` or `NOT COVERED` unless the app is installed. |
| Data-change | `DAT`, `IMP`, `FIT`, `DEL`, and `SYN`: import, FIT conversion, delete-two-track flow, freshness, cache refresh, map, browser, stats, filters, heatmap, and details. |
| Theme | `APP_01-APP_08`: toggle dark/light and each map style; verify UI, charts, map, and persistence. |
