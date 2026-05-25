# Regression Test Plan

Manual checklist for validating MTL Explorer end-to-end. Written for a tester clicking through the app; no code knowledge required.

For each item, perform the action and confirm the expected result. Note any deviation, including blank screens, error toasts, slow responses, or visually broken layouts.

## Coverage Accounting

- Treat every checklist bullet as a required coverage item unless it is
  explicitly not applicable to the run.
- Do not mark a section `PASS` when any bullet inside it was skipped, only
  spot-checked, or verified indirectly. Mark the section `PARTIAL`, `NOT
  COVERED`, `NOT APPLICABLE`, `BLOCKED`, or `FAIL` as appropriate.
- In full-regression reports, include enough coverage detail to show which
  bullets were actually exercised and which were not. Broad section summaries
  are acceptable only when all child bullets have supporting evidence.
- Capture compact screenshots for representative working user-facing functions
  as well as failures, so reports provide a visual overview and not only defect
  evidence.
- If time, tooling, viewport, data, permissions, or environment constraints
  prevent a check, record that constraint explicitly instead of silently
  collapsing the check into a parent row.

> Developer baseline (handled before handover): `npm run test`, `npm run type-check`, `npm run build`, and `npm run lint` are green or pre-existing failures are documented. API types come from the generated OpenAPI client.

## Required Data-Change Regression

Run this in every full regression and every release-candidate data-change pass.

### Public Test Data

- Use at least **five public internet GPX files** with real track sequences (`trk` / `trkseg` / `trkpt`). Waypoint-only files (`wpt` without `trkpt`) are not valid positive import evidence.
- Prefer GPX files with timestamped trackpoints so duration, speed, moving time, and period statistics can be verified.
- Record for every source file: source URL, source page/license note, destination filename, SHA-256, byte size, `trkpt` count, timestamp count, imported track id(s), and imported track name(s).
- Suggested verified GPX source: `https://github.com/gps-touring/sample-gpx` with raw files such as:
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/JuraRoute72011.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/MoselradwegAusWiki.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/Vitry-le-Francois_Langres.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/BrittanyJura/VoieVerteHauteVosges.gpx`
  - `https://raw.githubusercontent.com/gps-touring/sample-gpx/master/RoscoffCoastal/Lannion_Plestin_parcours24.4RE.gpx`
- Use at least **one public FIT activity file with GPS positions**. Suggested verified FIT source: Garmin's official FIT SDK examples, e.g. `https://raw.githubusercontent.com/garmin/fit-javascript-sdk/main/test/data/Activity.fit`.
- Do not count non-GPS FIT files or waypoint-only GPX files as positive evidence. They are useful negative tests only: MTL Explorer should fail, ignore, or mark them clearly without adding map tracks, corrupting stats, or blanking the UI.

### Five-GPX Import, Index, Map, And Stats Flow

- Capture baseline map count, track-browser count, statistics totals, data-freshness token, and GPS indexer status.
- Import the five GPX files through the Admin upload UI or by placing them in the documented watched import folder.
- Wait for indexing to finish. If live file watching does not react, trigger **Rescan GPS** from Admin and record that it was needed.
- Confirm upload/index status: all five source files reach completed state, no unexpected GPS index failures appear, data freshness changes, and background jobs (Duplicate Finder, Exploration Score) settle.
- Reload from the freshness banner or helper reload action → the map, track browser, filters, and statistics all show the new data.
- Verify each imported file by name: it appears in track browser search, on the map, in statistics summaries, and in at least one filter result.
- On the map, zoom to the imported tracks, click each track, verify selection/detail opening, point popups, visible line geometry, and no stale or duplicated lines.
- In statistics, verify count increased by five unless a source file legitimately split into multiple tracks; if it split, record the source-to-track mapping and expected count.
- Verify totals changed in the correct direction: total distance, duration, ascent/descent, activity breakdown, period charts, rankings, heatmap density, and track-browser summary row.

### Delete-Two-Track Flow

- Delete two of the imported source files from the watched import/upload folder. If the test environment only exposes browser upload and not the watched folder, mark deletion sync `BLOCKED` and run this flow in the install/full-regression environment.
- Wait for automatic delete processing or trigger **Rescan GPS**.
- Verify the two deleted tracks disappear from the map, track browser, filter results, selection lists, heatmap, related-track lists, and statistics totals.
- Verify the remaining imported tracks still display and open correctly.
- Deleted-track API probes or stale deleted-track URLs are not pass/fail criteria
  for this deletion flow. The frontend regression requirement is that deleted
  tracks no longer appear in user-visible map, browser, filter, heatmap,
  related-track, detail, or statistics surfaces.

### FIT Conversion Flow

- Import the FIT activity file with GPS positions.
- Verify it is accepted by upload/import, indexed successfully, displayed on the map, searchable in the browser, and included in statistics.
- Open the FIT-backed track details → overview, graphs, quality, events, related tracks, mini-map, and point popups render as they do for GPX-backed tracks.
- **Download original source file** → the downloaded file remains FIT and matches the uploaded checksum.
- **Download as GPX** → a valid GPX file downloads and contains real `trkpt` trackpoints, not only waypoints.
- If GPSBabel or FIT conversion is unavailable, the UI shows a clear conversion/indexing error and the failure is recorded as blocking for FIT support.

### Other Supported Track Formats

- The server accepts `.gpx`, `.fit`, `.tcx`, `.kml`, `.kmz`, `.igc`, `.sbp`, `.nmea`, `.geojson`, and `.gdb`. For a full release regression, test at least one GPS-bearing sample for each available format, or mark that format `NOT COVERED` with the reason.
- For each non-GPX format tested, verify upload acceptance, GPSBabel conversion, map display, details/charts, statistics inclusion, **Download original source file**, and **Download as GPX**.

## 1. Sign-in And First Load

- Open the app while signed out → you are redirected to the login screen.
- Sign in with valid credentials → you reach the map.
- Sign in with wrong credentials → a clear error appears and you stay on login.
- If demo mode is active, the login screen shows the demo credentials banner.
- Sign out → you return to login; signing in again works.
- The splash screen (logo, background, message) displays during startup and disappears once the map and tracks are loaded.
- If startup fails (e.g. server down), a retry is offered instead of a frozen splash.
- "MTL Explorer" branding appears in About / public-facing copy.
- The About page is reachable without signing in.
- Deep links work: opening `/track/<id>`, `/plan/<id>`, `/stats`, and `/about` directly loads the right view (after login when required).
- Browser back/forward navigation between views works without errors.

## 2. Map And Tracks

- Base map and overlays load on first open.
- All your tracks appear on the map; the total/visible count is correct.
- Newly imported tracks from the required data-change flow appear without a full browser restart after accepting the freshness/reload prompt.
- Deleted tracks from the required data-change flow disappear from all map sources, selection lists, and popups.
- Zoom in on a track → detail/precision improves (no duplicate or broken lines).
- Fast pan/zoom doesn't leave stale lines, missing tiles, or runaway loading spinners.
- Direction arrows appear on tracks at high zoom (if enabled in settings).
- Click a single track → it highlights and details open.
- Click an area where several tracks overlap → a selection list appears; picking one opens its details.
- Deselect / close the selection → the map returns to its normal state.
- Clicking a point on a track shows a popup with the expected metrics (time, speed, elevation, etc.).
- Swiss Mobility routes popup (where applicable) shows nearby official routes and closes cleanly.

## 3. Track Details

- Open at least one GPX-backed track and one FIT-backed track from user-facing navigation (map, browser, or stats) and record the track ids/source filenames.
- Opening a track loads its overview, charts, related-tracks list, event list, mini-map, and quality info.
- Switch between **Overview**, **Graphs**, **Quality**, **Related**, and **Events**; tabs do not refetch in a loop, lose state, or show blank panels.
- Elevation, speed, distance, and gain charts render with readable values.
- Graph controls work: time/distance x-axis toggle, range band toggle, point-count slider, and graph-height slider update charts without layout breakage.
- Hovering a chart highlights the matching point on the mini-map and hovering the mini-map highlights the chart. No stale cursors remain after leaving either surface.
- The track shape preview (small thumbnail) is visible in browser, filters, stats, related tracks, and selection lists.
- **Download original source file** (GPX/FIT/etc.) → file downloads and matches the uploaded one.
- **Download as GPX** → a valid GPX file downloads even if the source was FIT or another format.
- **Change activity type** (e.g. hiking → cycling) → saves successfully; energy/calorie values update automatically.
- **Energy "what-if" recalculation** (custom rider weight, etc.) → updates the displayed values without permanently saving.
- **Exclude from statistics** toggle → the track stops counting in stats overview; re-including it brings it back.
- **Related tracks** show duplicates and previous/next tracks; clicking one navigates to it.
- **Events tab** shows detected stops / GPS gaps where present; selecting an event highlights the matching mini-map position and deselects cleanly.

## 4. Filters

- Open the filter panel → previously saved filter is still active and shown as a chip.
- Browse the filter catalog; search and grouping work.
- Pick a filter → its parameters appear; apply, reset, and cancel all behave correctly.
- Date, text, and geo parameters all save and re-apply correctly after reload.
- **Geo drawing**: draw a circle, rectangle, and polygon; undo, cancel, finish, and clear all work; saved shapes reappear next time.
- Applied filter updates: visible track count, map colors, legend, and stats — without a full page reload.
- Legend reflects the active filter (categories or gradient); collapsing/hiding groups updates the map immediately.
- Clearing the filter restores all tracks.

## 5. Track Browser And Statistics

- Track browser lists all (or filtered) tracks with name, date, distance, duration, activity, etc.
- Search matches names, descriptions, dates, distances, durations, activity, and file paths.
- Sort by each column works; summary row reflects what is currently visible.
- Quick-view/preset buttons switch the browser subset correctly and preserve usable sorting/search behavior.
- Clicking a row opens the track's details.
- Statistics overview shows total distance, time, elevation, activity breakdown, rankings, milestones, and period charts.
- Stats are correct for: empty dataset, a single track, and many tracks.
- Stats update after the required five-GPX import and again after deleting two imported tracks; no stale deleted-track totals remain.
- Time-period charts (daily/weekly/monthly) render and switch correctly.
- Clicking a stats entry navigates / filters / highlights as expected.
- Highlight drilldowns open the expected track list, open a selected track, and expose excluded-highlight counts where applicable.

## 6. Planner

- Open the planner; pick a routing profile (e.g. hike, bike).
- Click on the map to add waypoints → a route is computed and drawn.
- Insert a waypoint on an existing leg by dragging the route.
- Move and delete waypoints; clear, undo, and redo all work.
- Live stats bar (distance, ascent, time) updates as you edit.
- Elevation profile renders and hovering it highlights the matching map point.
- **Save plan**, list saved plans, load a saved plan, delete a plan.
- **Download plan as GPX** → file is valid and matches the planned route.
- If the routing engine (BRouter) is missing data for an area, the UI shows a clear "segment downloading / unavailable" state instead of an unhandled error.
- Existing planned routes still display even when the planner has trouble fetching new data.
- Touch dragging on mobile works for placing and moving waypoints.

## 7. Measuring And Comparison Tools

- Start the measure tool, pick start and end points → result list of crossing tracks appears with speed/time/distance.
- Clicking a result opens that track's details / segment view.
- Stop the measure tool → all temporary markers and listeners are cleaned up.
- Segment comparison: pick several tracks → comparison chart + map align them correctly even with missing data.
- Sub-track / segment extraction (between two points on a track) returns the expected slice.

## 8. Animation And Virtual Race

- Start animation: tracks play back smoothly; pause, reset, and speed controls work.
- Virtual race: multiple racers move together; ranking and racer cards update in real time.
- Stopping or finishing animation/race leaves map gestures and tools usable (no stuck state).

## 9. Media (Photos)

- Toggle the media layer → photo pins appear in the map view.
- Pan/zoom → media is loaded for the current viewport (not the whole world at once).
- Click a pin → photo preview opens; next/previous navigation works.
- HEIC photos display correctly (converted server-side).
- A missing/broken photo shows a recoverable error, not a blank sheet.

## 10. Heatmap And Overlays

- Toggle the heatmap → it draws over the map without hiding the tracks; respects opacity.
- Toggle each map overlay (Swiss / OSM / etc.) independently; opacity sliders work; ordering above/below tracks stays correct.
- After changing filters, the heatmap updates accordingly.

## 11. GPS Location

- Enable GPS → permission prompt; on accept, the locate marker appears.
- "Follow me" mode keeps the map centered until you pan away (drifted state).
- Permission denied / disabled state shows a clear message.
- Disabling GPS removes the marker and stops updates.

## 12. Location Search

- Open the search → type a place name → results appear.
- Pick a result → map flies to it and a marker is placed.
- Clear search / pick a different tool → marker is removed cleanly.
- Empty / no-result queries show a clear message.

## 13. Globe Mode

- Zoom out far enough → globe view engages automatically.
- Zoom in → map returns to flat view.
- Manual disable of globe is respected (does not auto-re-enable until you re-enable it).
- Zoom limits don't trap the map at edges.

## 14. Admin Tools

- Open the admin dialog. Each tab loads without errors:
  - **Track file upload**: drag or pick GPX/FIT/etc.; upload availability, accepted formats, progress, success, unsupported-format errors, and empty-file errors are clear.
  - **Indexer status**: shows GPS and media pending/running/completed/failed/removed state; refresh updates over time.
  - **Manual rescan**: **Rescan GPS** and **Rescan Media** show queued/already-running/not-ready states without breaking map interaction.
  - **Background jobs**: Duplicate Finder and Exploration Score progress is visible and settles after imports.
  - **Operational tasks**: vector map tiles, location search, and routing segment status show ready/downloading/unavailable/disabled states with useful detail.
  - **Data freshness**: shows last-update timestamp and offers reload.
  - **Server log**: log lines load and refresh.
  - **Attribution**: shows expected map/data sources.
  - **Garmin export tools** (if present): status of installed exporters; install/update actions report success or error.
- Closing/reopening the dialog doesn't lose state mid-action.

## 15. Data Updates And Sync

- After server-side data changes (new import, re-index, or restart), a data-freshness banner appears.
- Reloading from the banner refreshes cached tracks and stats.
- The required five-GPX import and delete-two-track flow passes: indexer state,
  freshness banner, map, browser, stats, filters, heatmap, and details all
  reflect the new source-of-truth files.
- FIT conversion import changes freshness and cache state the same way a native GPX import does.
- Dismissing the banner doesn't loop or re-show immediately.
- Logging out and back in does not re-trigger an automatic data refresh repeatedly.
- Indexer-running state surfaces as a badge but doesn't block map interaction.

## 16. Appearance (Theme And Map Style)

- Switch between **light** and **dark** mode → the whole UI re-themes immediately (text, panels, dialogs, sheets, dropdowns, tooltips, charts).
- No text is unreadable (white-on-white or black-on-black) in either theme.
- Charts re-color on theme switch without needing a reload.
- Selected theme persists across reload and login.
- Hard refresh in dark mode does not flash the light theme first.
- Map theme is independent: each of the available map styles (light, dark, grayscale, light-topo, swisstopo, swisstopo-color) can be selected with either UI theme.
- Selected map style persists across reload.
- Layer opacity sliders, basemap dimming, and reset-to-defaults all behave and persist.

## 17. Locale, Units, And Formatting

- Numbers, distances, durations, and dates render in the expected locale format.
- Changing locale (if available) updates formatting across the app without reload artifacts.
- Locale persists across reload.
- Boundary values (zero, very large, negative gain, null elevation) render sensibly, not as "NaN" or blank.

## 18. Responsive / Mobile / Touch

- Test at a narrow mobile width and with touch:
  - Bottom sheets and the navigation sheet drag, snap, and close correctly.
  - Tables, charts, and map controls stay usable; no text overflows.
  - Planner waypoints can be tapped, dragged, and inserted with touch.
  - Map gestures (pinch, double-tap, drag) work after using each tool.

## 19. Offline And Network Issues

- Installed PWA / installed web-app mode only: after installing MTL Explorer in
  the browser and loading once online, reload while offline → cached tracks and
  tiles still display. A normal browser tab is not expected to pass this offline
  reload check; mark that row `NOT APPLICABLE` or `NOT COVERED` unless the app
  is installed as a web app.
- A flaky connection shows recoverable error states, not a blank screen.
- 401 / 403 from the server redirects to login.
- Service worker update: a "new version available" prompt appears after an update; accepting it reloads cleanly.

## 20. Error Recovery

- Trigger or simulate: failed track load, failed map config, failed media, failed planner route, expired session. Each shows an actionable message (retry, re-login, dismiss) rather than freezing or going blank.
- Rapid switching between tools does not leave the previous tool's markers, listeners, or cursors behind.

## Suggested Regression Passes

| Pass | Scope |
|---|---|
| Smoke (~10 min) | Sign-in, map loads, apply filter, open a track, open stats, open planner, sign out. |
| Full desktop | Every section above on a desktop browser with a normal network. |
| Full mobile / touch | Sections 1–6, 9, 11, and 18 on a phone or tablet. |
| Offline / cache | Installed PWA / installed web-app mode only: load online, then reload offline; verify cached tracks, map cache, and freshness recovery after going online. Normal browser-tab runs should mark this pass `NOT APPLICABLE` or `NOT COVERED` unless the app is installed. |
| Data-change | Run the required five-GPX import, FIT conversion, and delete-two-track flows; verify freshness banner, cache refresh, map, browser, stats, filters, heatmap, and details all update. |
| Theme | Toggle dark/light and each map style; verify UI, charts, map, and persistence. |
